package app.gympulse.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.database.GymPulseDatabase
import app.gympulse.core.database.entity.ExerciseEntity
import app.gympulse.core.database.entity.ExerciseMuscleEntity
import app.gympulse.core.database.entity.ExerciseTranslationEntity
import app.gympulse.core.database.entity.PlanExerciseEntity
import app.gympulse.core.database.entity.SessionExerciseEntity
import app.gympulse.core.database.entity.SetEntryEntity
import app.gympulse.core.database.entity.WorkoutPlanEntity
import app.gympulse.core.database.entity.WorkoutSessionEntity
import app.gympulse.core.insights.InsightCode
import app.gympulse.core.insights.ProgressionAction
import app.gympulse.core.insights.TrendStatus
import app.gympulse.core.model.SessionStatus
import app.gympulse.core.model.SetType
import app.gympulse.core.model.TrackingType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Echte DAOs auf einer In-Memory-Room-Datenbank: Abfragen, Mapper und Engine zusammen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InsightsRepositoryTest {
    private lateinit var db: GymPulseDatabase
    private lateinit var repository: InsightsRepository

    // Donnerstag, 22.10.2026
    private val now = Instant.parse("2026-10-22T10:00:00Z")
    private val time = object : TimeProvider {
        override fun now(): Instant = now
        override fun zone(): ZoneId = ZoneId.of("UTC")
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GymPulseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = InsightsRepository(db.sessionDao(), db.exerciseDao(), db.planDao(), time)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun emptyDatabaseYieldsEmptyInsights() = runTest {
        val report = repository.observeReport("en").first()

        assertTrue(report.insights.exercises.isEmpty())
        assertTrue(report.insights.findings.isEmpty())
        assertTrue(report.exerciseNames.isEmpty())
    }

    @Test
    fun flatBenchPressWithPlanYieldsPlateauAndProgressionSuggestion() = runTest {
        seedBench()
        seedPlan(repsMin = 8, repsMax = 12, sets = 3)
        // Sechs wöchentliche Einheiten mit identischem Gewicht und identischen Wiederholungen.
        for (week in 5 downTo 0) {
            insertSession("s$week", daysAgo = week * 7L, planId = "plan", reps = 12)
        }

        val report = repository.observeReport("en").first()

        val bench = report.insights.exercise("bench")
        assertNotNull(bench)
        assertEquals(TrendStatus.PLATEAU, bench!!.trend.status)
        assertEquals("Bench Press", report.exerciseNames["bench"])
        val codes = report.insights.findingsFor("bench").map { it.code }
        assertTrue(codes.contains(InsightCode.PLATEAU))
        // Plan: 8-12 Wiederholungen, alle Sätze bei 12 -> Gewicht steigern (oder Deload nach Plateau-Serie).
        assertTrue(
            bench.progression!!.action == ProgressionAction.INCREASE_WEIGHT ||
                bench.progression!!.action == ProgressionAction.DELOAD,
        )
    }

    @Test
    fun namesFollowTheRequestedLocaleWithEnglishFallback() = runTest {
        seedBench()

        assertEquals("Bankdrücken", repository.observeReport("de").first().exerciseNames["bench"])
        assertEquals("Bench Press", repository.observeReport("fr").first().exerciseNames["bench"])
    }

    @Test
    fun activeAndOldSessionsAreIgnored() = runTest {
        seedBench()
        insertSession("active", daysAgo = 1, status = SessionStatus.ACTIVE)
        // Älter als der Auswertungszeitraum von 26 Wochen.
        insertSession("old", daysAgo = InsightsRepository.LOOKBACK_WEEKS * 7 + 10)

        val report = repository.observeReport("en").first()

        assertNull(report.insights.exercise("bench"))
    }

    @Test
    fun newCompletedSessionTriggersNewResult() = runTest {
        seedBench()
        insertSession("s1", daysAgo = 7)
        assertEquals(TrendStatus.INSUFFICIENT_DATA, repository.observeReport("en").first().insights.exercise("bench")!!.trend.status)

        for (week in 6 downTo 2) insertSession("n$week", daysAgo = week * 7L)
        val report = repository.observeReport("en").first()

        assertEquals(6, report.insights.exercise("bench")!!.trend.sampleSize)
    }

    private suspend fun seedBench() {
        db.exerciseDao().upsertFull(
            listOf(
                ExerciseEntity(
                    id = "bench", slug = "bench", category = "strength", equipmentType = "barbell",
                    trackingType = TrackingType.WEIGHT_REPS, bodyweightFactor = null, imageUrls = null, notes = null,
                    isCustom = false, isFavorite = false, defaultRestSeconds = null, equipmentId = null,
                    source = null, license = null, createdAt = 0, updatedAt = 0,
                ),
            ),
            listOf(
                ExerciseTranslationEntity("t-en", "bench", "en", "Bench Press", null, 0, 0),
                ExerciseTranslationEntity("t-de", "bench", "de", "Bankdrücken", null, 0, 0),
            ),
            listOf(ExerciseMuscleEntity("m1", "bench", "chest", isPrimary = true, createdAt = 0, updatedAt = 0)),
        )
    }

    private suspend fun seedPlan(repsMin: Int, repsMax: Int, sets: Int) {
        db.planDao().savePlan(
            WorkoutPlanEntity("plan", "Push", null, null, isArchived = false, createdAt = 0, updatedAt = 0),
            listOf(
                PlanExerciseEntity(
                    id = "pe1", planId = "plan", exerciseId = "bench", sortOrder = 0, supersetGroup = null,
                    targetSets = sets, targetRepsMin = repsMin, targetRepsMax = repsMax, targetWeightKg = null,
                    restSeconds = null, createdAt = 0, updatedAt = 0,
                ),
            ),
            now = 0,
        )
    }

    private suspend fun insertSession(
        id: String,
        daysAgo: Long,
        status: SessionStatus = SessionStatus.COMPLETED,
        planId: String? = null,
        reps: Int = 10,
        weightKg: Double = 80.0,
    ) {
        val startedAt = now.minus(daysAgo, ChronoUnit.DAYS)
        val session = WorkoutSessionEntity(
            id = id, planId = planId, title = null, startedAt = startedAt.toEpochMilli(),
            endedAt = if (status == SessionStatus.COMPLETED) startedAt.plusSeconds(3_600).toEpochMilli() else null,
            status = status, bodyweightKg = null, rating = null, notes = null, totalVolumeKg = null,
            createdAt = 0, updatedAt = 0,
        )
        val sessionExercise = SessionExerciseEntity(
            id = "$id-bench", sessionId = id, exerciseId = "bench", sortOrder = 0, supersetGroup = null,
            notes = null, equipmentSettingsSnapshot = null, createdAt = 0, updatedAt = 0,
        )
        val sets = (0 until 3).map { index ->
            SetEntryEntity(
                id = "$id-set$index", sessionExerciseId = "$id-bench", setIndex = index, setType = SetType.WORKING,
                weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = null, rpe = null,
                isCompleted = true, completedAt = null, createdAt = 0, updatedAt = 0,
            )
        }
        db.sessionDao().insertSessionWithContent(session, listOf(sessionExercise), sets)
    }
}
