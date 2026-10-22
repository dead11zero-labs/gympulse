package app.gympulse.wear.data

import app.gympulse.core.wearprotocol.AckResult
import app.gympulse.core.wearprotocol.CommandAck
import app.gympulse.core.wearprotocol.CompleteSet
import app.gympulse.core.wearprotocol.ExerciseSnapshot
import app.gympulse.core.wearprotocol.PlanSummary
import app.gympulse.core.wearprotocol.PlanSummaryList
import app.gympulse.core.wearprotocol.SessionSnapshot
import app.gympulse.core.wearprotocol.SetSnapshot
import app.gympulse.core.wearprotocol.WearCommand
import app.gympulse.core.wearprotocol.WearCommands
import app.gympulse.core.wearprotocol.WorkoutSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SESSION = "11111111-1111-7111-8111-111111111111"
private const val SET_A = "22222222-2222-7222-8222-222222222222"

private class FakeWearChannel : WearChannel {
    val snapshotFlow = MutableSharedFlow<WorkoutSnapshot>(replay = 1)
    val planFlow = MutableSharedFlow<PlanSummaryList>(replay = 1)
    val connectionFlow = MutableStateFlow(WearConnection.CONNECTED)

    /** Alle erfolgreich abgelegten Kommandos in Reihenfolge, auch Wiederholungen derselben ID. */
    val sent = mutableListOf<WearCommand>()
    var failing = false

    override val snapshots: Flow<WorkoutSnapshot> = snapshotFlow
    override val plans: Flow<PlanSummaryList> = planFlow
    override val connection: Flow<WearConnection> = connectionFlow

    override suspend fun sendCommand(command: WearCommand) {
        if (failing) throw IllegalStateException("Data Layer nicht erreichbar")
        sent += command
    }
}

private fun snapshot(version: Long, acks: List<CommandAck> = emptyList(), completed: Boolean = false, session: Boolean = true) =
    WorkoutSnapshot(
        version = version,
        acks = acks,
        session = if (!session) null else SessionSnapshot(
            sessionId = SESSION,
            title = "Push",
            startedAt = 1_000,
            exercises = listOf(
                ExerciseSnapshot(
                    sessionExerciseId = "33333333-3333-7333-8333-333333333333",
                    exerciseId = "44444444-4444-7444-8444-444444444444",
                    name = "Bankdrücken",
                    restSeconds = 90,
                    sets = listOf(SetSnapshot(setId = SET_A, number = 1, weightKg = 60.0, reps = 8, isCompleted = completed, updatedAt = 500)),
                ),
            ),
        ),
    )

@OptIn(ExperimentalCoroutinesApi::class)
class WatchWorkoutRepositoryTest {

    private fun TestScope.repository(channel: FakeWearChannel) =
        WatchWorkoutRepository(channel, backgroundScope)

    private fun runRepoTest(block: suspend TestScope.(FakeWearChannel, WatchWorkoutRepository) -> Unit) =
        runTest(UnconfinedTestDispatcher()) {
            val channel = FakeWearChannel()
            block(channel, repository(channel))
        }

    private fun WatchWorkoutRepository.isSetCompleted() =
        state.value.session!!.exercises[0].sets[0].isCompleted

    @Test
    fun `snapshot from the phone becomes the visible session`() = runRepoTest { channel, repo ->
        channel.snapshotFlow.emit(snapshot(version = 1))

        assertEquals(SESSION, repo.state.value.session?.sessionId)
        assertEquals(1L, repo.state.value.version)
    }

    @Test
    fun `local command is applied optimistically and sent as outbox item`() = runRepoTest { channel, repo ->
        channel.snapshotFlow.emit(snapshot(version = 1))

        repo.completeSet(SET_A, now = 2_000)

        assertTrue(repo.isSetCompleted())
        assertEquals(1, repo.state.value.pending.size)
        assertEquals(1, channel.sent.size)
        assertTrue(channel.sent.single() is CompleteSet)
        assertTrue(repo.state.value.session!!.restEndsAt == 2_000L + 90_000L)
    }

    @Test
    fun `ack in a newer snapshot clears the pending command`() = runRepoTest { channel, repo ->
        channel.snapshotFlow.emit(snapshot(version = 1))
        repo.completeSet(SET_A, now = 2_000)
        val id = channel.sent.single().commandId

        channel.snapshotFlow.emit(snapshot(version = 2, acks = listOf(CommandAck(id)), completed = true))

        assertTrue(repo.state.value.pending.isEmpty())
        assertTrue(repo.isSetCompleted())
    }

