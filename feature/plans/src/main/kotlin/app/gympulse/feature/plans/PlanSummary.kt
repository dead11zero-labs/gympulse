package app.gympulse.feature.plans

import app.gympulse.core.model.WorkoutPlan

/** Kennzahlen für die Plankarte. */
internal data class PlanSummary(
    val exerciseCount: Int,
    val totalSets: Int,
    val estimatedMinutes: Int,
)

/** Grobe Annahme: so lange dauert ein Arbeitssatz selbst. */
private const val SECONDS_PER_SET = 45

/** Pause, wenn der Plan keine eigene vorgibt (Standard aus dem Konzept: 90 s). */
private const val DEFAULT_REST_SECONDS = 90

private const val SECONDS_PER_MINUTE = 60

internal fun WorkoutPlan.summary(): PlanSummary {
    val seconds = exercises.sumOf { it.targetSets * (SECONDS_PER_SET + (it.restSeconds ?: DEFAULT_REST_SECONDS)) }
    return PlanSummary(
        exerciseCount = exercises.size,
        totalSets = exercises.sumOf { it.targetSets },
        // Aufrunden, damit ein nicht leerer Plan nie "0 min" anzeigt.
        estimatedMinutes = (seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE,
    )
}

/** Kopie eines Plans mit neuen IDs für Plan und Übungen; die Reihenfolge bleibt erhalten. */
internal fun WorkoutPlan.duplicate(newName: String, newId: () -> String): WorkoutPlan = copy(
    id = newId(),
    name = newName,
    isArchived = false,
    exercises = exercises.map { it.copy(id = newId()) },
)
