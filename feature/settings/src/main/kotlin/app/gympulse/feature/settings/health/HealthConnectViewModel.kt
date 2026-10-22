package app.gympulse.feature.settings.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.data.health.HealthAvailability
import app.gympulse.core.data.health.HealthConnectSync
import app.gympulse.core.data.health.HealthInsights
import app.gympulse.core.data.health.HealthReadType
import app.gympulse.core.settings.HealthMetric
import app.gympulse.core.settings.IntegrationSettings
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
internal enum class HealthMessage { PERMISSIONS_DENIED, METRIC_DENIED }

/**
 * Einzeln schaltbare Datentypen im Health-Connect-Untermenü. [metric] ist der gespeicherte Schalter
 * (DataStore), [readType] der Lesetyp, dessen Berechtigung nötig ist. Schlaf hat keinen [metric],
 * er hängt am Feature-Gate `HEALTH_SLEEP`.
 */
internal enum class HealthToggle(val metric: HealthMetric?, val readType: HealthReadType?) {
    WORKOUTS(HealthMetric.WORKOUTS, null),
    HEART_RATE(HealthMetric.HEART_RATE, HealthReadType.HEART_RATE),
    STEPS(HealthMetric.STEPS, HealthReadType.STEPS),
    CALORIES(HealthMetric.CALORIES, HealthReadType.ACTIVE_CALORIES),
    SLEEP(null, HealthReadType.SLEEP),
}

internal data class HealthConnectUiState(
    val availability: HealthAvailability = HealthAvailability.UNSUPPORTED,
    val enabled: Boolean = false,
    val permissionsGranted: Boolean = false,
    /** Lesbare Datentypen mit erteilter Berechtigung. */
    val grantedReadTypes: Set<HealthReadType> = emptySet(),
    /** Metriken, die der Nutzer nicht abgeschaltet hat (Standard: alle). */
    val enabledMetrics: Set<HealthMetric> = HealthMetric.entries.toSet(),
    val sleepEnabled: Boolean = false,
    val message: HealthMessage? = null,
) {
    val available: Boolean get() = availability == HealthAvailability.AVAILABLE
    val active: Boolean get() = available && enabled && permissionsGranted

    /** Ob der Schalter für [toggle] an ist: gewollt und, wo nötig, auch berechtigt. */
    fun isOn(toggle: HealthToggle): Boolean {
        if (!active) return false
        val wanted = toggle.metric?.let { it in enabledMetrics } ?: sleepEnabled
        return wanted && !needsPermission(toggle)
    }

    /** Ob für [toggle] noch die Lese-Berechtigung fehlt. */
    fun needsPermission(toggle: HealthToggle): Boolean =
        toggle.readType != null && toggle.readType !in grantedReadTypes
}

private data class DeviceState(
    val availability: HealthAvailability = HealthAvailability.UNSUPPORTED,
    val permissionsGranted: Boolean = false,
    val grantedReadTypes: Set<HealthReadType> = emptySet(),
    val message: HealthMessage? = null,
)

@HiltViewModel
internal class HealthConnectViewModel @Inject constructor(
    private val sync: HealthConnectSync,
    private val insights: HealthInsights,
    private val settings: IntegrationSettings,
) : ViewModel() {

    private val device = MutableStateFlow(DeviceState())

    /** Schalter, für den gerade die Berechtigung angefragt wird. */
    private var pendingToggle: HealthToggle? = null

    /** Kernberechtigungen plus optionale Lesetypen; Schlaf nur mit eingeschaltetem Schalter. */
    val permissions: Set<String> get() = insights.permissionsToRequest(includeSleep = uiState.value.sleepEnabled)

    private val metrics = combine(HealthMetric.entries.map(settings::observeHealthMetric)) { values ->
        HealthMetric.entries.filterIndexedTo(mutableSetOf()) { index, _ -> values[index] }.toSet()
    }

    val uiState: StateFlow<HealthConnectUiState> = combine(
        device,
        sync.observeEnabled(),
        insights.observeSleepEnabled(),
        metrics,
    ) { current, enabled, sleepEnabled, enabledMetrics ->
        HealthConnectUiState(
            availability = current.availability,
            enabled = enabled,
            permissionsGranted = current.permissionsGranted,
            grantedReadTypes = current.grantedReadTypes.takeIf { enabled }.orEmpty(),
            enabledMetrics = enabledMetrics,
            sleepEnabled = sleepEnabled,
            message = current.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HealthConnectUiState())

    /** Liest Verfügbarkeit und Berechtigungen neu, z. B. beim Zurückkehren aus Health Connect. */
    fun refresh() {
        viewModelScope.launch {
            val availability = sync.availability()
            val granted = availability == HealthAvailability.AVAILABLE && sync.hasAllPermissions()
            val readTypes = insights.grantedReadTypes()
            device.update {
                it.copy(availability = availability, permissionsGranted = granted, grantedReadTypes = readTypes)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled && !uiState.value.available) return
        sync.setEnabled(enabled)
        if (enabled) refresh()
    }

    /** Ergebnis des Berechtigungsdialogs; bei vollständiger Zustimmung wird die Anbindung aktiv. */
    fun onPermissionResult(granted: Set<String>) {
        if (granted.containsAll(sync.requiredPermissions)) {
            sync.setEnabled(true)
            refresh()
        } else {
            device.update { it.copy(message = HealthMessage.PERMISSIONS_DENIED) }
            refresh()
        }
    }

    /**
     * Schaltet einen Datentyp. Fehlt beim Einschalten die Lese-Berechtigung, liefert die Methode
     * die anzufragenden Berechtigungen zurück und der Schalter ändert sich erst mit
     * [onTogglePermissionResult]; sonst null.
     */
    fun toggle(toggle: HealthToggle, checked: Boolean): Set<String>? {
        val readType = toggle.readType
        if (checked && readType != null && uiState.value.needsPermission(toggle)) {
            pendingToggle = toggle
            return setOf(insights.readPermission(readType))
        }
        store(toggle, checked)
        return null
    }

    /** Ergebnis des Berechtigungsdialogs für den zuletzt angefragten Datentyp. */
    fun onTogglePermissionResult(granted: Set<String>) {
        val toggle = pendingToggle ?: return
        pendingToggle = null
        val readType = toggle.readType
        if (readType != null && insights.readPermission(readType) in granted) {
            store(toggle, true)
        } else {
            device.update { it.copy(message = HealthMessage.METRIC_DENIED) }
            refresh()
        }
    }

    private fun store(toggle: HealthToggle, enabled: Boolean) {
        viewModelScope.launch {
            toggle.metric?.let { settings.setHealthMetricEnabled(it, enabled) }
            if (toggle == HealthToggle.SLEEP) insights.setSleepEnabled(enabled)
            refresh()
        }
    }

    fun dismissMessage() = device.update { it.copy(message = null) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
