package app.gympulse.core.data.repository

import app.gympulse.core.model.Exercise
import app.gympulse.core.model.PlanExercise
import app.gympulse.core.model.TrackingType

/**
 * Standardwerte für eine neu in einen Plan aufgenommene Übung. Gemeinsame Grundlage für den
 * Plan-Editor und "Zu Plan hinzufügen" aus der Bibliothek.
 */
object PlanExerciseDefaults {
    const val SETS = 3

    /** Eine reine Dauer-Übung (z. B. Cardio) ist ein einziger Block ohne Satzaufteilung. */
    const val SETS_DURATION = 1
    const val REPS_MIN = 8
    const val REPS_MAX = 12

    /** Wiederholungsziele gibt es nur bei Übungen, die Wiederholungen erfassen. */
    fun usesReps(trackingType: TrackingType): Boolean =
        trackingType == TrackingType.WEIGHT_REPS || trackingType == TrackingType.REPS

    fun defaultSets(trackingType: TrackingType): Int =
        if (trackingType == TrackingType.DURATION) SETS_DURATION else SETS

    fun newEntry(exercise: Exercise, id: String, sortOrder: Int): PlanExercise {
        val reps = usesReps(exercise.trackingType)
        return PlanExercise(
            id = id,
            exerciseId = exercise.id,
            sortOrder = sortOrder,
            supersetGroup = null,
            targetSets = defaultSets(exercise.trackingType),
            targetRepsMin = if (reps) REPS_MIN else null,
            targetRepsMax = if (reps) REPS_MAX else null,
            targetWeightKg = null,
            restSeconds = null,
        )
    }
}
