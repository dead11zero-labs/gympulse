package app.gympulse.core.workout

import android.content.Context
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
import app.gympulse.core.model.SetType
import app.gympulse.core.model.TrackingType
import app.gympulse.core.model.WorkoutPlan
import app.gympulse.core.workout.timer.RestTimer
import app.gympulse.core.workout.timer.RestTimerSettings
import app.gympulse.core.workout.timer.RestTimerState
import app.gympulse.core.workout.timer.RestTimerStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

/** Write path of the running workout without any screen, against an in-memory Room database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActiveWorkoutControllerTest {
    private lateinit var db: GymPulseDatabase
    private val time = FakeTime(Instant.parse("2026-10-19T10:00:00Z"))
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var planRepository: PlanRepository
    private lateinit var scope: CoroutineScope
    private val timerStore = InMemoryRestTimerStore()
    private lateinit var restTimer: RestTimer
    private lateinit var controller: ActiveWorkoutController
    private val exported = CompletableDeferred<String>()
    private var healthMetrics: WorkoutHealthMetrics? = null

    @Before
    fun setUp() {
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
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        restTimer = RestTimer(time, timerStore, scope)
        controller = ActiveWorkoutController(
            workoutRepository = workoutRepository,
            exerciseRepository = ExerciseRepository(db.exerciseDao(), time),
            restTimer = restTimer,
            restTimerSettings = object : RestTimerSettings {
                override val restSeconds: Flow<Int> = flowOf(DEFAULT_REST_SECONDS)
            },
            healthExporter = { exported.complete(it) },
            healthReader = { _, _ -> healthMetrics },
            time = time,
            scope = scope,
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test
    fun startWorkout_createsSessionOnceAndResumes() = runTest {
        seed()

        val first = controller.startWorkout(PLAN_ID)
        val second = controller.startWorkout(null)

        assertEquals(first, second)
        val session = controller.activeSession.first()!!
        assertEquals("Push", session.title)
        assertEquals(3, session.exercises.single().sets.size)
    }

    @Test
    fun toggleSetCompleted_usesSettingsRestAndPersistsTimer() = runTest {
        seed()
        controller.startWorkout(PLAN_ID)
        val exercise = controller.activeSession.first()!!.exercises.single()
        val set = exercise.sets.first()

        controller.updateSet(exercise.id, set.id) { it.copy(weightKg = 80.0) }
        controller.toggleSetCompleted(exercise.id, set.id)

        val stored = controller.activeSession.first()!!.exercises.single().sets.first()
        assertTrue(stored.isCompleted)
        assertEquals(80.0, stored.weightKg!!, 0.001)
        assertEquals(DEFAULT_REST_SECONDS, controller.restTimerState.value!!.remainingSeconds(time.now()))
        assertEquals(controller.restTimerState.value, timerStore.stored)

        // Reopening a set does not touch the timer.
        controller.skipRest()
        controller.toggleSetCompleted(exercise.id, set.id)
        assertNull(controller.restTimerState.value)
        assertTrue(!controller.activeSession.first()!!.exercises.single().sets.first().isCompleted)
    }

    @Test
    fun toggleSetCompleted_prefersExerciseRest() = runTest {
        seed()
        controller.startWorkout(null)
        controller.addExercise("pullup")
        val exercise = controller.activeSession.first()!!.exercises.single()

        controller.toggleSetCompleted(exercise.id, exercise.sets.single().id)

        assertEquals(PULLUP_REST_SECONDS, controller.restTimerState.value!!.totalSeconds)
    }

    @Test
    fun editSet_findsSetByIdChecksSessionAndRule() = runTest {
        seed()
        val sessionId = controller.startWorkout(PLAN_ID)
        val set = controller.activeSession.first()!!.exercises.single().sets.first()

        time.advanceSeconds(5)
        val changed = controller.editSet(sessionId, set.id) { it.copy(weightKg = 82.5) }
        assertEquals(SetEditStatus.CHANGED, changed.status)
        assertEquals(82.5, changed.set!!.weightKg!!, 0.001)
        assertEquals(time.now(), changed.set.updatedAt)

        assertEquals(SetEditStatus.UNCHANGED, controller.editSet(sessionId, set.id) { it }.status)
        assertEquals(SetEditStatus.REJECTED, controller.editSet(sessionId, set.id) { null }.status)
        assertEquals(SetEditStatus.SET_NOT_FOUND, controller.editSet(sessionId, "missing") { it }.status)
        assertEquals(SetEditStatus.OTHER_SESSION, controller.editSet("other", set.id) { it }.status)
        controller.discardWorkout()
        assertEquals(SetEditStatus.NO_SESSION, controller.editSet(sessionId, set.id) { it }.status)
    }

    @Test
    fun setSetCompleted_isAbsoluteAndCountsRestFromCompletion() = runTest {
        seed()
        val sessionId = controller.startWorkout(PLAN_ID)
        val set = controller.activeSession.first()!!.exercises.single().sets.first()
        val completedAt = time.now().minusSeconds(30)

        val result = controller.setSetCompleted(sessionId, set.id, completed = true, completedAt = completedAt)

        assertEquals(SetEditStatus.CHANGED, result.status)
        assertEquals(completedAt, result.set!!.completedAt)
        assertEquals(DEFAULT_REST_SECONDS - 30, controller.restTimerState.value!!.remainingSeconds(time.now()))
        assertEquals(
            SetEditStatus.UNCHANGED,
            controller.setSetCompleted(sessionId, set.id, completed = true).status,
        )
        assertEquals(
            SetEditStatus.REJECTED,
            controller.setSetCompleted(sessionId, set.id, completed = false) { false }.status,
        )
        assertTrue(controller.activeSession.first()!!.exercises.single().sets.first().isCompleted)
    }

    @Test
    fun adjustAndSkipRest() = runTest {
        seed()
        controller.startWorkout(PLAN_ID)
        val exercise = controller.activeSession.first()!!.exercises.single()
        controller.toggleSetCompleted(exercise.id, exercise.sets.first().id)

        controller.adjustRest(RestTimer.ADJUST_STEP_SECONDS)
        assertEquals(DEFAULT_REST_SECONDS + 15, controller.restTimerState.value!!.totalSeconds)

        controller.skipRest()
        assertNull(controller.restTimerState.value)
        assertNull(timerStore.stored)
    }

    @Test
    fun concurrentWrites_areAllApplied() = runTest {
        seed()
        controller.startWorkout(PLAN_ID)
        val exercise = controller.activeSession.first()!!.exercises.single()

        (1..5).map { async { controller.addSet(exercise.id) } }.awaitAll()

        assertEquals(8, controller.activeSession.first()!!.exercises.single().sets.size)
    }

    @Test
    fun warmupNotesAndExerciseEditing() = runTest {
        seed()
        controller.startWorkout(PLAN_ID)
        val exercise = controller.activeSession.first()!!.exercises.single()

        controller.toggleWarmup(exercise.id, exercise.sets.first().id)
        controller.updateNotes(exercise.id, "Slow eccentric")
        controller.deleteSet(exercise.sets.last().id)
        val edited = controller.activeSession.first()!!.exercises.single()
        assertEquals(SetType.WARMUP, edited.sets.first().setType)
        assertEquals("Slow eccentric", edited.notes)
        assertEquals(2, edited.sets.size)

        controller.removeExercise(exercise.id)
        assertTrue(controller.activeSession.first()!!.exercises.isEmpty())
    }

    @Test
    fun finishWorkout_buildsSummaryClearsTimerAndExports() = runTest {
        healthMetrics = WorkoutHealthMetrics(averageHeartRateBpm = 120, maxHeartRateBpm = 160, steps = 900)
        seed()
        val sessionId = controller.startWorkout(PLAN_ID)
        val exercise = controller.activeSession.first()!!.exercises.single()
        controller.updateSet(exercise.id, exercise.sets.first().id) { it.copy(weightKg = 100.0) }
        controller.toggleSetCompleted(exercise.id, exercise.sets.first().id)
        time.advanceSeconds(1200)

        val summary = controller.finishWorkout()!!

        assertEquals(1200, summary.durationSeconds)
        assertEquals(1, summary.completedSets)
        assertEquals(1000.0, summary.volumeKg, 0.001)
        assertNull(controller.restTimerState.value)
        assertNull(timerStore.stored)
        assertNull(controller.activeSession.first())
        assertEquals(SessionStatus.COMPLETED, db.sessionDao().getById(sessionId)!!.session.status)
        assertEquals(sessionId, exported.await())
        // Health values arrive in the published summary, not in the returned snapshot.
        assertEquals(120L, controller.summary.first { it?.health != null }!!.health?.averageHeartRateBpm)

        controller.dismissSummary()
        assertNull(controller.summary.value)
    }

    @Test
    fun proposeAndFinishAll_withoutScreen() = runTest {
        seed()
        val sessionId = controller.startWorkout(PLAN_ID)
        time.advanceSeconds(60)

        // 3 sets: 3 x 45 s work + 2 x 120 s rest.
        assertEquals(375L, controller.proposeFinishAll()!!.durationSeconds)

        val end = time.now()
        val summary = controller.finishAll(durationSeconds = 420)!!

        assertEquals(420, summary.durationSeconds)
        assertEquals(3, summary.completedSets)
        val stored = db.sessionDao().getById(sessionId)!!
        assertEquals(SessionStatus.COMPLETED, stored.session.status)
        assertEquals(end.minusSeconds(420).toEpochMilli(), stored.session.startedAt)
        assertTrue(stored.exercises.single().sets.all { it.isCompleted })
        assertNull(controller.proposeFinishAll())
    }

    @Test
    fun discardWorkout_marksDiscardedAndClearsTimer() = runTest {
        seed()
        val sessionId = controller.startWorkout(PLAN_ID)
        val exercise = controller.activeSession.first()!!.exercises.single()
        controller.toggleSetCompleted(exercise.id, exercise.sets.first().id)

        controller.discardWorkout()

        assertNull(controller.restTimerState.value)
        assertEquals(SessionStatus.DISCARDED, db.sessionDao().getById(sessionId)!!.session.status)
        assertNotEquals(sessionId, controller.startWorkout(null))
    }

    @Test
    fun withoutActiveSession_writesAreIgnored() = runTest {
        seed()

        assertNull(controller.finishWorkout())
        controller.toggleSetCompleted("missing", "missing")
        controller.addSet("missing")
        controller.discardWorkout()

        assertNull(controller.activeSession.first())
        assertNull(controller.restTimerState.value)
        assertNull(controller.summary.value)
        assertTrue(!exported.isCompleted)
    }

    private suspend fun seed() {
        db.exerciseDao().upsertExercises(
            listOf(
                exercise("bench", TrackingType.WEIGHT_REPS, factor = null, rest = null),
                exercise("pullup", TrackingType.REPS, factor = 1.0, rest = PULLUP_REST_SECONDS),
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

    private fun exercise(id: String, trackingType: TrackingType, factor: Double?, rest: Int?) = ExerciseEntity(
        id = id, slug = id, category = "strength", equipmentType = "barbell",
        trackingType = trackingType, bodyweightFactor = factor, imageUrls = null, notes = null,
        isCustom = false, isFavorite = false, defaultRestSeconds = rest, equipmentId = null,
        source = null, license = null, createdAt = 0, updatedAt = 0,
    )

    private class InMemoryRestTimerStore : RestTimerStore {
        var stored: RestTimerState? = null
        override suspend fun read(): RestTimerState? = stored
        override suspend fun write(state: RestTimerState?) {
            stored = state
        }
    }

    private class FakeTime(var instant: Instant) : TimeProvider {
        override fun now(): Instant = instant
        override fun zone(): ZoneId = ZoneOffset.UTC
        fun advanceSeconds(seconds: Long) {
            instant = instant.plusSeconds(seconds)
        }
    }

    private companion object {
        const val PLAN_ID = "plan-push"
        const val DEFAULT_REST_SECONDS = 120
        const val PULLUP_REST_SECONDS = 45
    }
}
