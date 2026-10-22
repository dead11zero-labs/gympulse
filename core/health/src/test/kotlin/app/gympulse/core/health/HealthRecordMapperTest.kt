package app.gympulse.core.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import app.gympulse.core.data.health.HealthExerciseType
import app.gympulse.core.data.health.HealthReadType
import app.gympulse.core.data.health.HealthSegmentType
import app.gympulse.core.data.health.HealthWorkout
import app.gympulse.core.data.health.HealthWorkoutSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class HealthRecordMapperTest {

    private val workout = HealthWorkout(
        clientRecordId = "session-1",
        clientRecordVersion = 42L,
        title = "Leg Day",
        notes = "New squat PR",
        startTime = Instant.parse("2026-07-01T16:00:00Z"),
        startZoneOffset = ZoneOffset.ofHours(2),
        endTime = Instant.parse("2026-07-01T17:00:00Z"),
        endZoneOffset = ZoneOffset.ofHours(2),
        exerciseType = HealthExerciseType.WEIGHTLIFTING,
    )

    @Test
    fun workoutMapsToExerciseSessionRecord() {
        val record = HealthRecordMapper.toExerciseSessionRecord(workout)

        assertEquals(workout.startTime, record.startTime)
        assertEquals(workout.endTime, record.endTime)
        assertEquals(workout.startZoneOffset, record.startZoneOffset)
        assertEquals(workout.endZoneOffset, record.endZoneOffset)
        assertEquals("Leg Day", record.title)
        assertEquals("New squat PR", record.notes)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING, record.exerciseType)
        assertEquals("session-1", record.metadata.clientRecordId)
        assertEquals(42L, record.metadata.clientRecordVersion)
    }

    private fun minutes(value: Long): Instant = workout.startTime.plus(Duration.ofMinutes(value))

    private fun segment(
        from: Long,
        to: Long,
        type: HealthSegmentType,
        reps: Int = 0,
        weightKg: Double? = null,
        setIndex: Int? = null,
        rpe: Double? = null,
    ) = HealthWorkoutSegment(minutes(from), minutes(to), type, reps, weightKg, setIndex, rpe)

    @Test
    fun segmentsCarryWeightSetIndexAndRpePerSet() {
        val record = HealthRecordMapper.toExerciseSessionRecord(
            workout.copy(
                exerciseType = HealthExerciseType.STRENGTH_TRAINING,
                segments = listOf(
                    segment(0, 5, HealthSegmentType.BENCH_PRESS, reps = 8, weightKg = 60.0, setIndex = 0, rpe = 7.5),
                    segment(5, 10, HealthSegmentType.BENCH_PRESS, reps = 6, weightKg = 62.5, setIndex = 1),
                    segment(10, 15, HealthSegmentType.PULL_UP, reps = 10, setIndex = 0),
                ),
            ),
        )

        assertEquals(3, record.segments.size)
        assertEquals(listOf(8, 6, 10), record.segments.map { it.repetitions })
        assertEquals(listOf(0, 1, 0), record.segments.map { it.setIndex })
        assertEquals(60.0, record.segments[0].weight!!.inKilograms, 1e-9)
        assertEquals(62.5, record.segments[1].weight!!.inKilograms, 1e-9)
        assertNull(record.segments[2].weight)
        assertEquals(listOf(7.5f, null, null), record.segments.map { it.rateOfPerceivedExertion })
    }

    @Test
    fun invalidSetDetailsAreLeftOutInsteadOfDroppingTheSegment() {
        val record = HealthRecordMapper.toExerciseSessionRecord(
            workout.copy(
                exerciseType = HealthExerciseType.STRENGTH_TRAINING,
                segments = listOf(
                    segment(0, 5, HealthSegmentType.SQUAT, reps = 5, weightKg = 0.0, setIndex = -1, rpe = 12.0),
                    segment(5, 10, HealthSegmentType.SQUAT, reps = 5, weightKg = -20.0, setIndex = 1, rpe = -0.5),
                ),
            ),
        )

        assertEquals(2, record.segments.size)
        record.segments.forEach { assertNull(it.weight) }
        record.segments.forEach { assertNull(it.rateOfPerceivedExertion) }
        assertEquals(listOf(null, 1), record.segments.map { it.setIndex })
    }

    @Test
    fun segmentsAreWrittenWithTypeAndRepetitions() {
        val record = HealthRecordMapper.toExerciseSessionRecord(
            workout.copy(
                exerciseType = HealthExerciseType.STRENGTH_TRAINING,
                segments = listOf(
                    segment(0, 20, HealthSegmentType.BENCH_PRESS, reps = 24),
                    segment(20, 40, HealthSegmentType.LAT_PULL_DOWN, reps = 30),
                    segment(40, 60, HealthSegmentType.LEG_PRESS, reps = 36),
                ),
            ),
        )

        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, record.exerciseType)
        assertEquals(
            listOf(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS,
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_LAT_PULL_DOWN,
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_PRESS,
            ),
            record.segments.map { it.segmentType },
        )
        assertEquals(listOf(24, 30, 36), record.segments.map { it.repetitions })
        assertEquals(minutes(20), record.segments[1].startTime)
        assertEquals(minutes(40), record.segments[1].endTime)
    }

    @Test
    fun everySegmentTypeHasADistinctConstant() {
        val constants = HealthSegmentType.entries.map(HealthRecordMapper::segmentType)
        assertEquals(HealthSegmentType.entries.size, constants.toSet().size)
    }

    @Test
    fun everySegmentTypeIsAcceptedInEveryExportedSessionType() {
        // Prüft den Nachbau der Kompatibilitätsregel gegen die Validierung der Bibliothek (connect-client
        // 1.2.0-alpha06, auf der JVM ohne Plattform-Validierung): Ein Abschnitt
        // geht nie verloren, unpassende Typen werden zu OTHER_WORKOUT.
        HealthExerciseType.entries.forEach { sessionType ->
            HealthSegmentType.entries.forEach { segmentType ->
                val record = HealthRecordMapper.toExerciseSessionRecord(
                    workout.copy(exerciseType = sessionType, segments = listOf(segment(0, 30, segmentType))),
                )
                val written = record.segments.singleOrNull()
                assertNotNull("$sessionType/$segmentType", written)
                val expected = HealthRecordMapper.segmentType(segmentType)
                    .takeIf { HealthRecordMapper.isCompatible(it, HealthRecordMapper.exerciseType(sessionType)) }
                    ?: ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT
                assertEquals("$sessionType/$segmentType", expected, written!!.segmentType)
            }
        }
    }

    @Test
    fun strengthSegmentsAreCompatibleWithStrengthSessionsOnly() {
        val strength = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        assertTrue(HealthRecordMapper.isCompatible(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT, strength))
        assertTrue(HealthRecordMapper.isCompatible(ExerciseSegment.EXERCISE_SEGMENT_TYPE_UNKNOWN, strength))
        assertFalse(HealthRecordMapper.isCompatible(ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING_TREADMILL, strength))
        assertFalse(
            HealthRecordMapper.isCompatible(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT,
                ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING,
            ),
        )
        assertTrue(
            HealthRecordMapper.isCompatible(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING_TREADMILL,
                ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
            ),
        )
        // Seit 1.2.0-alpha06 unverändert: Gewichtheben und Calisthenics teilen die Kraft-Abschnitte,
        // Dehnen lässt nur die universellen Abschnitte zu.
        listOf(ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING, ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS)
            .forEach { assertTrue(HealthRecordMapper.isCompatible(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS, it)) }
        assertTrue(
            HealthRecordMapper.isCompatible(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_STRETCHING,
                ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING,
            ),
        )
        assertFalse(
            HealthRecordMapper.isCompatible(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_YOGA,
                ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            ),
        )
    }

    @Test
    fun cardioSegmentInStrengthSessionBecomesOtherWorkout() {
        val record = HealthRecordMapper.toExerciseSessionRecord(
            workout.copy(
                exerciseType = HealthExerciseType.STRENGTH_TRAINING,
                segments = listOf(
                    segment(0, 10, HealthSegmentType.RUNNING_TREADMILL),
                    segment(10, 60, HealthSegmentType.SQUAT, reps = 25),
                ),
            ),
        )
        assertEquals(
            listOf(ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT, ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT),
            record.segments.map { it.segmentType },
        )

        val cardio = HealthRecordMapper.toExerciseSessionRecord(
            workout.copy(
                exerciseType = HealthExerciseType.OTHER_WORKOUT,
                segments = listOf(segment(0, 60, HealthSegmentType.RUNNING_TREADMILL)),
            ),
        )
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING_TREADMILL, cardio.segments.single().segmentType)
    }

    @Test
    fun invalidSegmentsAreDroppedInsteadOfFailingTheExport() {
        val record = HealthRecordMapper.toExerciseSessionRecord(
            workout.copy(
                exerciseType = HealthExerciseType.STRENGTH_TRAINING,
                segments = listOf(
                    segment(0, 20, HealthSegmentType.BENCH_PRESS),
                    // überlappt den vorigen
                    segment(10, 30, HealthSegmentType.SQUAT),
                    // leeres Zeitfenster
                    segment(30, 30, HealthSegmentType.DEADLIFT),
                    // endet nach der Session
                    segment(50, 70, HealthSegmentType.LEG_PRESS),
                    segment(-5, -1, HealthSegmentType.PLANK),
                    segment(30, 45, HealthSegmentType.PULL_UP, reps = -2),
                ),
            ),
        )

        assertEquals(
            listOf(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS, ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP),
            record.segments.map { it.segmentType },
        )
        assertEquals(0, record.segments.last().repetitions)
        assertEquals(workout.title, record.title)
        assertEquals(workout.notes, record.notes)
    }

    @Test
    fun recordWithoutSegmentsKeepsEverythingElse() {
        val full = workout.copy(segments = listOf(segment(0, 60, HealthSegmentType.SQUAT)))
        val record = HealthRecordMapper.toExerciseSessionRecordWithoutSegments(full)
        assertTrue(record.segments.isEmpty())
        assertEquals(full.title, record.title)
        assertEquals(full.clientRecordId, record.metadata.clientRecordId)
    }

    @Test
    fun everyExerciseTypeHasADistinctRecordConstant() {
        val constants = HealthExerciseType.entries.map(HealthRecordMapper::exerciseType)
        assertEquals(HealthExerciseType.entries.size, constants.toSet().size)
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            HealthRecordMapper.exerciseType(HealthExerciseType.STRENGTH_TRAINING),
        )
    }

    @Test
    fun everyReadTypeHasADistinctRecordClassAndPermission() {
        val classes = HealthReadType.entries.map(HealthRecordMapper::recordType)
        assertEquals(HealthReadType.entries.size, classes.toSet().size)
        assertEquals(HeartRateRecord::class, HealthRecordMapper.recordType(HealthReadType.HEART_RATE))

        val permissions = HealthReadType.entries.map(HealthConnectPermissions::read)
        assertEquals(HealthReadType.entries.size, permissions.toSet().size)
        assertEquals(
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthConnectPermissions.read(HealthReadType.SLEEP),
        )
        // Gewicht gehört zu den Kernberechtigungen.
        assertTrue(HealthConnectPermissions.read(HealthReadType.WEIGHT) in HealthConnectPermissions.REQUIRED)
    }

    @Test
    fun sleepSessionKeepsTimes() {
        val record = SleepSessionRecord(
            startTime = Instant.parse("2026-06-30T22:00:00Z"),
            startZoneOffset = ZoneOffset.ofHours(2),
            endTime = Instant.parse("2026-07-01T06:00:00Z"),
            endZoneOffset = ZoneOffset.ofHours(2),
            metadata = Metadata.manualEntry(),
        )

        val sleep = HealthRecordMapper.toHealthSleep(record)

        assertEquals(record.startTime, sleep.start)
        assertEquals(record.endTime, sleep.end)
    }
}
