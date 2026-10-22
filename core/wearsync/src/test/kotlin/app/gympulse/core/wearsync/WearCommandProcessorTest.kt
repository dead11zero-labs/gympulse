package app.gympulse.core.wearsync

import app.gympulse.core.model.WorkoutPlan
import app.gympulse.core.wearprotocol.AckResult
import app.gympulse.core.wearprotocol.CommandAck
import app.gympulse.core.wearprotocol.WearCommands
import app.gympulse.core.wearprotocol.WearPaths
import app.gympulse.core.workout.WorkoutForegroundMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WearCommandProcessorTest {
    private val time = FakeTime()
    private val workout = FakeWorkoutPort(time)
    private val sources = FakeSources()
    private val data = FakeWearDataClient()
    private val store = InMemoryWearSyncStore()
    private val clocks = SetClockBook()
    private val foreground = FakeForeground()
    private val gate = FakeFeatureGate()
    private val processor = WearCommandProcessor(workout, sources, data, store, clocks, foreground, gate, time)

    private val now get() = time.now().toEpochMilli()

    private fun acks() = store.processed.value

    @Test
    fun updateSet_appliesAcksAndDeletesItem() = runTest {
        workout.activeSession.value = session()
        val command = WearCommands.updateSet(SESSION_ID, "k1", weightKg = 102.5, reps = 6, now = now)

        processor.process(listOf(data.putCommand(command)))

        assertEquals(102.5, workout.set("k1").weightKg!!, 0.0)
        assertEquals(6, workout.set("k1").reps)
        assertEquals(listOf(CommandAck(command.commandId, AckResult.APPLIED)), acks())
        assertEquals(listOf(WearPaths.command(command.commandId)), data.deletes)
        assertTrue(data.items.isEmpty())
    }

    @Test
    fun sameCommandTwice_isAppliedOnce() = runTest {
        workout.activeSession.value = session()
        workout.restEndsAt.value = time.now().plusSeconds(60)
        val command = WearCommands.adjustRest(SESSION_ID, 15, now = now)

        processor.process(listOf(data.putCommand(command)))
        // Erneut zugestellt (z. B. Item wurde nicht gelöscht oder nach Neustart nochmal gelesen).
        processor.process(listOf(data.putCommand(command)))
        processor.processPending()

        assertEquals(1, workout.adjusted)
        assertEquals(1, acks().size)
        assertTrue(data.items.isEmpty())
    }

    @Test
    fun completeSet_isIdempotentAgainstLaterPhoneChange() = runTest {
        workout.activeSession.value = session()
        val command = WearCommands.completeSet(SESSION_ID, "k1", now = now)
        processor.process(listOf(data.putCommand(command)))
        assertTrue(workout.set("k1").isCompleted)
        assertEquals(Instant.ofEpochMilli(command.issuedAt), workout.set("k1").completedAt)
        assertEquals(time.now().plusSeconds(90), workout.restEndsAt.value)

        time.advanceMillis(5_000)
        workout.phoneEdit("k1") { it.copy(isCompleted = false, completedAt = null) }
        processor.process(listOf(data.putCommand(command)))

        assertFalse("bekanntes Kommando wird nicht erneut angewendet", workout.set("k1").isCompleted)
    }

    @Test
    fun sessionChecks_ackEndedAndMismatch() = runTest {
        val ended = WearCommands.completeSet("old-session", "k1", now = now)
        processor.process(listOf(data.putCommand(ended)))

        workout.activeSession.value = session()
        val foreign = WearCommands.skipRest("other-session", now = now + 1)
        val missingSet = WearCommands.updateSet(SESSION_ID, "nope", reps = 3, now = now + 2)
        processor.process(listOf(data.putCommand(foreign), data.putCommand(missingSet)))

        assertEquals(
            listOf(
                CommandAck(ended.commandId, AckResult.SESSION_ENDED),
                CommandAck(foreign.commandId, AckResult.SESSION_MISMATCH),
                CommandAck(missingSet.commandId, AckResult.INVALID),
            ),
            acks(),
        )
        assertTrue(data.items.isEmpty())
    }

    @Test
    fun invalidValues_areRejected() = runTest {
        workout.activeSession.value = session()
        val negative = WearCommands.updateSet(SESSION_ID, "k1", weightKg = -5.0, now = now)
        val nan = WearCommands.updateSet(SESSION_ID, "k1", weightKg = Double.NaN, now = now + 1)

        processor.process(listOf(data.putCommand(negative), data.putCommand(nan)))

        assertEquals(listOf(AckResult.INVALID, AckResult.INVALID), acks().map { it.result })
        assertEquals(100.0, workout.set("k1").weightKg!!, 0.0)
    }

    @Test
    fun conflict_newerPhoneChangeWins() = runTest {
        workout.activeSession.value = session()
        // Die Uhr hat um t0 geändert, das Telefon danach um t0 + 2 s; das Kommando kommt erst jetzt an.
        val issuedAt = now
        time.advanceMillis(2_000)
        workout.phoneEdit("k1") { it.copy(weightKg = 110.0) }
        time.advanceMillis(1_000)
        val command = WearCommands.updateSet(SESSION_ID, "k1", weightKg = 105.0, now = issuedAt)

        processor.process(listOf(data.putCommand(command)))

        assertEquals(110.0, workout.set("k1").weightKg!!, 0.0)
        assertEquals("verarbeitet, der Telefon-Stand bleibt", AckResult.APPLIED, acks().single().result)
    }

    @Test
    fun conflict_olderPhoneChangeLoses() = runTest {
        workout.activeSession.value = session()
        workout.phoneEdit("k1") { it.copy(weightKg = 110.0) }
        time.advanceMillis(1_000)
        val command = WearCommands.updateSet(SESSION_ID, "k1", weightKg = 105.0, now = now)

        processor.process(listOf(data.putCommand(command)))

        assertEquals(105.0, workout.set("k1").weightKg!!, 0.0)
    }

    @Test
    fun offlineReplay_laterCommandsStillWinAfterPhoneAppliedEarlierOnes() = runTest {
        workout.activeSession.value = session()
        val t0 = now
        val weight = WearCommands.updateSet(SESSION_ID, "k1", weightKg = 105.0, now = t0)
        val reps = WearCommands.updateSet(SESSION_ID, "k1", reps = 8, now = t0 + 1_000)
        val complete = WearCommands.completeSet(SESSION_ID, "k1", now = t0 + 2_000)
        // Die Uhr war offline, das Telefon bekommt alles eine Minute später.
        time.advanceMillis(60_000)

        // Erst einzeln (wie bei getrennten Zustellungen), in vertauschter Reihenfolge innerhalb des Stapels.
        processor.process(listOf(data.putCommand(weight)))
        time.advanceMillis(10)
        processor.process(listOf(data.putCommand(complete), data.putCommand(reps)))

        val set = workout.set("k1")
        assertEquals(105.0, set.weightKg!!, 0.0)
        assertEquals(8, set.reps)
        assertTrue(set.isCompleted)
        assertEquals(Instant.ofEpochMilli(t0 + 2_000), set.completedAt)
        assertEquals(listOf(weight, reps, complete).map { it.commandId }, acks().map { it.commandId })
        // Snapshot meldet die Uhrzeit des letzten Kommandos als Feld-Uhr, nicht die Übernahmezeit.
        assertEquals(Instant.ofEpochMilli(t0 + 2_000), clocks.updatedAtFor(listOf(set))["k1"])
    }

    @Test
    fun startWorkout_startsSessionAndShowsForeground() = runTest {
        sources.plans.value = listOf(WorkoutPlan("push", "Push", null, null, false, emptyList()))
        val command = WearCommands.startWorkout("push", now = now)

        processor.process(listOf(data.putCommand(command)))

        assertEquals(listOf<String?>("push"), workout.started)
        assertEquals(listOf<String?>("Plan push"), foreground.shown)
        assertEquals(AckResult.APPLIED, acks().single().result)
    }

    @Test
    fun startWorkout_planB_sessionExistsEvenWithoutForegroundService() = runTest {
        foreground.mode = WorkoutForegroundMode.NOTIFICATION_ONLY
        val command = WearCommands.startWorkout(null, now = now)

        processor.process(listOf(data.putCommand(command)))

        assertEquals(SESSION_ID, workout.activeSession.value!!.id)
        assertEquals(1, foreground.shown.size)
        assertEquals(AckResult.APPLIED, acks().single().result)
    }

    @Test
    fun startWorkout_resumesRunningSessionAndRejectsUnknownPlan() = runTest {
        val unknown = WearCommands.startWorkout("gone", now = now)
        processor.process(listOf(data.putCommand(unknown)))
        assertNull(workout.activeSession.value)

        workout.activeSession.value = session()
        val again = WearCommands.startWorkout(null, now = now + 1)
        processor.process(listOf(data.putCommand(again)))

        assertTrue(workout.started.isEmpty())
        assertTrue(foreground.shown.isEmpty())
        assertEquals(listOf(AckResult.INVALID, AckResult.APPLIED), acks().map { it.result })
    }

    @Test
    fun finishAndDiscard_endSessionAndClearPlanBNotification() = runTest {
        workout.activeSession.value = session()
        val finish = WearCommands.finish(SESSION_ID, now = now)
        processor.process(listOf(data.putCommand(finish)))
        assertEquals(1, workout.finished)
        assertEquals(1, foreground.cleared)

        workout.activeSession.value = session(id = "s2")
        processor.process(listOf(data.putCommand(WearCommands.discard("s2", now = now + 1))))
        assertEquals(1, workout.discarded)
        assertNull(workout.activeSession.value)
    }

    @Test
    fun featureDisabled_dropsCommandsWithoutApplying() = runTest {
        gate.wearApp.value = false
        workout.activeSession.value = session()
        val command = WearCommands.updateSet(SESSION_ID, "k1", reps = 12, now = now)

        processor.process(listOf(data.putCommand(command)))

        assertEquals(5, workout.set("k1").reps)
        assertTrue(acks().isEmpty())
        assertTrue(data.items.isEmpty())
    }

    @Test
    fun corruptPayload_isAckedInvalidAndDeleted() = runTest {
        val id = "0192a000-0000-7000-8000-000000000001"
        val item = WearDataItem(WearPaths.command(id), "kaputt".encodeToByteArray())
        data.items[item.path] = item.data!!

        processor.processPending()

        assertEquals(listOf(CommandAck(id, AckResult.INVALID)), acks())
        assertTrue(data.items.isEmpty())
    }

    @Test
    fun otherPaths_areIgnored() = runTest {
        processor.process(listOf(WearDataItem(WearPaths.ACTIVE_WORKOUT, byteArrayOf(1))))

        assertTrue(acks().isEmpty())
        assertTrue(data.deletes.isEmpty())
    }
}
