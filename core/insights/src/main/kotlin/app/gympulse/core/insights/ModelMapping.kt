package app.gympulse.core.insights

import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.PlanExercise
import app.gympulse.core.model.SessionStatus
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.WorkoutPlan
import app.gympulse.core.model.WorkoutSession
import java.time.ZoneId

/**
 * Baut aus den Domänenmodellen von `core:model` die Eingaben der Auswertung.
 *
 * - Nur abgeschlossene Sessions zählen; das Datum ergibt sich aus `startedAt` in [zoneId].
 * - Kommt eine Übung in einer Session mehrfach vor, werden ihre Sätze zusammengefasst.
 * - Wiederholungsbereich und Satzzahl stammen aus dem Plan der jüngsten Session mit dieser Übung,
 *   ersatzweise aus dem ersten nicht archivierten Plan, der sie enthält.
 * - Übungen ohne Stammdaten bekommen ein neutrales Profil (e1RM ja, Volumen ohne Muskelgruppen).
 */
fun buildExerciseHistories(
    sessions: List<WorkoutSession>,
    exercises: Collection<Exercise>,
    plans: List<WorkoutPlan> = emptyList(),
    zoneId: ZoneId,
    weightIncrementsKg: Map<String, Double> = emptyMap(),
): List<ExerciseHistory> {
    val exercisesById = exercises.associateBy { it.id }
    val plansById = plans.associateBy { it.id }
    val completed = sessions
        .filter { it.status == SessionStatus.COMPLETED }
        .sortedBy { it.startedAt }

    val sessionsByExercise = linkedMapOf<String, MutableList<ExerciseSession>>()
    val latestPlanByExercise = mutableMapOf<String, String>()

    for (session in completed) {
        val date = session.startedAt.atZone(zoneId).toLocalDate()
        session.exercises
            .sortedBy { it.sortOrder }
            .groupBy { it.exerciseId }
            .forEach { (exerciseId, entries) ->
                val sets = entries.flatMap { entry -> entry.sets.sortedBy { it.setIndex }.map { it.toPerformedSet() } }
                sessionsByExercise.getOrPut(exerciseId) { mutableListOf() }
                    .add(ExerciseSession(session.id, date, sets, session.bodyweightKg))
                session.planId?.let { latestPlanByExercise[exerciseId] = it }
            }
    }

    return sessionsByExercise.map { (exerciseId, exerciseSessions) ->
        val target = latestPlanByExercise[exerciseId]
            ?.let { planId -> plansById[planId]?.exercises?.firstOrNull { it.exerciseId == exerciseId } }
            ?: plans.asSequence()
                .filter { !it.isArchived }
                .flatMap { it.exercises.asSequence() }
                .firstOrNull { it.exerciseId == exerciseId }
        ExerciseHistory(
            profile = profileOf(exerciseId, exercisesById[exerciseId], target, weightIncrementsKg[exerciseId]),
            sessions = exerciseSessions,
        )
    }
}

private fun profileOf(
    exerciseId: String,
    exercise: Exercise?,
    target: PlanExercise?,
    incrementKg: Double?,
): ExerciseProfile {
    val repMin = target?.targetRepsMin
    val repMax = target?.targetRepsMax
    val range = when {
        repMin != null && repMax != null && repMin >= 1 && repMax >= repMin -> RepRange(repMin, repMax)
        repMin == null && repMax != null && repMax >= 1 -> RepRange(repMax, repMax)
        repMin != null && repMax == null && repMin >= 1 -> RepRange(repMin, repMin)
        else -> null
    }
    return ExerciseProfile(
        exerciseId = exerciseId,
        equipmentType = exercise?.equipmentType ?: EquipmentType.OTHER,
        category = exercise?.category ?: ExerciseCategory.STRENGTH,
        primaryMuscles = exercise?.primaryMuscles.orEmpty(),
        secondaryMuscles = exercise?.secondaryMuscles.orEmpty(),
        bodyweightFactor = exercise?.bodyweightFactor,
        targetRepRange = range,
        targetSets = target?.targetSets?.takeIf { it > 0 },
        weightIncrementKg = incrementKg,
    )
}

private fun SetEntry.toPerformedSet() = PerformedSet(
    weightKg = weightKg,
    reps = reps,
    rpe = rpe,
    setType = setType,
    isCompleted = isCompleted,
)