    @Test
    fun `rejected command is reverted and reported as notice`() = runRepoTest { channel, repo ->
        channel.snapshotFlow.emit(snapshot(version = 1))
        repo.completeSet(SET_A, now = 2_000)
        val id = channel.sent.single().commandId

        channel.snapshotFlow.emit(snapshot(version = 2, acks = listOf(CommandAck(id, AckResult.SESSION_ENDED))))

        assertFalse(repo.isSetCompleted())
        assertEquals(listOf(AckResult.SESSION_ENDED), repo.state.value.notices.map { it.result })
        repo.dismissNotices()
        assertTrue(repo.state.value.notices.isEmpty())
    }

    @Test
    fun `stale snapshot is ignored`() = runRepoTest { channel, repo ->
        channel.snapshotFlow.emit(snapshot(version = 5, completed = true))
        channel.snapshotFlow.emit(snapshot(version = 4, completed = false))

        assertEquals(5L, repo.state.value.version)
        assertTrue(repo.isSetCompleted())
    }

    @Test
    fun `sending the same command twice is applied and delivered once`() = runRepoTest { channel, repo ->
        channel.snapshotFlow.emit(snapshot(version = 1))
        val command = WearCommands.completeSet(SESSION, SET_A, now = 2_000)

        repo.send(command)
        repo.send(command)

        assertEquals(1, repo.state.value.pending.size)
        assertEquals(1, channel.sent.size)
    }

    @Test
    fun `already acknowledged command is not applied or sent again`() = runRepoTest { channel, repo ->
        channel.snapshotFlow.emit(snapshot(version = 1))
        val command = WearCommands.completeSet(SESSION, SET_A, now = 2_000)
        repo.send(command)
        channel.snapshotFlow.emit(snapshot(version = 2, acks = listOf(CommandAck(command.commandId)), completed = true))

        repo.send(command)

        assertTrue(repo.state.value.pending.isEmpty())
        assertEquals(1, channel.sent.size)
    }

    @Test
    fun `commands without a running session are dropped`() = runRepoTest { channel, repo ->
        repo.completeSet(SET_A)
        repo.finish()

        assertTrue(channel.sent.isEmpty())
        assertNull(repo.state.value.session)
    }

    @Test
    fun `start workout is sent without a session`() = runRepoTest { channel, repo ->
        repo.startWorkout(planId = null, now = 3_000)

        assertTrue(repo.state.value.isStartPending)
        assertEquals(1, channel.sent.size)
    }

    @Test
    fun `offline state follows the connection`() = runRepoTest { channel, repo ->
        assertEquals(WearConnection.CONNECTED, repo.connection.value)

        channel.connectionFlow.value = WearConnection.DISCONNECTED

        assertEquals(WearConnection.DISCONNECTED, repo.connection.value)
    }

    @Test
    fun `failed send stays pending and is resent on reconnect`() = runRepoTest { channel, repo ->
        channel.snapshotFlow.emit(snapshot(version = 1))
        channel.connectionFlow.value = WearConnection.DISCONNECTED
        channel.failing = true

        repo.completeSet(SET_A, now = 2_000)

        assertTrue(repo.isSetCompleted())
        assertEquals(1, repo.state.value.pending.size)
        assertTrue(channel.sent.isEmpty())

        channel.failing = false
        channel.connectionFlow.value = WearConnection.CONNECTED

        assertEquals(1, channel.sent.size)
        assertEquals(repo.state.value.pending.single().commandId, channel.sent.single().commandId)
    }

    @Test
    fun `reconnect resends pending commands in order and skips acknowledged ones`() = runRepoTest { channel, repo ->
        channel.snapshotFlow.emit(snapshot(version = 1))
        val first = WearCommands.updateSet(SESSION, SET_A, weightKg = 62.5, now = 2_000)
        val second = WearCommands.completeSet(SESSION, SET_A, now = 3_000)
        repo.send(first)
        repo.send(second)
        channel.snapshotFlow.emit(snapshot(version = 2, acks = listOf(CommandAck(first.commandId))))
        channel.sent.clear()

        channel.connectionFlow.value = WearConnection.DISCONNECTED
        channel.connectionFlow.value = WearConnection.CONNECTED

        assertEquals(listOf(second.commandId), channel.sent.map { it.commandId })
    }

    @Test
    fun `a failed send is retried together with the next successful send`() = runRepoTest { channel, repo ->
        channel.snapshotFlow.emit(snapshot(version = 1))
        channel.failing = true
        val first = WearCommands.updateSet(SESSION, SET_A, reps = 9, now = 2_000)
        repo.send(first)
        channel.failing = false

        val second = WearCommands.completeSet(SESSION, SET_A, now = 3_000)
        repo.send(second)

        assertEquals(setOf(first.commandId, second.commandId), channel.sent.map { it.commandId }.toSet())
    }

    @Test
    fun `plans from the phone are exposed`() = runRepoTest { channel, repo ->
        val list = PlanSummaryList(generatedAt = 1, plans = listOf(PlanSummary("p1", "Push", 5)))
        channel.planFlow.emit(list)

        assertEquals(list, repo.plans.value)
        assertEquals(null, repo.session.first())
    }
}
