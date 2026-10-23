package app.gympulse.feature.dashboard

import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MuscleGroup
import app.gympulse.core.model.SessionExercise
import app.gympulse.core.model.SessionStatus
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.SetType
import app.gympulse.core.model.TrackingType
import app.gympulse.core.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class WeekAnalysisTest {
    private val zone = ZoneId.of("UTC")

    // 2026-10-19 ist ein Montag.
    private val monday = LocalDate.of(2026, 10, 19)

    private val bench = exercise("bench", "Bench press", primary = listOf(MuscleGroup.CHEST), secondary = listOf(MuscleGroup.TRICEPS))
    private val squat = exercise("squat", "Squat", primary = listOf(MuscleGroup.QUADRICEPS), secondary = listOf(MuscleGroup.GLUTES))
    private val run = exercise("run", "Run", category = ExerciseCategory.CARDIO, tracking = TrackingType.DISTANCE)
    private val exercises = listOf(bench, squat, run).associateBy { it.id }

    private fun exercise(
        id: String,
        name: String,
        category: ExerciseCategory = ExerciseCategory.STRENGTH,
        tracking: TrackingType = TrackingType.WEIGHT_REPS,
        primary: List<MuscleGroup> = emptyList(),
        secondary: List<MuscleGroup> = emptyList(),
    ) = Exercise(
        id = id,
        slug = id,
        name = name,
        category = category,
        equipmentType = EquipmentType.BARBELL,
        trackingType = tracking,
        bodyweightFactor = null,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        instructions = emptyList(),
        imageUrls = emptyList(),
        notes = null,
        isCustom = false,
        isFavorite = false,
        defaultRestSeconds = null,
        equipmentId = null,
    )

    private fun set(
        weight: Double? = null,
        reps: Int? = null,
        type: SetType = SetType.WORKING,
        done: Boolean = true,
        seconds: Int? = null,
        meters: Double? = null,
    ) = SetEntry(
        id = "s-${counter++}",
        setIndex = 0,
        setType = type,
        weightKg = weight,
        reps = reps,
        durationSeconds = seconds,
        distanceMeters = meters,
        rpe = null,
        isCompleted = done,
        completedAt = null,
    )

    private var counter = 0

    private fun session(
        day: LocalDate,
        vararg items: Pair<Exercise, List<SetEntry>>,
        minutes: Long = 60,
    ): WorkoutSession {
        val end = day.atTime(18, 0).atZone(zone).toInstant()
        return WorkoutSession(
            id = "w-${counter++}",
            planId = null,
            title = null,
            startedAt = end.minusSeconds(minutes * 60),
            endedAt = end,
            status = SessionStatus.COMPLETED,
            bodyweightKg = 80.0,
            rating = null,
            notes = null,
            totalVolumeKg = null,
            exercises = items.mapIndexed { index, (exercise, sets) ->
                SessionExercise(
                    id = "se-${counter++}",
                    exerciseId = exercise.id,
                    sortOrder = index,
                    supersetGroup = null,
                    notes = null,
                    equipmentSettingsSnapshot = emptyMap(),
                    sets = sets,
                )
            },
        )
    }

    @Test
    fun volumeCountsOnlyCompletedWorkingSets() {
        val week = buildWeekStats(
            monday,
            listOf(
                session(
                    monday,
                    bench to listOf(
                        set(100.0, 5),
                        set(60.0, 10, type = SetType.WARMUP),
                        set(100.0, 5, done = false),
                    ),
                ),
            ),
            exercises,
            zone,
        )
        assertEquals(500.0, week.volumeKg, 0.001)
        assertEquals(1, week.strengthSets)
        assertEquals(500.0, week.dailyKg[0], 0.001)
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 0), week.dailySessions)
    }

    @Test
    fun cardioIsCountedByTimeAndDistanceNotVolume() {
        val week = buildWeekStats(
            monday,
            listOf(
                session(
                    monday.plusDays(2),
                    run to listOf(set(seconds = 1800, meters = 5000.0), set(seconds = 600, done = false)),
                    minutes = 60,
                ),
            ),
            exercises,
            zone,
        )
        assertEquals(0.0, week.volumeKg, 0.001)
        assertEquals(0, week.strengthSets)
        assertEquals(30L, week.cardioMinutes)
        assertEquals(5000.0, week.cardioDistanceMeters, 0.001)
        assertEquals(0.5f, week.cardioShare, 0.001f)
    }

    @Test
    fun secondaryMusclesCountHalf() {
        val week = buildWeekStats(
            monday,
            listOf(session(monday, bench to listOf(set(80.0, 8), set(80.0, 8)))),
            exercises,
            zone,
        )
        assertEquals(2.0, week.muscleSets.getValue(MuscleGroup.CHEST), 0.001)
        assertEquals(1.0, week.muscleSets.getValue(MuscleGroup.TRICEPS), 0.001)
        val shares = week.regionShares()
        assertEquals(1f, shares.getValue(BodyRegion.UPPER), 0.001f)
        assertEquals(0f, shares.getValue(BodyRegion.LEGS), 0.001f)
    }

    @Test
    fun topExercisesAreSortedByVolume() {
        val week = buildWeekStats(
            monday,
            listOf(session(monday, bench to listOf(set(50.0, 10)), squat to listOf(set(100.0, 10)))),
            exercises,
            zone,
        )
        assertEquals(listOf("Squat", "Bench press"), week.topExercises.map { it.name })
        assertEquals(1000.0, week.topExercises.first().volumeKg, 0.001)
    }

    @Test
    fun emptyWeekHasNoSharesAndNoCardioShare() {
        val week = buildWeekStats(monday, emptyList(), exercises, zone)
        assertTrue(week.isEmpty)
        assertTrue(week.regionShares().isEmpty())
        assertEquals(0f, week.cardioShare, 0.001f)
        assertEquals(0.0, week.averageVolumeKg, 0.001)
    }

    @Test
    fun weeksAreGroupedAndOrderedOldestFirst() {
        val sessions = listOf(
            session(monday, bench to listOf(set(100.0, 5))),
            session(monday.minusDays(1), bench to listOf(set(50.0, 10))), // Sonntag der Vorwoche
        )
        val weeks = buildWeeks(sessions, exercises, monday, DayOfWeek.MONDAY, zone)
        assertEquals(WEEKS_LOADED, weeks.size)
        assertEquals(monday, weeks.last().weekStart)
        assertEquals(500.0, weeks.last().volumeKg, 0.001)
        assertEquals(500.0, weeks[WEEKS_LOADED - 2].volumeKg, 0.001)
        assertEquals(6, weeks[WEEKS_LOADED - 2].dailySessions.indexOfFirst { it > 0 })
        assertTrue(weeks.first().isEmpty)
    }

    @Test
    fun highlightsFindStrongestDayAndSkippedMuscle() {
        val earlier = (1..3).map { back ->
            val start = monday.minusWeeks(back.toLong())
            buildWeekStats(
                start,
                listOf(session(start, squat to listOf(set(100.0, 5), set(100.0, 5), set(100.0, 5)))),
                exercises,
                zone,
            )
        }
        val selected = buildWeekStats(
            monday,
            listOf(
                session(monday, bench to listOf(set(60.0, 5))),
                session(monday.plusDays(2), bench to listOf(set(100.0, 5))),
            ),
            exercises,
            zone,
        )
        val highlights = weekHighlights(selected, earlier.reversed())
        assertEquals(DayOfWeek.WEDNESDAY, highlights.strongestDay)
        assertEquals(MuscleGroup.CHEST, highlights.topMuscle)
        assertEquals(MuscleGroup.QUADRICEPS, highlights.neglectedMuscle)
    }

    @Test
    fun highlightsAreEmptyForEmptyWeek() {
        val empty = buildWeekStats(monday, emptyList(), exercises, zone)
        assertTrue(weekHighlights(empty, emptyList()).isEmpty)
        assertNull(weekHighlights(empty, emptyList()).neglectedMuscle)
    }
}
