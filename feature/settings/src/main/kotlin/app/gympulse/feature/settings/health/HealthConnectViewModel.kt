package app.gympulse.feature.settings.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.data.health.HealthAvailability
import app.gympulse.core.data.health.HealthConnectSync
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
internal enum class HealthMessage { WEIGHT_IMPORTED, WEIGHT_ALREADY_IMPORTED, PERMISSIONS_DENIED }

internal data class HealthConnectUiState(
    val availability: HealthAvailability = HealthAvailability.UNSUPPORTED,
    val enabled: Boolean = false,
    val permissionsGranted: Boolean = false,
    val latestWeight: HealthWeight? = null,
    val message: HealthMessage? = null,
) {
    val available: Boolean get() = availability == HealthAvailability.AVAILABLE
    val active: Boolean get() = available && enabled && permissionsGranted
}

private data class DeviceState(
    val availability: HealthAvailability = HealthAvailability.UNSUPPORTED,
    val permissionsGranted: Boolean = false,
    val latestWeight: HealthWeight? = null,
    val message: HealthMessage? = null,
)

@HiltViewModel
internal class HealthConnectViewModel @Inject constructor(
    private val sync: HealthConnectSync,
) : ViewModel() {

    private val device = MutableStateFlow(DeviceState())

    val permissions: Set<String> get() = sync.requiredPermissions

    val uiState: StateFlow<HealthConnectUiState> = combine(device, sync.observeEnabled()) { current, enabled ->
        HealthConnectUiState(
            availability = current.availability,
            enabled = enabled,
            permissionsGranted = current.permissionsGranted,
            latestWeight = current.latestWeight.takeIf { enabled },
            message = current.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HealthConnectUiState())

    /** Liest Verfügbarkeit, Berechtigungen und das jüngste Gewicht neu, z. B. beim Zurückkehren. */
    fun refresh() {
        viewModelScope.launch {
            val availability = sync.availability()
            val granted = availability == HealthAvailability.AVAILABLE && sync.hasAllPermissions()
            device.update { it.copy(availability = availability, permissionsGranted = granted) }
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
