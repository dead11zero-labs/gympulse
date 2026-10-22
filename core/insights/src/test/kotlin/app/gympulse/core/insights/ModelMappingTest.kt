package app.gympulse.core.insights

import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MuscleGroup
import app.gympulse.core.model.PlanExercise
import app.gympulse.core.model.SessionExercise
import app.gympulse.core.model.SessionStatus
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.SetType
import app.gympulse.core.model.TrackingType
import app.gympulse.core.model.WorkoutPlan
import app.gympulse.core.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ModelMappingTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    private val benchExercise = Exercise(
        id = "bench",
        slug = "barbell-bench-press",
        name = "Bench Press",
        category = ExerciseCategory.STRENGTH,
        equipmentType = EquipmentType.BARBELL,
        trackingType = TrackingType.WEIGHT_REPS,
        bodyweightFactor = null,
        primaryMuscles = listOf(MuscleGroup.CHEST),
        secondaryMuscles = listOf(MuscleGroup.TRICEPS),
        instructions = emptyList(),
        imageUrls = emptyList(),
        notes = null,
        isCustom = false,
        isFavorite = false,
        defaultRestSeconds = null,
        equipmentId = null,
    )

    private fun setEntry(index: Int, kg: Double?, reps: Int?, type: SetType = SetType.WORKING, completed: Boolean = true, rpe: Double? = null) =
        SetEntry(
            id = "set-$index-$kg-$reps",
            setIndex = index,
            setType = type,
            weightKg = kg,
            reps = reps,
            durationSeconds = null,
            distanceMeters = null,
            rpe = rpe,
            isCompleted = completed,
            completedAt = null,
        )

    private fun sessionExercise(id: String, exerciseId: String, sortOrder: Int, sets: List<SetEntry>) = SessionExercise(
        id = id,
        exerciseId = exerciseId,
        sortOrder = sortOrder,
        supersetGroup = null,
        notes = null,
        equipmentSettingsSnapshot = emptyMap(),
        sets = sets,
    )

    private fun workout(
        id: String,
        startedAt: Instant,
        exercises: List<SessionExercise>,
        status: SessionStatus = SessionStatus.COMPLETED,
        planId: String? = null,
        bodyweightKg: Double? = null,
    ) = WorkoutSession(
        id = id,
        planId = planId,
        title = null,
        startedAt = startedAt,
        endedAt = startedAt.plusSeconds(3600),
        status = status,
        bodyweightKg = bodyweightKg,
        rating = null,
        notes = null,
        totalVolumeKg = null,
        exercises = exercises,
    )

    private fun plan(id: String, exerciseId: String, repsMin: Int?, repsMax: Int?, sets: Int = 3, archived: Boolean = false) =
        WorkoutPlan(
            id = id,
            name = id,
            description = null,
            color = null,
            isArchived = archived,
            exercises = listOf(
                PlanExercise(
                    id = "pe-$id",
                    exerciseId = exerciseId,
                    sortOrder = 0,
                    supersetGroup = null,
                    targetSets = sets,
                    targetRepsMin = repsMin,
                    targetRepsMax = repsMax,
                    targetWeightKg = null,
                    restSeconds = null,
                ),
            ),
        )

    @Test
    fun mapsCompletedSessionsWithProfileAndSets() {
        val sessions = listOf(
            workout(
                "w1",
                Instant.parse("2026-03-02T17:00:00Z"),
                listOf(sessionExercise("se1", "bench", 0, listOf(setEntry(1, 80.0, 10, rpe = 8.0), setEntry(0, 40.0, 10, SetType.WARMUP)))),
                bodyweightKg = 82.0,
            ),
            workout("w2", Instant.parse("2026-03-04T17:00:00Z"), listOf(sessionExercise("se2", "bench", 0, listOf(setEntry(0, 90.0, 10)))), SessionStatus.DISCARDED),
            workout("w3", Instant.parse("2026-03-05T17:00:00Z"), listOf(sessionExercise("se3", "bench", 0, listOf(setEntry(0, 90.0, 10)))), SessionStatus.ACTIVE),
        )

        val histories = buildExerciseHistories(sessions, listOf(benchExercise), zoneId = berlin)
        val history = histories.single()
        assertEquals("bench", history.profile.exerciseId)
        assertEquals(EquipmentType.BARBELL, history.profile.equipmentType)
        assertEquals(listOf(MuscleGroup.CHEST), history.profile.primaryMuscles)
        assertEquals(listOf(MuscleGroup.TRICEPS), history.profile.secondaryMuscles)
        assertNull(history.profile.targetRepRange)

        val session = history.sessions.single()
        assertEquals("w1", session.sessionId)
        assertEquals(LocalDate.of(2026, 3, 2), session.date)
        assertEquals(82.0, session.bodyweightKg!!, 0.0)
        // Sätze nach setIndex sortiert
        assertEquals(listOf(SetType.WARMUP, SetType.WORKING), session.sets.map { it.setType })
        assertEquals(8.0, session.sets[1].rpe!!, 0.0)
    }

    @Test
    fun dateUsesGivenTimeZone() {
        // 23:30 UTC ist in Berlin schon der nächste Tag
        val sessions = listOf(
            workout("w1", Instant.parse("2026-03-02T23:30:00Z"), listOf(sessionExercise("se", "bench", 0, listOf(setEntry(0, 80.0, 10))))),
        )
        assertEquals(LocalDate.of(2026, 3, 3), buildExerciseHistories(sessions, listOf(benchExercise), zoneId = berlin).single().sessions.single().date)
        assertEquals(LocalDate.of(2026, 3, 2), buildExerciseHistories(sessions, listOf(benchExercise), zoneId = ZoneId.of("UTC")).single().sessions.single().date)
    }

    @Test
    fun sameExerciseTwiceInSession_isMerged() {
        val sessions = listOf(
            workout(
                "w1",
                Instant.parse("2026-03-02T17:00:00Z"),
                listOf(
                    sessionExercise("se1", "bench", 0, listOf(setEntry(0, 80.0, 10))),
                    sessionExercise("se2", "bench", 2, listOf(setEntry(0, 70.0, 12))),
                ),
            ),
        )
        val session = buildExerciseHistories(sessions, listOf(benchExercise), zoneId = berlin).single().sessions.single()
        assertEquals(listOf(80.0, 70.0), session.sets.map { it.weightKg })
    }

    @Test
    fun repRangeComesFromPlanOfLatestSession() {
        val sessions = listOf(
            workout("w1", Instant.parse("2026-03-02T17:00:00Z"), listOf(sessionExercise("a", "bench", 0, listOf(setEntry(0, 80.0, 10)))), planId = "strength"),
            workout("w2", Instant.parse("2026-03-09T17:00:00Z"), listOf(sessionExercise("b", "bench", 0, listOf(setEntry(0, 70.0, 12)))), planId = "hypertrophy"),
        )
        val plans = listOf(plan("strength", "bench", 3, 5, sets = 5), plan("hypertrophy", "bench", 8, 12, sets = 3))
        val profile = buildExerciseHistories(sessions, listOf(benchExercise), plans, berlin).single().profile
        assertEquals(RepRange(8, 12), profile.targetRepRange)
        assertEquals(3, profile.targetSets)
    }

    @Test
    fun withoutSessionPlan_fallsBackToFirstActivePlan() {
        val sessions = listOf(
            workout("w1", Instant.parse("2026-03-02T17:00:00Z"), listOf(sessionExercise("a", "bench", 0, listOf(setEntry(0, 80.0, 10))))),
        )
        val plans = listOf(plan("old", "bench", 1, 3, archived = true), plan("current", "bench", 6, 10))
        assertEquals(RepRange(6, 10), buildExerciseHistories(sessions, listOf(benchExercise), plans, berlin).single().profile.targetRepRange)
    }

    @Test
    fun partialOrInvalidRepTargets() {
        val sessions = listOf(
            workout("w1", Instant.parse("2026-03-02T17:00:00Z"), listOf(sessionExercise("a", "bench", 0, listOf(setEntry(0, 80.0, 10)))), planId = "p"),
        )
        fun rangeFor(min: Int?, max: Int?) =
            buildExerciseHistories(sessions, listOf(benchExercise), listOf(plan("p", "bench", min, max)), berlin).single().profile.targetRepRange
        assertEquals(RepRange(10, 10), rangeFor(null, 10))
        assertEquals(RepRange(5, 5), rangeFor(5, null))
        assertNull(rangeFor(null, null))
        assertNull(rangeFor(12, 8))
        assertNull(rangeFor(0, 0))
    }

    @Test
    fun unknownExercise_getsNeutralProfile() {
        val sessions = listOf(
            workout("w1", Instant.parse("2026-03-02T17:00:00Z"), listOf(sessionExercise("a", "mystery", 0, listOf(setEntry(0, 30.0, 10))))),
        )
        val history = buildExerciseHistories(sessions, emptyList(), zoneId = berlin, weightIncrementsKg = mapOf("mystery" to 1.0)).single()
        assertEquals(EquipmentType.OTHER, history.profile.equipmentType)
        assertTrue(history.profile.primaryMuscles.isEmpty())
        assertEquals(1.0, history.profile.weightIncrementKg!!, 0.0)
    }

    @Test
    fun mappedHistoriesFeedTheEngine() {
        val sessions = (0 until 3).map { week ->
            workout(
                "w$week",
                Instant.parse("2026-03-02T17:00:00Z").plusSeconds(week * 7L * 86_400),
                listOf(sessionExercise("se$week", "bench", 0, List(3) { setEntry(it, 80.0, 12) })),
                planId = "p",
            )
        }
        val histories = buildExerciseHistories(sessions, listOf(benchExercise), listOf(plan("p", "bench", 8, 12)), berlin)
        val insights = InsightsEngine().analyze(histories, LocalDate.of(2026, 3, 22))
        val progression = insights.exercise("bench")!!.progression!!
        assertEquals(ProgressionAction.INCREASE_WEIGHT, progression.action)
        assertEquals(82.5, progression.suggestedWeightKg, 0.0)
    }
}
