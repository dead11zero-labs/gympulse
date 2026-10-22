package app.gympulse.core.data.health

import app.gympulse.core.model.BodyMeasurement
import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MeasurementSource
import app.gympulse.core.model.MeasurementType
import app.gympulse.core.model.MuscleGroup
import app.gympulse.core.model.SessionExercise
import app.gympulse.core.model.SessionStatus
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.SetType
import app.gympulse.core.model.TrackingType
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
import java.util.Locale

class WorkoutHealthMapperTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val start = Instant.parse("2026-07-01T16:00:00Z")
    private val end = Instant.parse("2026-07-01T17:05:00Z")

    private fun set(completed: Boolean, completedAt: Instant = end) = SetEntry(
        id = "set-${if (completed) "done" else "open"}",
        setIndex = 0,
        setType = SetType.WORKING,
        weightKg = 60.0,
        reps = 8,
        durationSeconds = null,
        distanceMeters = null,
        rpe = null,
        isCompleted = completed,
        completedAt = if (completed) completedAt else null,
    )

    private fun exercise(
        exerciseId: String,
        completed: Boolean = true,
        sortOrder: Int = 0,
        completedAt: Instant = end,
    ) = SessionExercise(
        id = "se-$exerciseId",
        exerciseId = exerciseId,
        sortOrder = sortOrder,
        supersetGroup = null,
        notes = null,
        equipmentSettingsSnapshot = emptyMap(),
        sets = listOf(set(completed, completedAt)),
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

    private fun catalogEntry(
        id: String,
        category: ExerciseCategory,
        slug: String? = null,
        name: String = id,
        equipment: EquipmentType = EquipmentType.BARBELL,
        muscles: List<MuscleGroup> = emptyList(),
    ) = Exercise(
        id = id,
        slug = slug,
        name = name,
        category = category,
        equipmentType = equipment,
        trackingType = TrackingType.WEIGHT_REPS,
        bodyweightFactor = null,
        primaryMuscles = muscles,
        secondaryMuscles = emptyList(),
        instructions = emptyList(),
        imageUrls = emptyList(),
        notes = null,
        isCustom = slug == null,
        isFavorite = false,
        defaultRestSeconds = null,
        equipmentId = null,
    )

    private val categories = listOf(
        catalogEntry("bench", ExerciseCategory.STRENGTH, "barbell_bench_press_medium_grip", "Bankdrücken"),
        catalogEntry("squat", ExerciseCategory.POWERLIFTING, "barbell_squat", "Kniebeuge"),
        catalogEntry("snatch", ExerciseCategory.OLYMPIC_WEIGHTLIFTING, "snatch", "Reißen"),
        catalogEntry("stretch", ExerciseCategory.STRETCHING, "chest_stretch", "Brustdehnung"),
        catalogEntry("jump", ExerciseCategory.PLYOMETRICS, "front_box_jump", "Box Jump"),
        catalogEntry("run", ExerciseCategory.CARDIO, "running_treadmill", "Laufband"),
    ).associateBy { it.id }

    @Test
    fun completedSessionMapsTimesTitleNotesAndId() {
        val workout = WorkoutHealthMapper.toHealthWorkout(session(), categories, berlin)

        assertNotNull(workout)
        workout!!
        assertEquals("0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b", workout.clientRecordId)
        assertEquals(end.toEpochMilli(), workout.clientRecordVersion)
        assertEquals(start, workout.startTime)
        assertEquals(end, workout.endTime)
        assertEquals("GymPulse: Push Day", workout.title)
        assertEquals("Felt strong\n\n• Bankdrücken: 1 × 8 × 60 kg", workout.notes)
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
    fun blankTitleFallsBackToAppNameAndBlankNotesKeepOnlyTheExerciseList() {
        val workout = WorkoutHealthMapper.toHealthWorkout(session(title = "  ", notes = ""), categories, berlin)!!
        assertEquals("GymPulse", workout.title)
        assertEquals("• Bankdrücken: 1 × 8 × 60 kg", workout.notes)
    }

    @Test
    fun withoutCompletedSetsThereAreNoNotesOrSegments() {
        val workout = WorkoutHealthMapper.toHealthWorkout(
            session(notes = null, exercises = listOf(exercise("bench", completed = false))),
            categories,
            berlin,
        )!!
        assertNull(workout.notes)
        assertTrue(workout.segments.isEmpty())
    }

    @Test
    fun everyTrainedExerciseBecomesASegmentInSessionOrder() {
        val workout = WorkoutHealthMapper.toHealthWorkout(
            session(
                exercises = listOf(
                    exercise("squat", completed = true, sortOrder = 1, completedAt = start.plusSeconds(1800)),
                    exercise("bench", completed = true, sortOrder = 0, completedAt = start.plusSeconds(900)),
                    exercise("custom-unknown"),
                ),
            ),
            categories,
            berlin,
            Locale.GERMAN,
        )!!

        assertEquals(
            listOf(HealthSegmentType.BENCH_PRESS, HealthSegmentType.SQUAT),
            workout.segments.map { it.type },
        )
        assertEquals(start, workout.segments.first().startTime)
        assertEquals(start.plusSeconds(1800), workout.segments.last().endTime)
        assertEquals(listOf(8, 8), workout.segments.map { it.repetitions })
        assertEquals(
            "Felt strong\n\n• Bankdrücken: 1 × 8 × 60 kg\n• Kniebeuge: 1 × 8 × 60 kg",
            workout.notes,
        )
    }

    @Test
    fun titleHelper() {
        assertEquals("GymPulse: Leg Day", WorkoutHealthMapper.title(" Leg Day "))
        assertEquals("GymPulse", WorkoutHealthMapper.title(null))
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
