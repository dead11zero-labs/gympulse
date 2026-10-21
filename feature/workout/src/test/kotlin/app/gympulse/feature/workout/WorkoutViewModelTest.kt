package app.gympulse.feature.workout

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gympulse.core.data.health.WorkoutHealthMetrics
import app.gympulse.core.data.repository.ExerciseRepository
import app.gympulse.core.data.repository.PlanRepository
import app.gympulse.core.data.repository.WorkoutRepository
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.database.GymPulseDatabase
import app.gympulse.core.database.entity.ExerciseEntity
import app.gympulse.core.model.PlanExercise
import app.gympulse.core.model.SessionStatus
import app.gympulse.core.model.TrackingType
import app.gympulse.core.model.WorkoutPlan
import app.gympulse.feature.workout.timer.RestTimer
import app.gympulse.feature.workout.timer.RestTimerSettings
import app.gympulse.feature.workout.timer.Ticker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
import java.time.ZoneOffset

/** State transitions of the active workout against a real in-memory Room database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkoutViewModelTest {
    private lateinit var db: GymPulseDatabase
    private val time = FakeTime(Instant.parse("2026-10-19T10:00:00Z"))
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var planRepository: PlanRepository
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var restTimer: RestTimer
    private val exportedSession = CompletableDeferred<String>()
    private var healthMetrics: WorkoutHealthMetrics? = null
    private val healthWindows = mutableListOf<Pair<Instant, Instant>>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GymPulseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workoutRepository = WorkoutRepository(
            sessionDao = db.sessionDao(),
            planDao = db.planDao(),
            exerciseDao = db.exerciseDao(),
            equipmentDao = db.equipmentDao(),
            bodyDao = db.bodyDao(),
            time = time,
        )
        planRepository = PlanRepository(db.planDao(), time)
        exerciseRepository = ExerciseRepository(db.exerciseDao(), time)
        restTimer = RestTimer(time)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun withoutActiveSession_showsPlanSelection() = runTest {
        seedPlan()
        val viewModel = viewModel()

        val state = viewModel.uiState.first { it is WorkoutUiState.SelectPlan } as WorkoutUiState.SelectPlan

        assertEquals(listOf("Push"), state.plans.map { it.name })
        assertEquals(1, state.plans.single().exerciseCount)
    }

    @Test
    fun startFromPlan_createsActiveSessionWithTargetSets() = runTest {
        seedPlan()
        val viewModel = viewModel()

        viewModel.startWorkout(PLAN_ID)
        val session = viewModel.awaitActive()

        assertEquals("Push", session.title)
        val exercise = session.exercises.single()
        assertEquals("bench", exercise.exerciseId)
        assertEquals(3, exercise.sets.size)
        assertEquals(10, exercise.sets.first().reps)
        assertEquals(3, session.openSets)
    }

    @Test
    fun startWhileActive_resumesSameSession() = runTest {
        seedPlan()
        val viewModel = viewModel()

        viewModel.startWorkout(PLAN_ID)
        val first = viewModel.awaitActive()
        viewModel.startWorkout(null)
        val second = viewModel.awaitActive()

        assertEquals(first.sessionId, second.sessionId)
        assertEquals(first.sessionId, workoutRepository.startOrResumeSession(null))
    }

    @Test
    fun newViewModel_resumesSessionFromDatabase() = runTest {
        seedPlan()
        val sessionId = workoutRepository.startSession(PLAN_ID)

        // Like after process death: a fresh ViewModel only knows the database.
        val session = viewModel().awaitActive()

        assertEquals(sessionId, session.sessionId)
    }

    @Test
    fun routeWithPlanId_startsPlanOnce() = runTest {
        seedPlan()
        val handle = SavedStateHandle(mapOf(WorkoutViewModel.KEY_PLAN_ID to PLAN_ID))

        val session = viewModel(handle).awaitActive()
        workoutRepository.discardSession(session.sessionId)
        // Recreated with the same saved state (e.g. configuration change): no second start.
        val again = viewModel(handle)

        assertTrue(again.uiState.first { it !is WorkoutUiState.Loading } is WorkoutUiState.SelectPlan)
    }

    @Test
    fun completingSet_savesAndStartsRestTimer() = runTest {
        seedPlan()
        val viewModel = viewModel()
        viewModel.startWorkout(PLAN_ID)
        val session = viewModel.awaitActive()
        val exercise = session.exercises.single()
        val set = exercise.sets.first()

        viewModel.onWeightChange(exercise.sessionExerciseId, set.id, "62,5")
        viewModel.toggleSetCompleted(exercise.sessionExerciseId, set.id)
        val updated = viewModel.awaitActive { it.completedSets == 1 }

        val stored = updated.exercises.single().sets.first()
        assertEquals(62.5, stored.weightKg!!, 0.001)
        assertTrue(stored.isCompleted)
        assertEquals(62.5 * 10, updated.volumeKg, 0.001)
        assertEquals(REST_SECONDS, restTimer.state.value!!.remainingSeconds(time.now()))
    }

    @Test
    fun addAndDeleteSet_andWarmupToggle() = runTest {
        seedPlan()
        val viewModel = viewModel()
        viewModel.startWorkout(PLAN_ID)
        val exercise = viewModel.awaitActive().exercises.single()

        viewModel.addSet(exercise.sessionExerciseId)
        val withFour = viewModel.awaitActive { it.exercises.single().sets.size == 4 }
        assertEquals(10, withFour.exercises.single().sets.last().reps)

        viewModel.toggleWarmup(exercise.sessionExerciseId, exercise.sets.first().id)
        viewModel.deleteSet(withFour.exercises.single().sets.last().id)
        val result = viewModel.awaitActive { active ->
            active.exercises.single().sets.size == 3 &&
                active.exercises.single().sets.any { it.setType == app.gympulse.core.model.SetType.WARMUP }
        }
        assertEquals(3, result.exercises.single().sets.size)
    }

    @Test
    fun addAndRemoveExercise_withNotes() = runTest {
        seedPlan()
        val viewModel = viewModel()
        viewModel.startWorkout(null)
        assertTrue(viewModel.awaitActive().exercises.isEmpty())

        viewModel.addExercise("pullup")
        val added = viewModel.awaitActive { it.exercises.size == 1 }.exercises.single()
        assertEquals(TrackingType.REPS, added.trackingType)
        assertTrue(added.isBodyweight)
        assertEquals(1, added.sets.size)

        viewModel.onNotesChange(added.sessionExerciseId, "Grip wide")
        assertEquals("Grip wide", viewModel.awaitActive { it.exercises.single().notes != null }.exercises.single().notes)

        viewModel.removeExercise(added.sessionExerciseId)
        assertTrue(viewModel.awaitActive { it.exercises.isEmpty() }.exercises.isEmpty())
    }

    @Test
    fun finish_completesSessionAndShowsSummary() = runTest {
        seedPlan()
        val viewModel = viewModel()
        viewModel.startWorkout(PLAN_ID)
        val exercise = viewModel.awaitActive().exercises.single()
        viewModel.onWeightChange(exercise.sessionExerciseId, exercise.sets[0].id, "100")
        viewModel.toggleSetCompleted(exercise.sessionExerciseId, exercise.sets[0].id)
        viewModel.awaitActive { it.completedSets == 1 }
        time.advanceSeconds(1800)

        viewModel.finishWorkout()
        val summary = viewModel.summary.first { it != null }!!

        assertEquals(1800, summary.durationSeconds)
        assertEquals(1, summary.completedSets)
        assertEquals(1000.0, summary.volumeKg, 0.001)
        assertNull(restTimer.state.value)
        assertTrue(viewModel.uiState.first { it !is WorkoutUiState.Active } is WorkoutUiState.SelectPlan)
        val stored = db.sessionDao().observeRecentCompleted(1).first().single()
        assertEquals(SessionStatus.COMPLETED, stored.status)
        assertEquals(stored.id, exportedSession.await())
        assertNull(summary.health)

        viewModel.dismissSummary()
        assertNull(viewModel.summary.value)
    }

    @Test
    fun finish_addsHealthConnectValuesForTheSessionWindow() = runTest {
        healthMetrics = WorkoutHealthMetrics(averageHeartRateBpm = 128, maxHeartRateBpm = 165, steps = 2_340)
        seedPlan()
        val viewModel = viewModel()
        val startedAt = time.now()
        viewModel.startWorkout(PLAN_ID)
        val exercise = viewModel.awaitActive().exercises.single()
        viewModel.onWeightChange(exercise.sessionExerciseId, exercise.sets[0].id, "100")
        viewModel.toggleSetCompleted(exercise.sessionExerciseId, exercise.sets[0].id)
        viewModel.awaitActive { it.completedSets == 1 }
        time.advanceSeconds(1800)

        viewModel.finishWorkout()
        val summary = viewModel.summary.first { it?.health != null }!!

        assertEquals(128L, summary.health?.averageHeartRateBpm)
        assertEquals(2_340L, summary.health?.steps)
        assertEquals(1, summary.completedSets)
        assertEquals(listOf(startedAt to startedAt.plusSeconds(1800)), healthWindows)
    }

    @Test
    fun finish_summaryListsExercisesAndDetectsRecordsAgainstEarlierSessions() = runTest {
        seedPlan()
        val viewModel = viewModel()
        // First session: nothing to compare with.
        val first = finishOneSet(viewModel, weight = "80")
        assertEquals(0, first.recordCount)
        viewModel.dismissSummary()
        time.advanceSeconds(3600)

        val second = finishOneSet(viewModel, weight = "100")

        val exercise = second.exercises.single()
        assertEquals("bench", exercise.exerciseId)
        assertEquals(1, exercise.sets.size)
        assertEquals(1, exercise.sets.single().number)
        assertEquals(100.0, exercise.sets.single().weightKg!!, 0.001)
        val weightRecord = exercise.records.first { it.type == RecordType.BEST_WEIGHT }
        assertEquals(100.0, weightRecord.valueKg, 0.001)
        assertEquals(80.0, weightRecord.previousKg, 0.001)
        assertEquals(3, second.recordCount)
    }

    @Test
    fun setHistory_includesDeletedSetsWithDeletedAt() = runTest {
        seedPlan()
        val viewModel = viewModel()
        finishOneSet(viewModel, weight = "80")
        val sessionId = db.sessionDao().observeRecentCompleted(1).first().single().id
        val deletedSetId = db.sessionDao().getById(sessionId)!!.exercises.single().sets.last().id
        workoutRepository.deleteSet(deletedSetId)

        val history = workoutRepository.setHistory(listOf("bench"))

        assertEquals(3, history.size)
        assertEquals(listOf(deletedSetId), history.filter { it.deletedAt != null }.map { it.setId })
        assertTrue(history.all { it.sessionId == sessionId && it.exerciseId == "bench" })
        assertTrue(workoutRepository.setHistory(emptyList()).isEmpty())
    }

    private var lastFinishedSessionId: String? = null

    private suspend fun finishOneSet(viewModel: WorkoutViewModel, weight: String): WorkoutSummary {
        viewModel.startWorkout(PLAN_ID)
        // uiState may still hold the previous (finished) session until the new one arrives.
        val session = viewModel.awaitActive { it.sessionId != lastFinishedSessionId }
        lastFinishedSessionId = session.sessionId
        val exercise = session.exercises.single()
        viewModel.onWeightChange(exercise.sessionExerciseId, exercise.sets[0].id, weight)
        viewModel.toggleSetCompleted(exercise.sessionExerciseId, exercise.sets[0].id)
        viewModel.awaitActive { it.sessionId == session.sessionId && it.completedSets == 1 }
        time.advanceSeconds(600)
        viewModel.finishWorkout()
        return viewModel.summary.first { it != null }!!
    }

    @Test
    fun discard_marksSessionDiscarded() = runTest {
        seedPlan()
        val viewModel = viewModel()
        viewModel.startWorkout(PLAN_ID)
        val session = viewModel.awaitActive()

        viewModel.discardWorkout()

        assertTrue(viewModel.uiState.first { it !is WorkoutUiState.Active } is WorkoutUiState.SelectPlan)
        assertEquals(SessionStatus.DISCARDED, db.sessionDao().getById(session.sessionId)!!.session.status)
        assertNotNull(workoutRepository.startOrResumeSession(null))
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = WorkoutViewModel(
        savedStateHandle = handle,
        workoutRepository = workoutRepository,
        planRepository = planRepository,
        exerciseRepository = exerciseRepository,
        restTimer = restTimer,
        restTimerSettings = object : RestTimerSettings {
            override val restSeconds: Flow<Int> = flowOf(REST_SECONDS)
        },
        ticker = object : Ticker {
            override fun ticks(): Flow<Instant> = flowOf(time.now())
        },
        time = time,
        healthExporter = { exportedSession.complete(it) },
        healthReader = { start, end ->
            healthWindows += start to end
            healthMetrics
        },
    )

    private suspend fun WorkoutViewModel.awaitActive(
        predicate: (ActiveSessionUi) -> Boolean = { true },
    ): ActiveSessionUi = uiState
        .first { it is WorkoutUiState.Active && predicate(it.session) }
        .let { (it as WorkoutUiState.Active).session }

    private suspend fun seedPlan() {
        db.exerciseDao().upsertExercises(
            listOf(
                exercise("bench", TrackingType.WEIGHT_REPS, factor = null),
                exercise("pullup", TrackingType.REPS, factor = 1.0),
            ),
        )
        planRepository.save(
            WorkoutPlan(
                id = PLAN_ID,
                name = "Push",
                description = null,
                color = null,
                isArchived = false,
                exercises = listOf(
                    PlanExercise(
                        id = "pe1",
                        exerciseId = "bench",
                        sortOrder = 0,
                        supersetGroup = null,
                        targetSets = 3,
                        targetRepsMin = 8,
                        targetRepsMax = 10,
                        targetWeightKg = null,
                        restSeconds = null,
                    ),
                ),
            ),
        )
    }

    private fun exercise(id: String, trackingType: TrackingType, factor: Double?) = ExerciseEntity(
        id = id, slug = id, category = "strength", equipmentType = "barbell",
        trackingType = trackingType, bodyweightFactor = factor, imageUrls = null, notes = null,
        isCustom = false, isFavorite = false, defaultRestSeconds = null, equipmentId = null,
        source = null, license = null, createdAt = 0, updatedAt = 0,
    )

    private class FakeTime(var instant: Instant) : TimeProvider {
        override fun now(): Instant = instant
        override fun zone(): ZoneId = ZoneOffset.UTC
        fun advanceSeconds(seconds: Long) {
            instant = instant.plusSeconds(seconds)
        }
    }

    private companion object {
        const val PLAN_ID = "plan-push"
        const val REST_SECONDS = 120
    }
}
