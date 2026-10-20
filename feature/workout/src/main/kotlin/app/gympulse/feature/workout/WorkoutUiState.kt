package app.gympulse.feature.workout

import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.SetType
import app.gympulse.core.model.TrackingType
import java.time.Instant

internal sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState

    /** No session is running: choose a plan or start an empty workout. */
    data class SelectPlan(val plans: List<PlanOption>) : WorkoutUiState

    data class Active(val session: ActiveSessionUi) : WorkoutUiState
}

internal data class PlanOption(
    val id: String,
    val name: String,
    val exerciseCount: Int,
)

internal data class ActiveSessionUi(
    val sessionId: String,
    /** Plan name at the time of the start; null for an empty workout. */
    val title: String?,
    val startedAt: Instant,
    val volumeKg: Double,
    /** Completed sets with values; empty sets are ignored. */
    val completedSets: Int,
    /** Sets that are not ticked off yet. */
    val openSets: Int,
    val exercises: List<ExerciseUi>,
)

internal data class ExerciseUi(
    val sessionExerciseId: String,
    val exerciseId: String,
    val name: String,
    val trackingType: TrackingType,
    /** Bodyweight exercise: the weight field holds additional load only. */
    val isBodyweight: Boolean,
    val notes: String?,
    val sets: List<SetUi>,
)

internal data class SetUi(
    val id: String,
    /** 1-based number among the working sets; warm-up sets are labelled separately. */
    val number: Int,
    val setType: SetType,
    val weightKg: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    val isCompleted: Boolean,
    /** Matching set of the last completed session, for the "Previous" column. */
    val previous: SetEntry?,
)

/** Exercise entry in the picker sheet. */
internal data class ExerciseOption(
    val id: String,
    val name: String,
    val isFavorite: Boolean,
)

/** Shown after finishing a workout. */
internal data class WorkoutSummary(
    val title: String?,
    val durationSeconds: Long,
    val completedSets: Int,
    val volumeKg: Double,
    /** Exercises with at least one completed set, in session order. */
    val exercises: List<SummaryExercise> = emptyList(),
) {
    val recordCount: Int get() = exercises.sumOf { it.records.size }
}

internal data class SummaryExercise(
    val exerciseId: String,
    /** Null if the exercise is not in the catalog (the screen shows a generic label). */
    val name: String?,
    val sets: List<SummarySet>,
    val records: List<PersonalRecord>,
)

internal data class SummarySet(
    /** Number among the working sets; null for a warm-up set. */
    val number: Int?,
    val weightKg: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
)
