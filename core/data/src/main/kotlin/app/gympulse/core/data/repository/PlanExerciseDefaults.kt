package app.gympulse.core.data.repository

import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
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

    /** Standard-Zielzeit einer Cardio-Übung in Minuten; gespeichert in `targetRepsMin`. */
    const val CARDIO_MINUTES = 20
    const val REPS_MIN = 8
    const val REPS_MAX = 12

    /** Wiederholungsziele gibt es nur bei Übungen, die Wiederholungen erfassen. */
    fun usesReps(trackingType: TrackingType): Boolean =
        trackingType == TrackingType.WEIGHT_REPS || trackingType == TrackingType.REPS

    fun defaultSets(trackingType: TrackingType): Int =
        if (trackingType == TrackingType.DURATION) SETS_DURATION else SETS

    fun newEntry(exercise: Exercise, id: String, sortOrder: Int): PlanExercise {
        val cardio = exercise.category == ExerciseCategory.CARDIO
        val reps = usesReps(exercise.trackingType) && !cardio
        return PlanExercise(
            id = id,
            exerciseId = exercise.id,
            sortOrder = sortOrder,
            supersetGroup = null,
            targetSets = if (cardio) SETS_DURATION else defaultSets(exercise.trackingType),
            targetRepsMin = if (cardio) CARDIO_MINUTES else if (reps) REPS_MIN else null,
            targetRepsMax = if (reps) REPS_MAX else null,
            targetWeightKg = null,
            restSeconds = null,
        )
    }
}
