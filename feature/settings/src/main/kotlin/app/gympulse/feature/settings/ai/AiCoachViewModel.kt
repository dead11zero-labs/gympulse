package app.gympulse.feature.settings.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.data.ai.ModelDownloadManager
import app.gympulse.core.data.ai.ModelDownloadState
import app.gympulse.core.data.ai.StartDownloadResult
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.featuregate.UserFeatureToggles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Dialoge der Seite; höchstens einer ist offen. */
internal enum class AiCoachDialog { OPT_IN, DELETE_MODEL }

/** Rückmeldung zum Start des Downloads, die auf der Seite stehen bleibt, bis sie gelesen oder ersetzt wird. */
internal sealed interface AiCoachNotice {
    /** Das Modell ist in dieser Version noch nicht freigegeben (Platzhalter für Hash/Commit). */
    data object NotConfigured : AiCoachNotice

    data class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) : AiCoachNotice
}

internal data class AiCoachUiState(
    val enabled: Boolean = false,
    val model: ModelDownloadState = ModelDownloadState.NotDownloaded,
    val downloadSizeBytes: Long = 0L,
    val requiredFreeBytes: Long = 0L,
    val dialog: AiCoachDialog? = null,
    val notice: AiCoachNotice? = null,
) {
    val modelReady: Boolean get() = model is ModelDownloadState.Ready
    val downloading: Boolean get() = model is ModelDownloadState.Downloading

    /** Download starten oder wiederholen ist sinnvoll, sobald der Coach an ist und kein Modell lädt oder vorliegt. */
    val canStartDownload: Boolean
        get() = enabled && (model is ModelDownloadState.NotDownloaded || model is ModelDownloadState.Failed)

    /** Der Modell-Abschnitt ist nur relevant, wenn der Coach an ist oder schon Daten da sind. */
    val showModelSection: Boolean get() = enabled || model !is ModelDownloadState.NotDownloaded
}

private data class Local(val dialog: AiCoachDialog? = null, val notice: AiCoachNotice? = null)

@HiltViewModel
internal class AiCoachViewModel @Inject constructor(
    featureGate: FeatureGate,
    private val toggles: UserFeatureToggles,
    private val downloads: ModelDownloadManager,
) : ViewModel() {

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<AiCoachUiState> = combine(
        featureGate.observe(Feature.AI_COACH),
        downloads.state,
        local,
    ) { enabled, model, current ->
        AiCoachUiState(
            enabled = enabled,
            model = model,
            downloadSizeBytes = downloads.downloadSizeBytes,
            requiredFreeBytes = downloads.requiredFreeBytes(),
            dialog = current.dialog,
            notice = current.notice,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AiCoachUiState())

    /** Schalter: Einschalten fragt erst im Opt-in-Dialog nach; Ausschalten bricht einen laufenden Download ab. */
    fun setEnabled(enable: Boolean) {
        val state = uiState.value
        if (enable) {
            if (state.enabled) return
            if (state.modelReady) {
                toggles.setEnabled(Feature.AI_COACH, true)
            } else {
                local.update { it.copy(dialog = AiCoachDialog.OPT_IN) }
            }
        } else {
            toggles.setEnabled(Feature.AI_COACH, false)
            if (state.downloading) downloads.cancelDownload()
            local.update { it.copy(notice = null) }
        }
    }

    /** Nutzer bestätigt den Opt-in-Dialog: Coach einschalten und Download planen. */
    fun confirmOptIn() {
        local.update { it.copy(dialog = null) }
        toggles.setEnabled(Feature.AI_COACH, true)
        startDownload()
    }

    fun startDownload() {
        val notice = when (val result = downloads.startDownload()) {
            StartDownloadResult.Enqueued, StartDownloadResult.AlreadyReady -> null
            StartDownloadResult.NotConfigured -> AiCoachNotice.NotConfigured
            is StartDownloadResult.InsufficientStorage ->
                AiCoachNotice.InsufficientStorage(result.requiredBytes, result.availableBytes)
        }
        local.update { it.copy(notice = notice) }
    }

    fun cancelDownload() = downloads.cancelDownload()

    fun requestDeleteModel() = local.update { it.copy(dialog = AiCoachDialog.DELETE_MODEL) }

    fun confirmDeleteModel() {
        local.update { it.copy(dialog = null, notice = null) }
        downloads.deleteModel()
    }

    fun dismissDialog() = local.update { it.copy(dialog = null) }

    fun dismissNotice() = local.update { it.copy(notice = null) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
