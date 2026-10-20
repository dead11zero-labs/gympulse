package app.gympulse.core.data.health

import app.gympulse.core.model.BodyMeasurement
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MeasurementSource
import app.gympulse.core.model.MeasurementType
import app.gympulse.core.model.SessionExercise
import app.gympulse.core.model.SessionStatus
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.SetType
import app.gympulse.core.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class WorkoutHealthMapperTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val start = Instant.parse("2026-07-01T16:00:00Z")
    private val end = Instant.parse("2026-07-01T17:05:00Z")

    private fun set(completed: Boolean) = SetEntry(
        id = "set-${if (completed) "done" else "open"}",
        setIndex = 0,
        setType = SetType.WORKING,
        weightKg = 60.0,
        reps = 8,
        durationSeconds = null,
        distanceMeters = null,
        rpe = null,
        isCompleted = completed,
        completedAt = if (completed) end else null,
    )

    private fun exercise(exerciseId: String, completed: Boolean = true) = SessionExercise(
        id = "se-$exerciseId",
        exerciseId = exerciseId,
        sortOrder = 0,
        supersetGroup = null,
        notes = null,
        equipmentSettingsSnapshot = emptyMap(),
        sets = listOf(set(completed)),
    )

    private fun session(
        status: SessionStatus = SessionStatus.COMPLETED,
        endedAt: Instant? = end,
        title: String? = "Push Day",
        notes: String? = "Felt strong",
        exercises: List<SessionExercise> = listOf(exercise("bench")),
    ) = WorkoutSession(
        id = "0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b",
        planId = null,
        title = title,
        startedAt = start,
        endedAt = endedAt,
        status = status,
        bodyweightKg = 80.0,
        rating = null,
        notes = notes,
        totalVolumeKg = 480.0,
        exercises = exercises,
    )

    private val categories = mapOf(
        "bench" to ExerciseCategory.STRENGTH,
        "squat" to ExerciseCategory.POWERLIFTING,
        "snatch" to ExerciseCategory.OLYMPIC_WEIGHTLIFTING,
        "stretch" to ExerciseCategory.STRETCHING,
        "jump" to ExerciseCategory.PLYOMETRICS,
        "run" to ExerciseCategory.CARDIO,
    )

    @Test
    fun completedSessionMapsTimesTitleNotesAndId() {
        val workout = WorkoutHealthMapper.toHealthWorkout(session(), categories, berlin)

        assertNotNull(workout)
        workout!!
        assertEquals("0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b", workout.clientRecordId)
        assertEquals(end.toEpochMilli(), workout.clientRecordVersion)
        assertEquals(start, workout.startTime)
        assertEquals(end, workout.endTime)
        assertEquals("Push Day", workout.title)
        assertEquals("Felt strong", workout.notes)
        assertEquals(HealthExerciseType.STRENGTH_TRAINING, workout.exerciseType)
    }

    @Test
    fun zoneOffsetsFollowTheZoneAtEachInstant() {
        val workout = WorkoutHealthMapper.toHealthWorkout(session(), categories, berlin)!!
        // Juli: Sommerzeit in Berlin.
        assertEquals(ZoneOffset.ofHours(2), workout.startZoneOffset)
        assertEquals(ZoneOffset.ofHours(2), workout.endZoneOffset)
    }

    @Test
    fun blankTitleAndNotesBecomeNull() {
        val workout = WorkoutHealthMapper.toHealthWorkout(session(title = "  ", notes = ""), categories, berlin)!!
        assertNull(workout.title)
        assertNull(workout.notes)
    }

    @Test
    fun activeOrDiscardedSessionsAreNotExported() {
        assertNull(WorkoutHealthMapper.toHealthWorkout(session(status = SessionStatus.ACTIVE), categories, berlin))
        assertNull(WorkoutHealthMapper.toHealthWorkout(session(status = SessionStatus.DISCARDED), categories, berlin))
    }

    @Test
    fun sessionWithoutValidTimeRangeIsNotExported() {
        assertNull(WorkoutHealthMapper.toHealthWorkout(session(endedAt = null), categories, berlin))
        assertNull(WorkoutHealthMapper.toHealthWorkout(session(endedAt = start), categories, berlin))
        assertNull(WorkoutHealthMapper.toHealthWorkout(session(endedAt = start.minusSeconds(60)), categories, berlin))
    }

    @Test
    fun onlyExercisesWithCompletedSetsDecideTheType() {
        val workout = WorkoutHealthMapper.toHealthWorkout(
            session(exercises = listOf(exercise("bench", completed = false), exercise("stretch"))),
            categories,
            berlin,
        )!!
        assertEquals(HealthExerciseType.STRETCHING, workout.exerciseType)
    }

    @Test
    fun sessionWithoutCompletedSetsFallsBackToAllExercises() {
        val workout = WorkoutHealthMapper.toHealthWorkout(
            session(exercises = listOf(exercise("squat", completed = false))),
            categories,
            berlin,
        )!!
        assertEquals(HealthExerciseType.WEIGHTLIFTING, workout.exerciseType)
    }

    @Test
    fun exerciseTypeRules() {
        assertEquals(HealthExerciseType.STRENGTH_TRAINING, WorkoutHealthMapper.exerciseType(emptyList()))
        assertEquals(
            HealthExerciseType.STRETCHING,
            WorkoutHealthMapper.exerciseType(listOf(ExerciseCategory.STRETCHING)),
        )
        assertEquals(
            HealthExerciseType.WEIGHTLIFTING,
            WorkoutHealthMapper.exerciseType(
                listOf(ExerciseCategory.POWERLIFTING, ExerciseCategory.OLYMPIC_WEIGHTLIFTING, ExerciseCategory.STRETCHING),
            ),
        )
        assertEquals(
            HealthExerciseType.CALISTHENICS,
            WorkoutHealthMapper.exerciseType(listOf(ExerciseCategory.PLYOMETRICS)),
        )
        assertEquals(
            HealthExerciseType.OTHER_WORKOUT,
            WorkoutHealthMapper.exerciseType(listOf(ExerciseCategory.CARDIO, ExerciseCategory.CARDIO)),
        )
        assertEquals(
            HealthExerciseType.STRENGTH_TRAINING,
            WorkoutHealthMapper.exerciseType(listOf(ExerciseCategory.STRENGTH, ExerciseCategory.CARDIO)),
        )
    }

    @Test
    fun unknownExercisesAreIgnored() {
        val workout = WorkoutHealthMapper.toHealthWorkout(
            session(exercises = listOf(exercise("custom-unknown"), exercise("run"))),
            categories,
            berlin,
        )!!
        assertEquals(HealthExerciseType.OTHER_WORKOUT, workout.exerciseType)
    }

    @Test
    fun weightIsNewUnlessSameHealthConnectMeasurementExists() {
        val at = Instant.parse("2026-07-02T06:30:00Z")
        val weight = HealthWeight(measuredAt = at, weightKg = 81.4)
        val manualSameTime = BodyMeasurement("m1", at, MeasurementType.WEIGHT, 81.4, MeasurementSource.MANUAL)
        val importedSameTime = BodyMeasurement("m2", at, MeasurementType.WEIGHT, 81.4, MeasurementSource.HEALTH_CONNECT)
        val importedOther = importedSameTime.copy(id = "m3", measuredAt = at.minusSeconds(86_400))

        assertTrue(WorkoutHealthMapper.isNewWeight(weight, emptyList()))
        assertTrue(WorkoutHealthMapper.isNewWeight(weight, listOf(manualSameTime, importedOther)))
        assertFalse(WorkoutHealthMapper.isNewWeight(weight, listOf(importedSameTime)))
    }
}
