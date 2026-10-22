package app.gympulse.feature.settings.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.data.cloud.CloudBackupInfo
import app.gympulse.core.data.cloud.CloudBackupSync
import app.gympulse.core.data.cloud.CloudConfiguration
import app.gympulse.core.data.cloud.CloudException
import app.gympulse.core.data.cloud.CloudUser
import app.gympulse.core.data.cloud.GoogleIdTokenRequester
import app.gympulse.core.data.cloud.RestoreSummary
import app.gympulse.core.settings.IntegrationSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class CloudOperation { SIGN_IN, SIGN_OUT, BACKUP, RESTORE, DELETE }

/** Sicherheitsabfrage vor Aktionen, die Daten überschreiben oder löschen. */
internal enum class CloudConfirmation { RESTORE, DELETE }

/** Einmalige Rückmeldung als Dialog. */
internal sealed interface CloudMessage {
    data class BackupDone(val info: CloudBackupInfo) : CloudMessage
    data class RestoreDone(val summary: RestoreSummary) : CloudMessage
    data object Deleted : CloudMessage
    data class Failed(val operation: CloudOperation, val reason: CloudException.Reason) : CloudMessage
}

internal data class CloudBackupUiState(
    val configuration: CloudConfiguration = CloudConfiguration.NONE,
    val enabled: Boolean = false,
    /** Beim App-Start automatisch sichern (wirkt nur mit aktivem Cloud-Backup und Anmeldung). */
    val autoBackup: Boolean = false,
    val user: CloudUser? = null,
    val latest: CloudBackupInfo? = null,
    val working: CloudOperation? = null,
    val confirmation: CloudConfirmation? = null,
    val message: CloudMessage? = null,
) {
    val available: Boolean get() = configuration.backendConfigured
    val active: Boolean get() = available && enabled
    val signedIn: Boolean get() = user != null
    val idle: Boolean get() = working == null
    val canSignIn: Boolean get() = active && !signedIn && configuration.googleSignInConfigured && idle
    val canBackup: Boolean get() = active && signedIn && idle
    val canRestore: Boolean get() = canBackup && latest != null
}

private data class LocalState(
    val latest: CloudBackupInfo? = null,
    val working: CloudOperation? = null,
    val confirmation: CloudConfirmation? = null,
    val message: CloudMessage? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class CloudBackupViewModel @Inject constructor(
    private val sync: CloudBackupSync,
    private val integrationSettings: IntegrationSettings,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    // Ohne Opt-in wird der Supabase-Client nicht einmal erzeugt.
    private val user = sync.observeEnabled()
        .flatMapLatest { enabled ->
            if (enabled && sync.configuration.backendConfigured) sync.observeUser() else flowOf(null)
        }
        .catch { emit(null) }
        .distinctUntilChanged()

    val uiState: StateFlow<CloudBackupUiState> =
        combine(local, sync.observeEnabled(), user, integrationSettings.autoCloudBackup) { current, enabled, account, auto ->
            CloudBackupUiState(
                configuration = sync.configuration,
                enabled = enabled,
                autoBackup = auto,
                user = account,
                latest = current.latest.takeIf { account != null },
                working = current.working,
                confirmation = current.confirmation,
                message = current.message,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            CloudBackupUiState(configuration = sync.configuration),
        )

    init {
        // Nach dem Anmelden den Stand der Cloud-Sicherung laden, nach dem Abmelden vergessen.
        user.onEach { current ->
            if (current != null) loadLatest() else local.update { it.copy(latest = null) }
        }.launchIn(viewModelScope)
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled && !sync.configuration.backendConfigured) return
        sync.setEnabled(enabled)
    }

    fun setAutoBackup(enabled: Boolean) {
        viewModelScope.launch { integrationSettings.setAutoCloudBackup(enabled) }
    }

    fun signIn(requester: GoogleIdTokenRequester) {
        if (!uiState.value.canSignIn) return
        run(CloudOperation.SIGN_IN) { sync.signIn(requester) }
    }

    fun signOut() = run(CloudOperation.SIGN_OUT) {
        sync.signOut()
        local.update { it.copy(latest = null) }
    }

    fun backupNow() {
        if (!uiState.value.canBackup) return
        run(CloudOperation.BACKUP) {
            val info = sync.backupNow()
            local.update { it.copy(latest = info, message = CloudMessage.BackupDone(info)) }
        }
    }

    fun requestRestore() {
        if (uiState.value.canRestore) local.update { it.copy(confirmation = CloudConfirmation.RESTORE) }
    }

    fun requestDelete() {
        if (uiState.value.canRestore) local.update { it.copy(confirmation = CloudConfirmation.DELETE) }
    }

    fun confirm() {
        val confirmation = local.value.confirmation ?: return
        local.update { it.copy(confirmation = null) }
        when (confirmation) {
            CloudConfirmation.RESTORE -> run(CloudOperation.RESTORE) {
                val summary = sync.restore()
                local.update { it.copy(message = CloudMessage.RestoreDone(summary)) }
            }
            CloudConfirmation.DELETE -> run(CloudOperation.DELETE) {
                sync.deleteRemote()
                local.update { it.copy(latest = null, message = CloudMessage.Deleted) }
            }
        }
    }

    fun dismissConfirmation() = local.update { it.copy(confirmation = null) }

    fun dismissMessage() = local.update { it.copy(message = null) }

    /** Lädt den Stand der Cloud-Sicherung im Hintergrund; Fehler lassen nur die Anzeige leer. */
    private fun loadLatest() {
        viewModelScope.launch {
            val latest = try {
                sync.latest()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            local.update { it.copy(latest = latest) }
        }
    }

    /**
     * Startet [block], solange nichts anderes läuft. Fehler werden zur Meldung; bricht der Nutzer
     * die Anmeldung ab, bleibt es still.
     */
    private fun run(operation: CloudOperation, block: suspend () -> Unit) {
        if (local.value.working != null) return
        local.update { it.copy(working = operation) }
        viewModelScope.launch {
            val failure = try {
                block()
                null
            } catch (e: CancellationException) {
                local.update { it.copy(working = null) }
                throw e
            } catch (e: CloudException) {
                e.reason
            } catch (e: Exception) {
                CloudException.Reason.UNKNOWN
            }
            local.update { state ->
                val message = when (failure) {
                    null, CloudException.Reason.CANCELLED -> state.message
                    else -> CloudMessage.Failed(operation, failure)
                }
                state.copy(working = null, message = message)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
