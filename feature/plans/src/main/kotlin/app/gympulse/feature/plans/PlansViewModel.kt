package app.gympulse.feature.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.data.repository.ExerciseRepository
import app.gympulse.core.data.repository.PlanRepository
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.Ids
import app.gympulse.core.model.WorkoutPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

internal data class PlanListItem(
    val plan: WorkoutPlan,
    val summary: PlanSummary,
    /** Namen der Übungen in Planreihenfolge; Übungen ohne Namen fehlen. */
    val exerciseNames: List<String> = emptyList(),
)

internal sealed interface PlansUiState {
    data object Loading : PlansUiState
    data class Loaded(val plans: List<PlanListItem>) : PlansUiState
}

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

@HiltViewModel
internal class PlansViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val exerciseRepository: ExerciseRepository,
    private val time: TimeProvider,
) : ViewModel() {

    val uiState: StateFlow<PlansUiState> = combine<List<WorkoutPlan>, List<Exercise>, PlansUiState>(
        planRepository.observePlans(),
        exerciseRepository.observeExercises(Locale.getDefault().language),
    ) { plans, exercises ->
        val names = exercises.associate { it.id to it.name }
        PlansUiState.Loaded(
            plans.map { plan ->
                PlanListItem(
                    plan = plan,
                    summary = plan.summary(),
                    exerciseNames = plan.exercises.sortedBy { it.sortOrder }.mapNotNull { names[it.exerciseId] },
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), PlansUiState.Loading)

    fun duplicate(plan: WorkoutPlan, copyName: String) {
        viewModelScope.launch {
            val millis = time.now().toEpochMilli()
            planRepository.save(plan.duplicate(copyName) { Ids.newId(millis) })
        }
    }

    fun delete(planId: String) {
        viewModelScope.launch { planRepository.delete(planId) }
    }
}
