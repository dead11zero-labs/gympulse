package app.gympulse.feature.settings.wear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.wearsync.WatchAppLauncher
import app.gympulse.core.wearsync.WatchAppState
import app.gympulse.core.wearsync.WatchAppStatus
import app.gympulse.core.wearsync.WatchLaunchResult
import app.gympulse.core.wearsync.WearPlayListing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Einmalige Rückmeldung als Dialog. */
internal enum class WearMessage { INSTALL_STARTED, OPEN_STARTED, NO_WATCH, FAILED }

/** Zustand der Uhr-App aus Sicht der Einstellungen. */
internal enum class WatchConnection { CONNECTED, INSTALLED_NOT_CONNECTED, NOT_DETECTED }

internal data class WearSettingsUiState(
    /** [Feature.WEAR_APP] ist aktiv; sonst wird der Eintrag nicht gezeigt. */
    val featureEnabled: Boolean = false,
    val watch: WatchAppState = WatchAppState.NONE,
    val busy: Boolean = false,
    val message: WearMessage? = null,
    /** Es gibt einen Play-Eintrag; sonst Sideload-Anleitung statt Install-Button. */
    val playListingAvailable: Boolean = false,
) {
    val connection: WatchConnection
        get() = when {
            watch.installed && watch.connected -> WatchConnection.CONNECTED
            watch.installed -> WatchConnection.INSTALLED_NOT_CONNECTED
            else -> WatchConnection.NOT_DETECTED
        }

    /** Installieren ist nur sinnvoll, solange die Uhr-App fehlt. */
    val canInstall: Boolean get() = featureEnabled && playListingAvailable && !watch.installed && !busy

    /** Öffnen geht nur mit installierter und verbundener Uhr-App. */
    val canOpen: Boolean get() = featureEnabled && watch.installed && watch.connected && !busy
}

private data class Transient(val busy: Boolean = false, val message: WearMessage? = null)

@HiltViewModel
internal class WearSettingsViewModel @Inject constructor(
    featureGate: FeatureGate,
    watchAppStatus: WatchAppStatus,
    private val launcher: WatchAppLauncher,
    @WearPlayListing private val playListingAvailable: Boolean,
) : ViewModel() {

    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<WearSettingsUiState> = combine(
        featureGate.observe(Feature.WEAR_APP),
        watchAppStatus.state,
        transient,
    ) { enabled, watch, current ->
        WearSettingsUiState(enabled, watch, current.busy, current.message, playListingAvailable)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WearSettingsUiState())

    fun installOnWatch() {
        if (!uiState.value.canInstall) return
        run(WearMessage.INSTALL_STARTED) { launcher.openInstallPageOnWatch() }
    }

    fun openOnWatch() {
        if (!uiState.value.canOpen) return
        run(WearMessage.OPEN_STARTED) { launcher.openAppOnWatch() }
    }

    fun dismissMessage() = transient.update { it.copy(message = null) }

    private fun run(success: WearMessage, action: suspend () -> WatchLaunchResult) {
        transient.update { it.copy(busy = true) }
        viewModelScope.launch {
            val message = when (action()) {
                WatchLaunchResult.STARTED -> success
                WatchLaunchResult.NO_WATCH -> WearMessage.NO_WATCH
                WatchLaunchResult.FAILED -> WearMessage.FAILED
            }
            transient.value = Transient(busy = false, message = message)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
