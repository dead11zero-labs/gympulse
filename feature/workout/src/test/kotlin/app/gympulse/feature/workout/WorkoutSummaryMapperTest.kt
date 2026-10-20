package app.gympulse.feature.workout

import app.gympulse.core.model.SessionExercise
import app.gympulse.core.model.SessionStatus
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.SetType
import app.gympulse.core.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class WorkoutSummaryMapperTest {
    private val start = Instant.parse("2026-10-19T10:00:00Z")

    private fun set(id: String, type: SetType, weight: Double?, reps: Int?, completed: Boolean = true) = SetEntry(
        id = id, setIndex = 0, setType = type, weightKg = weight, reps = reps, durationSeconds = null,
        distanceMeters = null, rpe = null, isCompleted = completed, completedAt = null,
    )

    private fun exercise(id: String, sets: List<SetEntry>) = SessionExercise(
        id = "se-$id", exerciseId = id, sortOrder = 0, supersetGroup = null, notes = null,
        equipmentSettingsSnapshot = emptyMap(), sets = sets,
    )

    private fun session(exercises: List<SessionExercise>) = WorkoutSession(
        id = "s", planId = null, title = "Push", startedAt = start, endedAt = null,
        status = SessionStatus.ACTIVE, bodyweightKg = null, rating = null, notes = null,
        totalVolumeKg = null, exercises = exercises,
    )

    @Test
    fun keepsOnlyCompletedSets_numbersWorkingSetsAndMarksWarmups() {
        val bench = exercise(
            "bench",
            listOf(
                set("a", SetType.WARMUP, 40.0, 10),
                set("b", SetType.WORKING, 80.0, 8),
                set("c", SetType.WORKING, 80.0, 8, completed = false),
                set("d", SetType.WORKING, 85.0, 6),
            ),
        )
        val summary = buildWorkoutSummary(
            session = session(listOf(bench)),
            endedAt = start.plusSeconds(1800),
            volumeKg = 1000.0,
            catalog = emptyMap(),
            records = emptyMap(),
        )

        assertEquals(1800, summary.durationSeconds)
        assertEquals(3, summary.completedSets)
        assertEquals(1000.0, summary.volumeKg, 1e-9)
        val sets = summary.exercises.single().sets
        assertEquals(listOf<Int?>(null, 1, 2), sets.map { it.number })
        assertEquals(listOf(40.0, 80.0, 85.0), sets.map { it.weightKg })
        assertNull(summary.exercises.single().name)
    }

    @Test
    fun skipsExercisesWithoutCompletedSets_andAttachesRecords() {
        val bench = exercise("bench", listOf(set("a", SetType.WORKING, 80.0, 8)))
        val squat = exercise("squat", listOf(set("b", SetType.WORKING, 100.0, 5, completed = false)))
        val record = PersonalRecord(RecordType.BEST_WEIGHT, 80.0, 70.0)

        val summary = buildWorkoutSummary(
            session = session(listOf(bench, squat)),
            endedAt = start,
            volumeKg = 640.0,
            catalog = emptyMap(),
            records = mapOf("bench" to listOf(record)),
        )

        assertEquals(listOf("bench"), summary.exercises.map { it.exerciseId })
        assertEquals(listOf(record), summary.exercises.single().records)
        assertEquals(1, summary.recordCount)
    }

    @Test
    fun endBeforeStart_givesZeroDuration() {
        val summary = buildWorkoutSummary(
            session = session(emptyList()),
            endedAt = start.minusSeconds(5),
            volumeKg = 0.0,
            catalog = emptyMap(),
            records = emptyMap(),
        )

        assertEquals(0, summary.durationSeconds)
    }
}
