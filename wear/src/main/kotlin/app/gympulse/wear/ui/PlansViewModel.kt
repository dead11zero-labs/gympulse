package app.gympulse.wear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.wearprotocol.CommandAck
import app.gympulse.core.wearprotocol.PlanSummary
import app.gympulse.wear.data.WatchWorkoutRepository
import app.gympulse.wear.data.WearConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlansUiState(
    /** null, solange das Telefon noch keine Planliste geliefert hat. */
    val plans: List<PlanSummary>? = null,
    val connection: WearConnection = WearConnection.UNKNOWN,
    val startPending: Boolean = false,
    val notices: List<CommandAck> = emptyList(),
    val incompatibleProtocol: Boolean = false,
)

@HiltViewModel
class PlansViewModel @Inject constructor(
    private val repository: WatchWorkoutRepository,
) : ViewModel() {

    val uiState: StateFlow<PlansUiState> = combine(
        repository.plans,
        repository.connection,
        repository.state,
    ) { plans, connection, state ->
        PlansUiState(
            plans = plans?.plans,
            connection = connection,
            startPending = state.isStartPending,
            notices = state.notices,
            incompatibleProtocol = state.incompatibleProtocol,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlansUiState())

    /** Startet ein Workout nach [planId]; null ist ein freies Workout. Ein zweiter Tipp während des Startens zählt nicht. */
    fun startWorkout(planId: String?) {
        if (repository.state.value.isStartPending) return
        viewModelScope.launch { repository.startWorkout(planId) }
    }

    fun dismissNotices() = repository.dismissNotices()
}
