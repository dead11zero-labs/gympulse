package app.gympulse.core.wearprotocol

import app.gympulse.core.model.Exercise
import app.gympulse.core.model.SessionStatus
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.SetType
import app.gympulse.core.model.TrackingType
import app.gympulse.core.model.WorkoutPlan
import app.gympulse.core.model.WorkoutSession
import java.time.Instant

/**
 * Baut den Snapshot für die Uhr. Eine Session, die nicht mehr [SessionStatus.ACTIVE] ist (oder null),
 * ergibt einen Snapshot ohne Session; die Uhr zeigt dann wieder die Planauswahl.
 *
 * @param version vom Telefon fortlaufend vergebene, monotone Version
 * @param acks Quittungen; es werden nur die letzten [WearProtocol.MAX_ACKS] übernommen
 * @see WorkoutSession.toSessionSnapshot
 */
fun buildWorkoutSnapshot(
    version: Long,
    session: WorkoutSession?,
    catalog: Map<String, Exercise>,
    previousSets: Map<String, List<SetEntry>> = emptyMap(),
    restEndsAt: Instant? = null,
    defaultRestSeconds: Int? = null,
    setUpdatedAt: Map<String, Instant> = emptyMap(),
    acks: List<CommandAck> = emptyList(),
): WorkoutSnapshot = WorkoutSnapshot(
    version = version,
    session = session
        ?.takeIf { it.status == SessionStatus.ACTIVE }
        ?.toSessionSnapshot(catalog, previousSets, restEndsAt, defaultRestSeconds, setUpdatedAt),
    acks = acks.takeLast(WearProtocol.MAX_ACKS),
)

/**
 * Mappt eine laufende Session auf das Wire-Format. Nummerierung und „Vorher“-Zuordnung folgen dem
 * Workout-Screen am Telefon: Aufwärm- und Arbeitssätze werden getrennt gezählt und jeweils mit dem
 * gleichnamigen Satz der letzten Session verglichen.
 *
 * @param catalog Übungen nach ID, für Namen (in der Gerätesprache), Erfassungsart und Pausendauer
 * @param previousSets Sätze der letzten abgeschlossenen Session je Übungs-ID
 * @param restEndsAt Ende der laufenden Pause oder null
 * @param defaultRestSeconds Pause aus den Einstellungen, wenn die Übung keine eigene hat
 * @param setUpdatedAt `updated_at` je Satz-ID; fehlt ein Eintrag, gilt `completedAt` bzw. `startedAt`
 */
fun WorkoutSession.toSessionSnapshot(
    catalog: Map<String, Exercise>,
    previousSets: Map<String, List<SetEntry>> = emptyMap(),
    restEndsAt: Instant? = null,
    defaultRestSeconds: Int? = null,
    setUpdatedAt: Map<String, Instant> = emptyMap(),
): SessionSnapshot = SessionSnapshot(
    sessionId = id,
    planId = planId,
    title = title,
    startedAt = startedAt.toEpochMilli(),
    restEndsAt = restEndsAt?.toEpochMilli(),
    exercises = exercises.sortedBy { it.sortOrder }.map { sessionExercise ->
        val exercise = catalog[sessionExercise.exerciseId]
        val previous = previousSets[sessionExercise.exerciseId].orEmpty()
        val previousWarmups = previous.filter { it.setType == SetType.WARMUP }
        val previousWorking = previous.filter { it.setType != SetType.WARMUP }
        var warmupCount = 0
        var workingCount = 0
        ExerciseSnapshot(
            sessionExerciseId = sessionExercise.id,
            exerciseId = sessionExercise.exerciseId,
            name = exercise?.name.orEmpty(),
            trackingType = (exercise?.trackingType ?: TrackingType.WEIGHT_REPS).toWire(),
            isBodyweight = exercise?.bodyweightFactor != null,
            supersetGroup = sessionExercise.supersetGroup,
            restSeconds = exercise?.defaultRestSeconds ?: defaultRestSeconds,
            sets = sessionExercise.sets.sortedBy { it.setIndex }.map { set ->
                val previousSet: SetEntry?
                val number: Int
                if (set.setType == SetType.WARMUP) {
                    previousSet = previousWarmups.getOrNull(warmupCount)
                    warmupCount += 1
                    number = warmupCount
                } else {
                    previousSet = previousWorking.getOrNull(workingCount)
                    workingCount += 1
                    number = workingCount
                }
                SetSnapshot(
                    setId = set.id,
                    number = number,
                    setType = set.setType.toWire(),
                    weightKg = set.weightKg,
                    reps = set.reps,
                    durationSeconds = set.durationSeconds,
                    distanceMeters = set.distanceMeters,
                    isCompleted = set.isCompleted,
                    completedAt = set.completedAt?.toEpochMilli(),
                    updatedAt = (setUpdatedAt[set.id] ?: set.completedAt ?: startedAt).toEpochMilli(),
                    previous = previousSet?.let {
                        PreviousSet(it.weightKg, it.reps, it.durationSeconds, it.distanceMeters)
                    },
                )
            },
        )
    },
)

/** Nicht archivierte Pläne für die Auswahl auf der Uhr, in der übergebenen Reihenfolge. */
fun List<WorkoutPlan>.toPlanSummaryList(generatedAt: Instant): PlanSummaryList = PlanSummaryList(
    generatedAt = generatedAt.toEpochMilli(),
    plans = filterNot { it.isArchived }.map { it.toPlanSummary() },
)

fun WorkoutPlan.toPlanSummary(): PlanSummary = PlanSummary(
    planId = id,
    name = name,
    exerciseCount = exercises.size,
    color = color,
)

fun TrackingType.toWire(): WearTrackingType = when (this) {
    TrackingType.WEIGHT_REPS -> WearTrackingType.WEIGHT_REPS
    TrackingType.REPS -> WearTrackingType.REPS
    TrackingType.DURATION -> WearTrackingType.DURATION
    TrackingType.DISTANCE -> WearTrackingType.DISTANCE
}

fun SetType.toWire(): WearSetType = when (this) {
    SetType.WARMUP -> WearSetType.WARMUP
    SetType.WORKING -> WearSetType.WORKING
    SetType.DROP -> WearSetType.DROP
    SetType.FAILURE -> WearSetType.FAILURE
}
