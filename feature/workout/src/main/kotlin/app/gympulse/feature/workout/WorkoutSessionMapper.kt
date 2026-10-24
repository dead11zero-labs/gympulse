package app.gympulse.feature.workout

import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.SetType
import app.gympulse.core.model.TrackingType
import app.gympulse.core.model.WorkoutSession
import app.gympulse.core.model.setVolumeKg
import app.gympulse.core.workout.completedSetCount

/** Input columns of a set row. */
/** [DURATION_MINUTES] is the cardio input: minutes in the field, stored as seconds. */
internal enum class SetField { WEIGHT, EXTRA_WEIGHT, REPS, DURATION, DURATION_MINUTES, DISTANCE }

/**
 * Which values a set records. Bodyweight exercises (with a bodyweight factor) only take
 * additional load in kg; the bodyweight share is added when computing the volume.
 */
internal fun setFieldsFor(
    trackingType: TrackingType,
    isBodyweight: Boolean,
    isCardio: Boolean = false,
): List<SetField> = if (isCardio) listOf(SetField.DURATION_MINUTES) else when (trackingType) {
    TrackingType.WEIGHT_REPS -> listOf(SetField.WEIGHT, SetField.REPS)
    TrackingType.REPS -> if (isBodyweight) listOf(SetField.EXTRA_WEIGHT, SetField.REPS) else listOf(SetField.REPS)
    TrackingType.DURATION -> listOf(SetField.DURATION)
    TrackingType.DISTANCE -> listOf(SetField.DISTANCE, SetField.DURATION)
}

/**
 * Builds the screen model. The live volume uses the same rule as
 * [app.gympulse.core.data.repository.WorkoutRepository.finishSession]: completed sets without
 * warm-ups, including the bodyweight share.
 *
 * @param previousSets sets of the last completed session per exercise id
 */
internal fun WorkoutSession.toUi(
    catalog: Map<String, Exercise>,
    previousSets: Map<String, List<SetEntry>>,
): ActiveSessionUi {
    var volume = 0.0
    var openSets = 0
    val exerciseUis = exercises.map { sessionExercise ->
        val exercise = catalog[sessionExercise.exerciseId]
        val previous = previousSets[sessionExercise.exerciseId].orEmpty()
        val previousWarmups = previous.filter { it.setType == SetType.WARMUP }
        val previousWorking = previous.filter { it.setType != SetType.WARMUP }
        var warmupCount = 0
        var workingCount = 0
        val sets = sessionExercise.sets.map { set ->
            val isWarmup = set.setType == SetType.WARMUP
            val number: Int
            val previousSet: SetEntry?
            if (isWarmup) {
                previousSet = previousWarmups.getOrNull(warmupCount)
                warmupCount += 1
                number = warmupCount
            } else {
                previousSet = previousWorking.getOrNull(workingCount)
                workingCount += 1
                number = workingCount
            }
            if (!set.isCompleted) openSets += 1
            if (set.isCompleted && !isWarmup) {
                volume += setVolumeKg(set.weightKg, set.reps, bodyweightKg, exercise?.bodyweightFactor)
            }
            SetUi(
                id = set.id,
                number = number,
                setType = set.setType,
                weightKg = set.weightKg,
                reps = set.reps,
                durationSeconds = set.durationSeconds,
                distanceMeters = set.distanceMeters,
                isCompleted = set.isCompleted,
                previous = previousSet,
            )
        }
        ExerciseUi(
            sessionExerciseId = sessionExercise.id,
            exerciseId = sessionExercise.exerciseId,
            name = exercise?.name.orEmpty(),
            trackingType = exercise?.trackingType ?: TrackingType.WEIGHT_REPS,
            isBodyweight = exercise?.bodyweightFactor != null,
            isCardio = exercise?.category == ExerciseCategory.CARDIO,
            notes = sessionExercise.notes,
            sets = sets,
        )
    }
    return ActiveSessionUi(
        sessionId = id,
        title = title,
        startedAt = startedAt,
        volumeKg = volume,
        completedSets = completedSetCount(),
        openSets = openSets,
        exercises = exerciseUis,
    )
}
