package app.gympulse.core.workout

import app.gympulse.core.data.health.WorkoutHealthMetrics
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.WorkoutSession

/** Shown after finishing a workout. */
data class WorkoutSummary(
    val title: String?,
    val durationSeconds: Long,
    val completedSets: Int,
    val volumeKg: Double,
    /** Exercises with at least one completed set, in session order. */
    val exercises: List<SummaryExercise> = emptyList(),
    /** Values from Health Connect for the session window; null until loaded or if there are none. */
    val health: WorkoutHealthMetrics? = null,
) {
    val recordCount: Int get() = exercises.sumOf { it.records.size }
}

data class SummaryExercise(
    val exerciseId: String,
    /** Null if the exercise is not in the catalog (the screen shows a generic label). */
    val name: String?,
    val sets: List<SummarySet>,
    val records: List<PersonalRecord>,
)

data class SummarySet(
    /** Number among the working sets; null for a warm-up set. */
    val number: Int?,
    val weightKg: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
)

/** A set without reps, duration or distance is empty and does not count as done. */
fun SetEntry.hasValues(): Boolean =
    (reps ?: 0) > 0 || (durationSeconds ?: 0) > 0 || (distanceMeters ?: 0.0) > 0.0

/** Number of ticked-off sets that carry values. */
fun WorkoutSession.completedSetCount(): Int =
    exercises.sumOf { exercise -> exercise.sets.count { it.isCompleted && it.hasValues() } }
