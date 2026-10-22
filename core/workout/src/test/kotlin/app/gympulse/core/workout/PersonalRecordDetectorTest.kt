package app.gympulse.core.workout

import app.gympulse.core.data.repository.HistorySet
import app.gympulse.core.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalRecordDetectorTest {
    private val detector = PersonalRecordDetector()

    private var counter = 0

    private fun set(
        session: String,
        weight: Double?,
        reps: Int?,
        exercise: String = "bench",
        type: SetType = SetType.WORKING,
        completed: Boolean = true,
        deletedAt: Long? = null,
        bodyweight: Double? = null,
        factor: Double? = null,
    ) = HistorySet(
        setId = "s${counter++}",
        sessionId = session,
        exerciseId = exercise,
        setType = type,
        weightKg = weight,
        reps = reps,
        isCompleted = completed,
        deletedAt = deletedAt,
        bodyweightKg = bodyweight,
        bodyweightFactor = factor,
    )

    private fun types(records: List<PersonalRecord>?) = records.orEmpty().map { it.type }.toSet()

    @Test
    fun firstSession_hasNoRecords() {
        val result = detector.detect("now", listOf(set("now", 100.0, 5)))

        assertTrue(result.isEmpty())
    }

    @Test
    fun heavierSet_isWeightOneRmAndVolumeRecord() {
        val result = detector.detect("now", listOf(set("old", 80.0, 5), set("now", 100.0, 5)))

        val records = result.getValue("bench")
        assertEquals(RecordType.entries.toSet(), types(records))
        val weight = records.first { it.type == RecordType.BEST_WEIGHT }
        assertEquals(100.0, weight.valueKg, 1e-9)
        assertEquals(80.0, weight.previousKg, 1e-9)
        val oneRm = records.first { it.type == RecordType.ESTIMATED_ONE_REP_MAX }
        assertEquals(100.0 * (1 + 5 / 30.0), oneRm.valueKg, 1e-9)
        val volume = records.first { it.type == RecordType.BEST_SET_VOLUME }
        assertEquals(500.0, volume.valueKg, 1e-9)
        assertEquals(400.0, volume.previousKg, 1e-9)
    }

    @Test
    fun tie_isNoRecord() {
        val result = detector.detect("now", listOf(set("old", 100.0, 5), set("now", 100.0, 5)))

        assertTrue(result.isEmpty())
    }

    @Test
    fun lighterButMoreReps_isOnlyOneRmAndVolumeRecord() {
        // 90 x 12: 1RM 126 > 116.67 and volume 1080 > 500, but 90 < 100 kg.
        val result = detector.detect("now", listOf(set("old", 100.0, 5), set("now", 90.0, 12)))

        assertEquals(
            setOf(RecordType.ESTIMATED_ONE_REP_MAX, RecordType.BEST_SET_VOLUME),
            types(result["bench"]),
        )
    }

    @Test
    fun bestOfSeveralSetsInSessionCounts() {
        val result = detector.detect(
            "now",
            listOf(set("old", 100.0, 5), set("now", 60.0, 5), set("now", 105.0, 3), set("now", 70.0, 5)),
        )

        val weight = result.getValue("bench").first { it.type == RecordType.BEST_WEIGHT }
        assertEquals(105.0, weight.valueKg, 1e-9)
    }

    @Test
    fun warmupSets_doNotCount() {
        // Current warm-up is heavier than anything before, but warm-ups never set records.
        val current = detector.detect(
            "now",
            listOf(set("old", 100.0, 5), set("now", 140.0, 3, type = SetType.WARMUP)),
        )
        assertTrue(current.isEmpty())

        // An earlier heavy warm-up does not raise the bar.
        val earlier = detector.detect(
            "now",
            listOf(set("old", 150.0, 5, type = SetType.WARMUP), set("old", 80.0, 5), set("now", 90.0, 5)),
        )
        assertEquals(80.0, earlier.getValue("bench").first { it.type == RecordType.BEST_WEIGHT }.previousKg, 1e-9)
    }

    @Test
    fun onlyWarmupsBefore_meansNoBaseline() {
        val result = detector.detect(
            "now",
            listOf(set("old", 50.0, 10, type = SetType.WARMUP), set("now", 100.0, 5)),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun deletedSets_areIgnoredOnBothSides() {
        // A deleted heavy set in the current session is no record.
        val currentDeleted = detector.detect(
            "now",
            listOf(set("old", 100.0, 5), set("now", 200.0, 5, deletedAt = 1L)),
        )
        assertTrue(currentDeleted.isEmpty())

        // A deleted heavy set in history does not raise the bar.
        val historyDeleted = detector.detect(
            "now",
            listOf(set("old", 200.0, 5, deletedAt = 1L), set("old", 100.0, 5), set("now", 110.0, 5)),
        )
        assertEquals(
            100.0,
            historyDeleted.getValue("bench").first { it.type == RecordType.BEST_WEIGHT }.previousKg,
            1e-9,
        )
    }

    @Test
    fun onlyDeletedHistory_meansNoBaseline() {
        val result = detector.detect("now", listOf(set("old", 100.0, 5, deletedAt = 1L), set("now", 120.0, 5)))

        assertTrue(result.isEmpty())
    }

    @Test
    fun openAndEmptySets_areIgnored() {
        val result = detector.detect(
            "now",
            listOf(
                set("old", 100.0, 5),
                set("now", 150.0, 5, completed = false),
                set("now", 150.0, null),
                set("now", 150.0, 0),
            ),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun bodyweightExercise_usesEffectiveLoadForOneRmAndVolume() {
        // Pull-ups: bodyweight 80 kg, factor 1.0. Before: 8 reps, now: 10 reps, no extra weight.
        val result = detector.detect(
            "now",
            listOf(
                set("old", null, 8, exercise = "pullup", bodyweight = 80.0, factor = 1.0),
                set("now", null, 10, exercise = "pullup", bodyweight = 80.0, factor = 1.0),
            ),
        )

        val records = result.getValue("pullup")
        // No additional load, so no best-weight record.
        assertEquals(setOf(RecordType.ESTIMATED_ONE_REP_MAX, RecordType.BEST_SET_VOLUME), types(records))
        assertEquals(800.0, records.first { it.type == RecordType.BEST_SET_VOLUME }.valueKg, 1e-9)
        assertEquals(640.0, records.first { it.type == RecordType.BEST_SET_VOLUME }.previousKg, 1e-9)
    }

    @Test
    fun bodyweightExercise_additionalLoadIsWeightRecordWithZeroBefore() {
        val result = detector.detect(
            "now",
            listOf(
                set("old", 0.0, 8, exercise = "pullup", bodyweight = 80.0, factor = 1.0),
                set("now", 5.0, 8, exercise = "pullup", bodyweight = 80.0, factor = 1.0),
            ),
        )

        val weight = result.getValue("pullup").first { it.type == RecordType.BEST_WEIGHT }
        assertEquals(5.0, weight.valueKg, 1e-9)
        assertEquals(0.0, weight.previousKg, 1e-9)
    }

    @Test
    fun bodyweightExercise_lighterBodyweightLowersVolume() {
        val result = detector.detect(
            "now",
            listOf(
                set("old", null, 10, exercise = "pullup", bodyweight = 90.0, factor = 1.0),
                set("now", null, 10, exercise = "pullup", bodyweight = 80.0, factor = 1.0),
            ),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun repsOnlyExerciseWithoutLoad_neverHasRecords() {
        // Plain reps exercise: no bodyweight factor, no weight.
        val result = detector.detect("now", listOf(set("old", null, 10, exercise = "situp"), set("now", null, 20, exercise = "situp")))

        assertTrue(result.isEmpty())
    }

    @Test
    fun singleRep_usesWeightAsOneRm() {
        val result = detector.detect("now", listOf(set("old", 100.0, 1), set("now", 105.0, 1)))

        val oneRm = result.getValue("bench").first { it.type == RecordType.ESTIMATED_ONE_REP_MAX }
        assertEquals(105.0, oneRm.valueKg, 1e-9)
        assertEquals(100.0, oneRm.previousKg, 1e-9)
    }

    @Test
    fun exercisesAreComparedIndependently() {
        val result = detector.detect(
            "now",
            listOf(
                set("old", 100.0, 5, exercise = "bench"),
                set("now", 110.0, 5, exercise = "bench"),
                set("old", 150.0, 5, exercise = "squat"),
                set("now", 100.0, 5, exercise = "squat"),
                set("now", 60.0, 5, exercise = "row"),
            ),
        )

        assertEquals(setOf("bench"), result.keys)
    }

    @Test
    fun laterSessionsCountAsOtherSessions() {
        // History holds every other completed session, regardless of order.
        val result = detector.detect("now", listOf(set("now", 100.0, 5), set("a", 90.0, 5), set("b", 95.0, 5)))

        assertEquals(95.0, result.getValue("bench").first { it.type == RecordType.BEST_WEIGHT }.previousKg, 1e-9)
    }

    @Test
    fun deletedExerciseInHistory_isIgnoredViaDeletedAt() {
        // The repository maps a soft-deleted session exercise to deletedAt on each of its sets.
        val result = detector.detect(
            "now",
            listOf(set("old", 100.0, 5), set("old2", 300.0, 5, deletedAt = 5L), set("now", 110.0, 5)),
        )

        assertEquals(100.0, result.getValue("bench").first { it.type == RecordType.BEST_WEIGHT }.previousKg, 1e-9)
    }
}
