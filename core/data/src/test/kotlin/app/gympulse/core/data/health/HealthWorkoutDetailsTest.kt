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

    private fun seconds(value: Long): Instant = start.plusSeconds(value)

    @Test
    fun everyCompletedSetBecomesASegmentEndingWhenItWasTickedOff() {
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
        assertEquals(
            listOf(HealthSegmentType.BENCH_PRESS, HealthSegmentType.BENCH_PRESS, HealthSegmentType.SQUAT),
            segments.map { it.type },
        )
        assertEquals(listOf(start, minutes(5), minutes(10)), segments.map { it.startTime })
        assertEquals(listOf(minutes(5), minutes(10), minutes(25)), segments.map { it.endTime })
        assertEquals(listOf(8, 6, 5), segments.map { it.repetitions })
        assertEquals(listOf(0, 1, 0), segments.map { it.setIndex })
    }

    @Test
    fun supersetSetsAlternateInTheOrderTheyWereTickedOff() {
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
            listOf(
                HealthSegmentType.ARM_CURL,
                HealthSegmentType.DOUBLE_ARM_TRICEPS_EXTENSION,
                HealthSegmentType.ARM_CURL,
                HealthSegmentType.DOUBLE_ARM_TRICEPS_EXTENSION,
            ),
            segments.map { it.type },
        )
        assertEquals(listOf(start, minutes(3), minutes(6), minutes(9)), segments.map { it.startTime })
        assertEquals(listOf(0, 0, 1, 1), segments.map { it.setIndex })
    }

    @Test
    fun setIndexCountsCompletedSetsPerExerciseFromZero() {
        // Offene Sätze fehlen bereits in der Liste, die gespeicherten Satznummern haben dann Lücken.
        val segments = HealthSegmentPlanner.plan(
            start,
            end,
            listOf(
                exercise("Row", HealthSegmentType.DUMBBELL_ROW, set(2), set(5), set(7)),
                exercise("Plank", HealthSegmentType.PLANK, set(1, reps = null)),
                exercise("Lunge", HealthSegmentType.LUNGE, set(0), set(3)),
            ),
        )

        assertValid(segments)
        assertEquals(listOf(0, 1, 2, 0, 0, 1), segments.map { it.setIndex })
    }

    @Test
    fun weightIsOnlyExportedWhenTheSetHasAPositiveWeight() {
        val segments = HealthSegmentPlanner.plan(
            start,
            end,
            listOf(
                exercise(
                    "Bench",
                    HealthSegmentType.BENCH_PRESS,
                    set(0, weightKg = 62.5),
                    set(1, weightKg = null),
                    set(2, weightKg = 0.0),
                    set(3, weightKg = -10.0),
                    set(4, weightKg = Double.NaN),
                ),
            ),
        )

        assertEquals(listOf(62.5, null, null, null, null), segments.map { it.weightKg })
    }

    @Test
    fun rpeIsOnlyExportedWithinTheBorgScale() {
        val segments = HealthSegmentPlanner.plan(
            start,
            end,
            listOf(
                exercise(
                    "Squat",
                    HealthSegmentType.SQUAT,
                    set(0).copy(rpe = 8.5),
                    set(1).copy(rpe = 10.0),
                    set(2),
                    set(3).copy(rpe = 11.0),
                    set(4).copy(rpe = -1.0),
                ),
            ),
        )

        assertEquals(listOf(8.5, 10.0, null, null, null), segments.map { it.rpe })
    }

    @Test
    fun missingTimestampsSplitTheSessionEvenlyAndEachWindowPerSet() {
        val segments = HealthSegmentPlanner.plan(
            start,
            end,
            listOf(
                exercise("A", HealthSegmentType.BENCH_PRESS, set(0, completedAt = minutes(5))),
                exercise("B", HealthSegmentType.SQUAT, set(0), set(1)),
                exercise("C", HealthSegmentType.DEADLIFT, set(0, completedAt = minutes(50))),
            ),
        )

        assertValid(segments)
        assertEquals(listOf(start, minutes(20), minutes(30), minutes(40)), segments.map { it.startTime })
        assertEquals(listOf(minutes(20), minutes(30), minutes(40), end), segments.map { it.endTime })
        assertEquals(listOf(0, 0, 1, 0), segments.map { it.setIndex })
    }

    @Test
    fun setsTickedOffTooQuicklyAreSpreadEvenlyWithinTheirExerciseWindow() {
        val segments = HealthSegmentPlanner.plan(
            start,
            end,
            listOf(
                // Zwei Sekunden Abstand: zu kurz für einen eigenen Abschnitt.
                exercise(
                    "A",
                    HealthSegmentType.BENCH_PRESS,
                    set(0, completedAt = minutes(10)),
                    set(1, completedAt = minutes(10).plusSeconds(2)),
                ),
                exercise(
                    "B",
                    HealthSegmentType.SQUAT,
                    set(0, completedAt = minutes(20)),
                    set(1, completedAt = minutes(30)),
                ),
            ),
        )

        assertValid(segments)
        assertEquals(
            listOf(start, seconds(301), seconds(602), minutes(20)),
            segments.map { it.startTime },
        )
        assertEquals(
            listOf(seconds(301), seconds(602), minutes(20), minutes(30)),
            segments.map { it.endTime },
        )
        assertEquals(listOf(0, 1, 0, 1), segments.map { it.setIndex })
    }

    @Test
    fun setsTickedOffAtOnceOrOutsideTheSessionFallBackToEvenSplit() {
        val bulk = HealthSegmentPlanner.plan(
            start,
            end,
            listOf(
                exercise(
                    "A",
                    HealthSegmentType.BENCH_PRESS,
                    set(0, completedAt = minutes(59)),
                    set(1, completedAt = minutes(59)),
                ),
                exercise("B", HealthSegmentType.SQUAT, set(0, completedAt = minutes(59))),
            ),
        )
        assertValid(bulk)
        assertEquals(listOf(start, minutes(15), minutes(30)), bulk.map { it.startTime })
        assertEquals(listOf(minutes(15), minutes(30), end), bulk.map { it.endTime })

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
        assertTrue(HealthSegmentPlanner.plan(start, end, listOf(exercise("A", HealthSegmentType.SQUAT))).isEmpty())
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
        assertEquals(listOf(0, 0, 4), segments.map { it.repetitions })
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
