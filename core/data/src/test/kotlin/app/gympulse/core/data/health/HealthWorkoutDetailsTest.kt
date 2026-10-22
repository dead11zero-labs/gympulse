package app.gympulse.core.data.health

import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.Locale

class HealthWorkoutDetailsTest {

    private val start = Instant.parse("2026-07-01T16:00:00Z")
    private val end = Instant.parse("2026-07-01T17:00:00Z")

    private fun set(
        index: Int,
        reps: Int? = 8,
        weightKg: Double? = 60.0,
        completedAt: Instant? = null,
        durationSeconds: Int? = null,
        distanceMeters: Double? = null,
    ) = SetEntry(
        id = "set-$index",
        setIndex = index,
        setType = SetType.WORKING,
        weightKg = weightKg,
        reps = reps,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        rpe = null,
        isCompleted = true,
        completedAt = completedAt,
    )

    private fun exercise(name: String, type: HealthSegmentType, vararg sets: SetEntry) =
        TrainedExercise(name = name, type = type, sets = sets.toList())

    private fun minutes(value: Long): Instant = start.plus(Duration.ofMinutes(value))

    private fun assertValid(segments: List<HealthWorkoutSegment>) {
        segments.forEach { assertTrue(it.startTime.isBefore(it.endTime)) }
        segments.zipWithNext().forEach { (a, b) -> assertTrue(!a.endTime.isAfter(b.startTime)) }
        assertTrue(!segments.first().startTime.isBefore(start))
        assertTrue(!segments.last().endTime.isAfter(end))
    }

    @Test
    fun timestampsDefineContiguousSegments() {
        val segments = HealthSegmentPlanner.plan(
            start,
            end,
            listOf(
                exercise(
                    "Bench",
                    HealthSegmentType.BENCH_PRESS,
                    set(0, completedAt = minutes(5)),
                    set(1, completedAt = minutes(10), reps = 6),
                ),
                exercise("Squat", HealthSegmentType.SQUAT, set(0, completedAt = minutes(25), reps = 5)),
            ),
        )

        assertValid(segments)
        assertEquals(listOf(HealthSegmentType.BENCH_PRESS, HealthSegmentType.SQUAT), segments.map { it.type })
        assertEquals(start, segments[0].startTime)
        assertEquals(minutes(10), segments[0].endTime)
        assertEquals(minutes(10), segments[1].startTime)
        assertEquals(minutes(25), segments[1].endTime)
        assertEquals(listOf(14, 5), segments.map { it.repetitions })
    }

    @Test
    fun supersetsAreOrderedByTheirLastSetAndNeverOverlap() {
        val segments = HealthSegmentPlanner.plan(
            start,
            end,
            listOf(
                exercise(
                    "Curl",
                    HealthSegmentType.ARM_CURL,
                    set(0, completedAt = minutes(3)),
                    set(1, completedAt = minutes(9)),
                ),
                exercise(
                    "Pushdown",
                    HealthSegmentType.DOUBLE_ARM_TRICEPS_EXTENSION,
                    set(0, completedAt = minutes(6)),
                    set(1, completedAt = minutes(12)),
                ),
            ),
        )

        assertValid(segments)
        assertEquals(
            listOf(HealthSegmentType.ARM_CURL, HealthSegmentType.DOUBLE_ARM_TRICEPS_EXTENSION),
            segments.map { it.type },
        )
        assertEquals(minutes(9), segments[1].startTime)
    }

    @Test
    fun missingTimestampsSplitTheSessionEvenly() {
        val segments = HealthSegmentPlanner.plan(
            start,
            end,
            listOf(
                exercise("A", HealthSegmentType.BENCH_PRESS, set(0, completedAt = minutes(5))),
                exercise("B", HealthSegmentType.SQUAT, set(0)),
                exercise("C", HealthSegmentType.DEADLIFT, set(0, completedAt = minutes(50))),
            ),
        )

        assertValid(segments)
        assertEquals(listOf(start, minutes(20), minutes(40)), segments.map { it.startTime })
        assertEquals(listOf(minutes(20), minutes(40), end), segments.map { it.endTime })
    }

    @Test
    fun setsTickedOffAtOnceOrOutsideTheSessionFallBackToEvenSplit() {
        val bulk = HealthSegmentPlanner.plan(
            start,
            end,
            listOf(
                exercise("A", HealthSegmentType.BENCH_PRESS, set(0, completedAt = minutes(59))),
                exercise("B", HealthSegmentType.SQUAT, set(0, completedAt = minutes(59))),
            ),
        )
        assertEquals(listOf(start, minutes(30)), bulk.map { it.startTime })

        val outside = HealthSegmentPlanner.plan(
            start,
            end,
            listOf(exercise("A", HealthSegmentType.BENCH_PRESS, set(0, completedAt = minutes(61)))),
        )
        assertEquals(listOf(start), outside.map { it.startTime })
        assertEquals(listOf(end), outside.map { it.endTime })
    }

