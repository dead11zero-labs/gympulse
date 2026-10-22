package app.gympulse.core.workout

import app.gympulse.core.model.Exercise
import app.gympulse.core.model.SetType
import app.gympulse.core.model.WorkoutSession
import java.time.Duration
import java.time.Instant

/**
 * Builds the summary of a just-finished session.
 *
 * @param session the session as it was before finishing (sets are unchanged by finishing)
 * @param volumeKg stored total volume of the finished session
 * @param records records per exercise id from [PersonalRecordDetector]
 */
fun buildWorkoutSummary(
    session: WorkoutSession,
    endedAt: Instant,
    volumeKg: Double,
    catalog: Map<String, Exercise>,
    records: Map<String, List<PersonalRecord>>,
): WorkoutSummary {
    val exercises = session.exercises.mapNotNull { sessionExercise ->
        val done = sessionExercise.sets.filter { it.isCompleted && it.hasValues() }
        if (done.isEmpty()) return@mapNotNull null
        var workingNumber = 0
        SummaryExercise(
            exerciseId = sessionExercise.exerciseId,
            name = catalog[sessionExercise.exerciseId]?.name,
            sets = done.map { set ->
                val isWarmup = set.setType == SetType.WARMUP
                if (!isWarmup) workingNumber += 1
                SummarySet(
                    number = if (isWarmup) null else workingNumber,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    durationSeconds = set.durationSeconds,
                    distanceMeters = set.distanceMeters,
                )
            },
            records = records[sessionExercise.exerciseId].orEmpty(),
        )
    }
    return WorkoutSummary(
        title = session.title,
        durationSeconds = Duration.between(session.startedAt, endedAt).seconds.coerceAtLeast(0),
        completedSets = session.completedSetCount(),
        volumeKg = volumeKg,
        exercises = exercises,
    )
}
