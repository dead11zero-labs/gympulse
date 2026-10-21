package app.gympulse.feature.settings.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.data.health.HealthAvailability
import app.gympulse.core.data.health.HealthConnectSync
import app.gympulse.core.data.health.HealthInsights
import app.gympulse.core.data.health.HealthReadType
import app.gympulse.core.data.health.HealthWeight
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
internal enum class HealthMessage { WEIGHT_IMPORTED, WEIGHT_ALREADY_IMPORTED, PERMISSIONS_DENIED, SLEEP_DENIED }

internal data class HealthConnectUiState(
    val availability: HealthAvailability = HealthAvailability.UNSUPPORTED,
    val enabled: Boolean = false,
    val permissionsGranted: Boolean = false,
    /** Lesbare Datentypen mit erteilter Berechtigung. */
    val grantedReadTypes: Set<HealthReadType> = emptySet(),
    val sleepEnabled: Boolean = false,
    val latestWeight: HealthWeight? = null,
    val message: HealthMessage? = null,
) {
    val available: Boolean get() = availability == HealthAvailability.AVAILABLE
    val active: Boolean get() = available && enabled && permissionsGranted

    /** Herzfrequenz, Schritte oder aktive Kalorien sind noch nicht freigegeben. */
    val activityAccessMissing: Boolean
        get() = active && !grantedReadTypes.containsAll(HealthInsights.ACTIVITY_TYPES)

    val sleepActive: Boolean get() = active && sleepEnabled && HealthReadType.SLEEP in grantedReadTypes
}

private data class DeviceState(
    val availability: HealthAvailability = HealthAvailability.UNSUPPORTED,
    val permissionsGranted: Boolean = false,
    val grantedReadTypes: Set<HealthReadType> = emptySet(),
    val latestWeight: HealthWeight? = null,
    val message: HealthMessage? = null,
)

@HiltViewModel
internal class HealthConnectViewModel @Inject constructor(
    private val sync: HealthConnectSync,
    private val insights: HealthInsights,
) : ViewModel() {

    private val device = MutableStateFlow(DeviceState())

    /** Kernberechtigungen plus optionale Lesetypen; Schlaf nur mit eingeschaltetem Unterschalter. */
    val permissions: Set<String> get() = insights.permissionsToRequest(includeSleep = uiState.value.sleepEnabled)

    val sleepPermissions: Set<String> get() = setOf(insights.sleepPermission)

    val uiState: StateFlow<HealthConnectUiState> = combine(
        device,
        sync.observeEnabled(),
        insights.observeSleepEnabled(),
    ) { current, enabled, sleepEnabled ->
        HealthConnectUiState(
            availability = current.availability,
            enabled = enabled,
            permissionsGranted = current.permissionsGranted,
            grantedReadTypes = current.grantedReadTypes.takeIf { enabled }.orEmpty(),
            sleepEnabled = sleepEnabled,
            latestWeight = current.latestWeight.takeIf { enabled },
            message = current.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HealthConnectUiState())

    /** Liest Verfügbarkeit, Berechtigungen und das jüngste Gewicht neu, z. B. beim Zurückkehren. */
    fun refresh() {
        viewModelScope.launch {
            val availability = sync.availability()
            val granted = availability == HealthAvailability.AVAILABLE && sync.hasAllPermissions()
            val readTypes = insights.grantedReadTypes()
            device.update {
                it.copy(availability = availability, permissionsGranted = granted, grantedReadTypes = readTypes)
            }
            val weight = sync.latestWeight()
            device.update { it.copy(latestWeight = weight) }
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

    /** Schlaf-Unterschalter; beim Einschalten ohne Berechtigung fragt die Oberfläche zuerst nach. */
    fun setSleepEnabled(enabled: Boolean) {
        insights.setSleepEnabled(enabled)
        refresh()
    }

    /** Ergebnis des Berechtigungsdialogs für Schlafdaten. */
    fun onSleepPermissionResult(granted: Set<String>) {
        if (granted.containsAll(sleepPermissions)) {
            insights.setSleepEnabled(true)
        } else {
            device.update { it.copy(message = HealthMessage.SLEEP_DENIED) }
        }
        refresh()
    }

    fun importWeight() {
        val weight = uiState.value.latestWeight ?: return
        viewModelScope.launch {
            val imported = sync.importWeight(weight)
            device.update {
                it.copy(message = if (imported) HealthMessage.WEIGHT_IMPORTED else HealthMessage.WEIGHT_ALREADY_IMPORTED)
            }
        }
    }

    fun dismissMessage() = device.update { it.copy(message = null) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
