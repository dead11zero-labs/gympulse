package app.gympulse.core.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import app.gympulse.core.data.health.HealthExerciseType
import app.gympulse.core.data.health.HealthWorkout
import org.junit.Assert.assertEquals
import org.junit.Test
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

    @Test
    fun everyExerciseTypeHasADistinctRecordConstant() {
        val constants = HealthExerciseType.entries.map(HealthRecordMapper::exerciseType)
        assertEquals(HealthExerciseType.entries.size, constants.toSet().size)
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            HealthRecordMapper.exerciseType(HealthExerciseType.STRENGTH_TRAINING),
        )
    }
}
