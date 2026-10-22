package app.gympulse.core.wearprotocol

import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.PlanExercise
import app.gympulse.core.model.SessionExercise
import app.gympulse.core.model.SessionStatus
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.SetType
import app.gympulse.core.model.TrackingType
import app.gympulse.core.model.WorkoutPlan
import app.gympulse.core.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SnapshotMappingTest {
    private val started = Instant.ofEpochMilli(1_000_000)

    private fun exercise(id: String, name: String, tracking: TrackingType = TrackingType.WEIGHT_REPS, bodyweightFactor: Double? = null, rest: Int? = null) =
        Exercise(
            id = id, slug = null, name = name, category = ExerciseCategory.STRENGTH, equipmentType = EquipmentType.BARBELL,
            trackingType = tracking, bodyweightFactor = bodyweightFactor, primaryMuscles = emptyList(),
            secondaryMuscles = emptyList(), instructions = emptyList(), imageUrls = emptyList(), notes = null,
            isCustom = false, isFavorite = false, defaultRestSeconds = rest, equipmentId = null,
        )

    private fun entry(id: String, index: Int, type: SetType = SetType.WORKING, weight: Double? = 60.0, reps: Int? = 10, completedAt: Instant? = null) =
        SetEntry(
            id = id, setIndex = index, setType = type, weightKg = weight, reps = reps, durationSeconds = null,
            distanceMeters = null, rpe = null, isCompleted = completedAt != null, completedAt = completedAt,
        )

    private val catalog = mapOf(
        "squat" to exercise("squat", "Kniebeuge", rest = 180),
        "dip" to exercise("dip", "Dips", TrackingType.REPS, bodyweightFactor = 0.9),
    )

    private val session = WorkoutSession(
        id = "s1", planId = "p1", title = "Leg Day", startedAt = started, endedAt = null, status = SessionStatus.ACTIVE,
        bodyweightKg = 80.0, rating = null, notes = null, totalVolumeKg = null,
        exercises = listOf(
            SessionExercise(
                id = "se2", exerciseId = "dip", sortOrder = 1, supersetGroup = 1, notes = null,
                equipmentSettingsSnapshot = emptyMap(), sets = listOf(entry("d1", 0, weight = 10.0)),
            ),
            SessionExercise(
                id = "se1", exerciseId = "squat", sortOrder = 0, supersetGroup = null, notes = null,
                equipmentSettingsSnapshot = emptyMap(),
                sets = listOf(
                    entry("w1", 0, SetType.WARMUP, weight = 40.0, completedAt = Instant.ofEpochMilli(1_100_000)),
                    entry("k2", 2, weight = 100.0, reps = 5),
                    entry("k1", 1, weight = 100.0, reps = 5),
                ),
            ),
        ),
    )

    private val previous = mapOf(
        "squat" to listOf(
            entry("pw", 0, SetType.WARMUP, weight = 35.0, reps = 10),
            entry("p1", 1, weight = 97.5, reps = 5),
        ),
    )

    @Test
    fun mapsSessionWithNamesNumbersPreviousAndRest() {
        val snapshot = session.toSessionSnapshot(
            catalog = catalog,
            previousSets = previous,
            restEndsAt = Instant.ofEpochMilli(1_200_000),
            defaultRestSeconds = 120,
            setUpdatedAt = mapOf("k1" to Instant.ofEpochMilli(1_150_000)),
        )
        assertEquals("s1", snapshot.sessionId)
        assertEquals("p1", snapshot.planId)
        assertEquals("Leg Day", snapshot.title)
        assertEquals(1_000_000L, snapshot.startedAt)
        assertEquals(1_200_000L, snapshot.restEndsAt)

        val (squat, dip) = snapshot.exercises
        assertEquals("Kniebeuge", squat.name)
        assertEquals(180, squat.restSeconds)
        assertEquals(WearTrackingType.WEIGHT_REPS, squat.trackingType)
        assertFalse(squat.isBodyweight)
        assertEquals(listOf("w1", "k1", "k2"), squat.sets.map { it.setId })
        assertEquals(listOf(1, 1, 2), squat.sets.map { it.number })
        assertEquals(listOf(WearSetType.WARMUP, WearSetType.WORKING, WearSetType.WORKING), squat.sets.map { it.setType })

        val (warmup, first, second) = squat.sets
        assertTrue(warmup.isCompleted)
        assertEquals(1_100_000L, warmup.completedAt)
        assertEquals(1_100_000L, warmup.updatedAt) // fallback: completedAt
        assertEquals(PreviousSet(weightKg = 35.0, reps = 10), warmup.previous)
        assertEquals(PreviousSet(weightKg = 97.5, reps = 5), first.previous)
        assertEquals(1_150_000L, first.updatedAt) // explicit updated_at
        assertNull(second.previous)
        assertEquals(1_000_000L, second.updatedAt) // fallback: startedAt
        assertEquals(100.0, first.weightKg!!, 0.0)

        assertEquals("Dips", dip.name)
        assertEquals(WearTrackingType.REPS, dip.trackingType)
        assertTrue(dip.isBodyweight)
        assertEquals(1, dip.supersetGroup)
        assertEquals(120, dip.restSeconds) // default from settings
        assertEquals(10.0, dip.sets.single().weightKg!!, 0.0)
    }

    @Test
    fun unknownExercise_getsEmptyNameAndDefaults() {
        val snapshot = session.toSessionSnapshot(catalog = emptyMap())
        assertTrue(snapshot.exercises.all { it.name.isEmpty() && it.trackingType == WearTrackingType.WEIGHT_REPS })
        assertTrue(snapshot.exercises.all { it.restSeconds == null })
    }

    @Test
    fun buildSnapshot_onlyForActiveSessions_andLimitsAcks() {
        val acks = (1..100).map { CommandAck(Fixtures.cmdId(it)) }
        val active = buildWorkoutSnapshot(version = 5, session = session, catalog = catalog, acks = acks)
        assertEquals(5L, active.version)
        assertEquals(WearProtocol.VERSION, active.protocolVersion)
        assertEquals("s1", active.session?.sessionId)
        assertEquals(WearProtocol.MAX_ACKS, active.acks.size)
        assertEquals(Fixtures.cmdId(100), active.acks.last().commandId)

        assertNull(buildWorkoutSnapshot(6, session.copy(status = SessionStatus.COMPLETED), catalog).session)
        assertNull(buildWorkoutSnapshot(7, session.copy(status = SessionStatus.DISCARDED), catalog).session)
        assertNull(buildWorkoutSnapshot(8, null, catalog).session)
    }

    @Test
    fun mappedSnapshot_survivesRoundTrip() {
        val snapshot = buildWorkoutSnapshot(9, session, catalog, previous, Instant.ofEpochMilli(1_200_000), 90)
        assertEquals(snapshot, WearProtocol.decodeSnapshot(WearProtocol.encodeSnapshot(snapshot)))
    }

    @Test
    fun planSummaries_skipArchived() {
        val planExercise = PlanExercise("pe", "squat", 0, null, 3, 5, 8, 100.0, 120)
        val plans = listOf(
            WorkoutPlan("p1", "Push", null, 0xFFFF0000, false, listOf(planExercise, planExercise)),
            WorkoutPlan("p2", "Alt", null, null, true, emptyList()),
            WorkoutPlan("p3", "Pull", "desc", null, false, listOf(planExercise)),
        )
        val list = plans.toPlanSummaryList(Instant.ofEpochMilli(5))
        assertEquals(5L, list.generatedAt)
        assertEquals(
            listOf(PlanSummary("p1", "Push", 2, 0xFFFF0000), PlanSummary("p3", "Pull", 1, null)),
            list.plans,
        )
    }

    @Test
    fun enumMappingsAreComplete() {
        assertEquals(TrackingType.entries.size, TrackingType.entries.map { it.toWire() }.toSet().size)
        assertEquals(SetType.entries.size, SetType.entries.map { it.toWire() }.toSet().size)
    }
}