    @Test
    fun emptyInputOrInvalidRangeGivesNoSegments() {
        assertTrue(HealthSegmentPlanner.plan(start, end, emptyList()).isEmpty())
        assertTrue(
            HealthSegmentPlanner.plan(end, start, listOf(exercise("A", HealthSegmentType.SQUAT, set(0)))).isEmpty(),
        )
    }

    @Test
    fun repetitionsIgnoreMissingOrNegativeValues() {
        val segments = HealthSegmentPlanner.plan(
            start,
            end,
            listOf(exercise("Plank", HealthSegmentType.PLANK, set(0, reps = null), set(1, reps = -3), set(2, reps = 4))),
        )
        assertEquals(4, segments.single().repetitions)
    }

    @Test
    fun exerciseLineGroupsEqualConsecutiveSets() {
        val line = HealthWorkoutNotes.exerciseLine(
            exercise(
                "Bankdrücken",
                HealthSegmentType.BENCH_PRESS,
                set(0),
                set(1),
                set(2),
                set(3, reps = 6, weightKg = 62.5),
                set(4),
            ),
            Locale.GERMAN,
        )
        assertEquals("• Bankdrücken: 3 × 8 × 60 kg, 1 × 6 × 62,5 kg, 1 × 8 × 60 kg", line)
    }

    @Test
    fun setSpecCoversBodyweightDurationAndDistance() {
        assertEquals("12", HealthWorkoutNotes.setSpec(set(0, reps = 12, weightKg = null), Locale.GERMAN))
        assertEquals("12", HealthWorkoutNotes.setSpec(set(0, reps = 12, weightKg = 0.0), Locale.GERMAN))
        assertEquals("20 kg", HealthWorkoutNotes.setSpec(set(0, reps = null, weightKg = 20.0), Locale.GERMAN))
        assertEquals(
            "45 s",
            HealthWorkoutNotes.setSpec(set(0, reps = null, weightKg = null, durationSeconds = 45), Locale.GERMAN),
        )
        assertEquals(
            "1:30 min",
            HealthWorkoutNotes.setSpec(set(0, reps = null, weightKg = null, durationSeconds = 90), Locale.GERMAN),
        )
        assertEquals(
            "20 min / 3,25 km",
            HealthWorkoutNotes.setSpec(
                set(0, reps = null, weightKg = null, durationSeconds = 1200, distanceMeters = 3250.0),
                Locale.GERMAN,
            ),
        )
        assertEquals(
            "400 m",
            HealthWorkoutNotes.setSpec(set(0, reps = null, weightKg = null, distanceMeters = 400.0), Locale.ENGLISH),
        )
        assertEquals("8 × 62.5 kg", HealthWorkoutNotes.setSpec(set(0, weightKg = 62.5), Locale.ENGLISH))
        assertEquals("–", HealthWorkoutNotes.setSpec(set(0, reps = null, weightKg = null), Locale.GERMAN))
    }

    @Test
    fun notesCombineOwnNotesAndExerciseList() {
        val exercises = listOf(
            exercise("Latzug", HealthSegmentType.LAT_PULL_DOWN, set(0, reps = 10, weightKg = 50.0)),
            exercise("Leer", HealthSegmentType.UNKNOWN),
            exercise("Klimmzüge", HealthSegmentType.PULL_UP, set(0, reps = 6, weightKg = null), set(1, reps = 6, weightKg = null)),
        )

        assertEquals(
            "Guter Tag\n\n• Latzug: 1 × 10 × 50 kg\n• Klimmzüge: 2 × 6",
            HealthWorkoutNotes.build("  Guter Tag ", exercises, Locale.GERMAN),
        )
        assertEquals(
            "• Latzug: 1 × 10 × 50 kg\n• Klimmzüge: 2 × 6",
            HealthWorkoutNotes.build(" ", exercises, Locale.GERMAN),
        )
        assertEquals("Nur Notiz", HealthWorkoutNotes.build("Nur Notiz", emptyList(), Locale.GERMAN))
        assertNull(HealthWorkoutNotes.build(null, emptyList(), Locale.GERMAN))
    }

    @Test
    fun longNotesAreCutAtWholeLines() {
        val exercises = (1..80).map { index ->
            exercise("Übung Nummer $index", HealthSegmentType.WEIGHTLIFTING, set(0), set(1, reps = 6))
        }

        val notes = HealthWorkoutNotes.build("Notiz", exercises, Locale.GERMAN)!!

        assertTrue(notes.length <= HealthWorkoutNotes.MAX_LENGTH)
        assertTrue(notes.startsWith("Notiz\n\n• Übung Nummer 1: 1 × 8 × 60 kg, 1 × 6 × 60 kg\n"))
        assertTrue(notes.endsWith("\n…"))
        val ownTooLong = HealthWorkoutNotes.build("x".repeat(2000), exercises, Locale.GERMAN)!!
        assertEquals(HealthWorkoutNotes.MAX_LENGTH, ownTooLong.length)
        assertTrue(ownTooLong.endsWith("…"))
    }
}
