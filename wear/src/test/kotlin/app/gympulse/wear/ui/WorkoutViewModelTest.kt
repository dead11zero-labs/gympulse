package app.gympulse.wear.ui

import app.gympulse.core.wearprotocol.CompleteSet
import app.gympulse.core.wearprotocol.ExerciseSnapshot
import app.gympulse.core.wearprotocol.Finish
import app.gympulse.core.wearprotocol.PlanSummaryList
import app.gympulse.core.wearprotocol.PreviousSet
import app.gympulse.core.wearprotocol.SessionSnapshot
import app.gympulse.core.wearprotocol.SetSnapshot
import app.gympulse.core.wearprotocol.StartWorkout
import app.gympulse.core.wearprotocol.UpdateSet
import app.gympulse.core.wearprotocol.WearCommand
import app.gympulse.core.wearprotocol.WorkoutSnapshot
import app.gympulse.wear.data.WatchWorkoutRepository
import app.gympulse.wear.data.WearChannel
import app.gympulse.wear.data.WearConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val SESSION = "11111111-1111-7111-8111-111111111111"
private const val EX_A = "aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa"
private const val EX_B = "bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb"
private const val A1 = "a1a1a1a1-a1a1-7a1a-8a1a-a1a1a1a1a1a1"
private const val A2 = "a2a2a2a2-a2a2-7a2a-8a2a-a2a2a2a2a2a2"
private const val B1 = "b1b1b1b1-b1b1-7b1b-8b1b-b1b1b1b1b1b1"

private class FakeChannel : WearChannel {
    val snapshotFlow = MutableSharedFlow<WorkoutSnapshot>(replay = 1)
    val planFlow = MutableSharedFlow<PlanSummaryList>(replay = 1)
    val connectionFlow = MutableStateFlow(WearConnection.CONNECTED)
    val sent = mutableListOf<WearCommand>()

    override val snapshots: Flow<WorkoutSnapshot> = snapshotFlow
    override val plans: Flow<PlanSummaryList> = planFlow
    override val connection: Flow<WearConnection> = connectionFlow
    override suspend fun sendCommand(command: WearCommand) {
        sent += command
    }
}

private fun set(id: String, number: Int, weight: Double? = null, reps: Int? = null, done: Boolean = false, previous: PreviousSet? = null) =
    SetSnapshot(setId = id, number = number, weightKg = weight, reps = reps, isCompleted = done, updatedAt = 1_000, previous = previous)

private fun snapshot(version: Long, a: List<SetSnapshot>, b: List<SetSnapshot>) = WorkoutSnapshot(
    version = version,
    session = SessionSnapshot(
        sessionId = SESSION,
        title = "Push",
        startedAt = 1_000,
        exercises = listOf(
            ExerciseSnapshot(sessionExerciseId = EX_A, exerciseId = "e1", name = "Bench Press", sets = a),
            ExerciseSnapshot(sessionExerciseId = EX_B, exerciseId = "e2", name = "Row", sets = b),
        ),
    ),
)

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTest {
    private val channel = FakeChannel()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(a: List<SetSnapshot>, b: List<SetSnapshot>): WorkoutViewModel {
        val repository = WatchWorkoutRepository(channel, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        channel.snapshotFlow.tryEmit(snapshot(1, a, b))
        return WorkoutViewModel(repository)
    }

    @Test
    fun `selects first exercise with open sets and shows previous values`() = runTest {
        val vm = viewModel(
            a = listOf(set(A1, 1, 80.0, 8, done = true)),
            b = listOf(set(B1, 1, previous = PreviousSet(weightKg = 60.0, reps = 10))),
        )
        val state = vm.uiState.value
        assertEquals("Row", state.exercise?.name)
        assertEquals(60.0, state.displayWeightKg, 0.0)
        assertEquals(10, state.displayReps)
    }

    @Test
    fun `crown steps are batched into one UpdateSet`() = runTest {
        val vm = viewModel(listOf(set(A1, 1, 80.0, 8), set(A2, 2)), listOf(set(B1, 1)))
        vm.rotate(1)
        vm.rotate(1)
        assertEquals(85.0, vm.uiState.value.displayWeightKg, 0.0)
        assertTrue(channel.sent.isEmpty())

        advanceTimeBy(WorkoutViewModel.FLUSH_DELAY_MS + 1)
        runCurrent()

        val update = channel.sent.single() as UpdateSet
        assertEquals(A1, update.setId)
        assertEquals(85.0, update.weightKg!!, 0.0)
        assertNull(update.reps)
        assertNull(vm.uiState.value.draft)
    }

    @Test
    fun `crown edits repetitions when reps field is selected and clamps at zero`() = runTest {
        val vm = viewModel(listOf(set(A1, 1, 80.0, 1)), listOf(set(B1, 1)))
        vm.selectField(EditField.REPS)
        vm.rotate(-5)
        assertEquals(0, vm.uiState.value.displayReps)
    }

    @Test
    fun `completing last set of exercise sends command and moves to next exercise`() = runTest {
        val vm = viewModel(listOf(set(A1, 1, 80.0, 8)), listOf(set(B1, 1, 50.0, 12)))
        vm.completeSet()
        runCurrent()

        assertTrue(channel.sent.last() is CompleteSet)
        assertEquals("Row", vm.uiState.value.exercise?.name)
    }

    @Test
    fun `completing a prefilled set saves the shown values first`() = runTest {
        val vm = viewModel(listOf(set(A1, 1, previous = PreviousSet(weightKg = 70.0, reps = 6))), listOf(set(B1, 1)))
        vm.completeSet()
        runCurrent()

        val update = channel.sent.first() as UpdateSet
        assertEquals(70.0, update.weightKg!!, 0.0)
        assertEquals(6, update.reps)
        assertTrue(channel.sent[1] is CompleteSet)
    }

    @Test
    fun `exercise switching moves between exercises`() = runTest {
        val vm = viewModel(listOf(set(A1, 1)), listOf(set(B1, 1)))
        assertEquals("Bench Press", vm.uiState.value.exercise?.name)
        vm.nextExercise()
        runCurrent()
        assertEquals("Row", vm.uiState.value.exercise?.name)
        vm.previousExercise()
        runCurrent()
        assertEquals("Bench Press", vm.uiState.value.exercise?.name)
    }

    @Test
    fun `finish sends Finish and ends the session view`() = runTest {
        val vm = viewModel(listOf(set(A1, 1)), listOf(set(B1, 1)))
        vm.finish()
        runCurrent()
        assertTrue(channel.sent.last() is Finish)
        assertNull(vm.uiState.value.session)
    }

    @Test
    fun `plans view model starts a workout once while start is pending`() = runTest {
        val repository = WatchWorkoutRepository(channel, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val plans = PlansViewModel(repository)
        plans.startWorkout("plan-1")
        plans.startWorkout("plan-1")
        runCurrent()
        assertEquals(1, channel.sent.count { it is StartWorkout })
        assertTrue(plans.uiState.value.startPending)
    }
}
