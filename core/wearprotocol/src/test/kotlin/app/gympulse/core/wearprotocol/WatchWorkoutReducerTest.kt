package app.gympulse.core.wearprotocol

import app.gympulse.core.wearprotocol.Fixtures.SESSION
import app.gympulse.core.wearprotocol.Fixtures.SET_1
import app.gympulse.core.wearprotocol.Fixtures.SET_2
import app.gympulse.core.wearprotocol.Fixtures.SET_3
import app.gympulse.core.wearprotocol.Fixtures.cmdId
import app.gympulse.core.wearprotocol.Fixtures.set
import app.gympulse.core.wearprotocol.Fixtures.snapshot
import app.gympulse.core.wearprotocol.WatchWorkoutReducer.onLocalCommand
import app.gympulse.core.wearprotocol.WatchWorkoutReducer.onSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchWorkoutReducerTest {
    private val initial = onSnapshot(WatchWorkoutState(), snapshot(version = 1))

    private fun update(n: Int, at: Long, weight: Double? = null, reps: Int? = null, setId: String = SET_1, session: String = SESSION) =
        UpdateSet(cmdId(n), at, session, setId, weight, reps)

    private fun complete(n: Int, at: Long, setId: String = SET_1, completed: Boolean = true) =
        CompleteSet(cmdId(n), at, SESSION, setId, completed)

    private val WatchWorkoutState.set1 get() = session!!.set(SET_1)

    // --- Grundverhalten -------------------------------------------------------------------

    @Test
    fun firstSnapshot_isAdopted() {
        assertEquals(1L, initial.version)
        assertEquals(Fixtures.session(), initial.session)
        assertFalse(initial.hasPendingCommands)
    }

    @Test
    fun localCommand_isAppliedOptimistically() {
        val state = onLocalCommand(initial, update(1, at = 2_000, weight = 82.5, reps = 6))
        assertEquals(82.5, state.set1.weightKg!!, 0.0)
        assertEquals(6, state.set1.reps)
        assertEquals(1, state.pending.size)
        // Bestätigter Stand bleibt unberührt.
        assertEquals(80.0, state.confirmed!!.session!!.set(SET_1).weightKg!!, 0.0)
    }

    @Test
    fun updateSet_nullFieldsStayUnchanged() {
        val state = onLocalCommand(initial, update(1, at = 2_000, reps = 12))
        assertEquals(80.0, state.set1.weightKg!!, 0.0)
        assertEquals(12, state.set1.reps)
    }

    @Test
    fun updateSet_invalidValuesAreIgnored() {
        var state = onLocalCommand(initial, update(1, at = 2_000, weight = -5.0, reps = -1))
        state = onLocalCommand(state, update(2, at = 2_001, weight = Double.NaN))
        assertEquals(80.0, state.set1.weightKg!!, 0.0)
        assertEquals(8, state.set1.reps)
    }

    @Test
    fun completeSet_marksDoneAndStartsRest() {
        val state = onLocalCommand(initial, complete(1, at = 5_000))
        assertTrue(state.set1.isCompleted)
        assertEquals(5_000L, state.set1.completedAt)
        assertEquals(5_000L + 90_000L, state.session!!.restEndsAt)
    }

    @Test
    fun completeSet_withoutRestSeconds_keepsRest() {
        val state = onLocalCommand(initial, complete(1, at = 5_000, setId = SET_3))
        assertTrue(state.session!!.set(SET_3).isCompleted)
        assertNull(state.session!!.restEndsAt)
    }

    @Test
    fun uncomplete_clearsCompletion() {
        var state = onLocalCommand(initial, complete(1, at = 5_000))
        state = onLocalCommand(state, complete(2, at = 6_000, completed = false))
        assertFalse(state.set1.isCompleted)
        assertNull(state.set1.completedAt)
    }

    @Test
    fun restCommands() {
        val running = onSnapshot(WatchWorkoutState(), snapshot(1, Fixtures.session(restEndsAt = 60_000)))
        val longer = onLocalCommand(running, AdjustRest(cmdId(1), 10_000, SESSION, deltaSeconds = 30))
        assertEquals(90_000L, longer.session!!.restEndsAt)
        val shorter = onLocalCommand(longer, AdjustRest(cmdId(2), 10_001, SESSION, deltaSeconds = -100))
        assertNull("Pause endet, wenn sie in die Vergangenheit verkürzt wird", shorter.session!!.restEndsAt)
        val skipped = onLocalCommand(running, SkipRest(cmdId(3), 10_000, SESSION))
        assertNull(skipped.session!!.restEndsAt)
        val noRest = onLocalCommand(initial, AdjustRest(cmdId(4), 10_000, SESSION, 30))
        assertNull(noRest.session!!.restEndsAt)
    }

    @Test
    fun finishAndDiscard_hideSessionUntilAcknowledged() {
        val finishing = onLocalCommand(initial, Finish(cmdId(1), 9_000, SESSION))
        assertNull(finishing.session)
        assertTrue(finishing.isEndPending)
        val discarding = onLocalCommand(initial, Discard(cmdId(2), 9_000, SESSION))
        assertNull(discarding.session)

        val done = onSnapshot(finishing, snapshot(2, session = null, CommandAck(cmdId(1))))
        assertNull(done.session)
        assertFalse(done.isEndPending)
    }

    @Test
    fun startWorkout_isPendingUntilPhoneReportsSession() {
        val idle = onSnapshot(WatchWorkoutState(), snapshot(1, session = null))
        val starting = onLocalCommand(idle, StartWorkout(cmdId(1), 1_000, "plan-push"))
        assertNull(starting.session)
        assertTrue(starting.isStartPending)

        val started = onSnapshot(starting, snapshot(2, Fixtures.session(), CommandAck(cmdId(1))))
        assertFalse(started.isStartPending)
        assertEquals(SESSION, started.session?.sessionId)
    }

    @Test
    fun commandsForOtherSession_doNotChangeView() {
        val state = onLocalCommand(initial, update(1, at = 2_000, weight = 999.0, session = Fixtures.OTHER_SESSION))
        assertEquals(initial.session, state.session)
        assertEquals(1, state.pending.size)
        val finish = onLocalCommand(initial, Finish(cmdId(2), 2_000, Fixtures.OTHER_SESSION))
        assertEquals(initial.session, finish.session)
    }

    @Test
    fun unknownSet_isIgnored() {
        val state = onLocalCommand(initial, update(1, at = 2_000, weight = 1.0, setId = "missing"))
        assertEquals(initial.session, state.session)
    }

    // --- Snapshots, Versionen und Quittungen ----------------------------------------------

    @Test
    fun acknowledgedCommand_isDroppedAndPhoneValueShown() {
        val optimistic = onLocalCommand(initial, update(1, at = 2_000, weight = 82.5))
        // Telefon hat 82.5 übernommen und rundet z. B. auf 82.0 – die Uhr zeigt dann den Telefon-Stand.
        val phone = Fixtures.session(set1 = set(SET_1, 1, weightKg = 82.0, reps = 8, updatedAt = 2_100))
        val state = onSnapshot(optimistic, snapshot(2, phone, CommandAck(cmdId(1))))
        assertTrue(state.pending.isEmpty())
        assertEquals(82.0, state.set1.weightKg!!, 0.0)
        assertTrue(cmdId(1) in state.acknowledgedIds)
    }

    @Test
    fun snapshotWithoutAck_keepsCommandApplied() {
        val optimistic = onLocalCommand(initial, update(1, at = 2_000, weight = 82.5))
        // Neuer Stand vom Telefon (anderer Satz geändert), Kommando noch nicht verarbeitet.
        val phone = Fixtures.session().let { s ->
            s.copy(exercises = s.exercises.map { e -> e.copy(sets = e.sets.map { if (it.setId == SET_2) it.copy(reps = 7, updatedAt = 1_500) else it }) })
        }
        val state = onSnapshot(optimistic, snapshot(2, phone))
        assertEquals(1, state.pending.size)
        assertEquals(82.5, state.set1.weightKg!!, 0.0)
        assertEquals(7, state.session!!.set(SET_2).reps)
    }

    @Test
    fun olderOrEqualVersion_isIgnored() {
        val v5 = onSnapshot(initial, snapshot(5, Fixtures.session(restEndsAt = 1)))
        val stale = Fixtures.session(set1 = set(SET_1, 1, weightKg = 1.0, reps = 1, updatedAt = 9_999))
        assertSame(v5, onSnapshot(v5, snapshot(5, stale)))
        assertSame(v5, onSnapshot(v5, snapshot(3, stale, CommandAck(cmdId(1)))))
    }

    @Test
    fun ackInStaleSnapshot_doesNotDropPendingCommand() {
        val v5 = onSnapshot(initial, snapshot(5))
        val pending = onLocalCommand(v5, update(1, at = 2_000, weight = 90.0))
        val state = onSnapshot(pending, snapshot(4, Fixtures.session(), CommandAck(cmdId(1))))
        assertEquals(1, state.pending.size)
        assertEquals(90.0, state.set1.weightKg!!, 0.0)
    }

    @Test
    fun rejectedCommand_producesNoticeOnceAndIsDropped() {
        val pending = onLocalCommand(initial, update(1, at = 2_000, weight = 90.0))
        val rejected = onSnapshot(pending, snapshot(2, Fixtures.session(), CommandAck(cmdId(1), AckResult.SESSION_ENDED)))
        assertTrue(rejected.pending.isEmpty())
        assertEquals(listOf(CommandAck(cmdId(1), AckResult.SESSION_ENDED)), rejected.notices)
        assertEquals(80.0, rejected.set1.weightKg!!, 0.0)

        // Derselbe Ack im nächsten Snapshot erzeugt keinen zweiten Hinweis.
        val again = onSnapshot(rejected, snapshot(3, Fixtures.session(), CommandAck(cmdId(1), AckResult.SESSION_ENDED)))
        assertEquals(1, again.notices.size)
        assertTrue(WatchWorkoutReducer.dismissNotices(again).notices.isEmpty())
    }

    @Test
    fun acksOfForeignCommands_produceNoNotice() {
        val state = onSnapshot(initial, snapshot(2, Fixtures.session(), CommandAck(cmdId(77), AckResult.INVALID)))
        assertTrue(state.notices.isEmpty())
    }

    @Test
    fun incompatibleProtocol_isFlaggedAndIgnored() {
        val alien = WorkoutSnapshot(protocolVersion = WearProtocol.VERSION + 1, version = 99, session = null)
        val state = onSnapshot(initial, alien)
        assertTrue(state.incompatibleProtocol)
        assertEquals(initial.confirmed, state.confirmed)
        assertEquals(initial.session, state.session)
        val recovered = onSnapshot(state, snapshot(2))
        assertFalse(recovered.incompatibleProtocol)
    }

    @Test
    fun newSessionFromPhone_dropsViewOfOldSessionCommands() {
        val pending = onLocalCommand(initial, update(1, at = 2_000, weight = 90.0))
        val other = Fixtures.session().copy(sessionId = Fixtures.OTHER_SESSION)
        val state = onSnapshot(pending, snapshot(2, other))
        assertEquals(80.0, state.set1.weightKg!!, 0.0) // Kommando gilt der alten Session
        assertEquals(1, state.pending.size)
    }

    // --- Idempotenz -----------------------------------------------------------------------

    @Test
    fun sameCommandTwice_isNoOp() {
        val command = update(1, at = 2_000, weight = 85.0)
        val once = onLocalCommand(initial, command)
        assertSame(once, onLocalCommand(once, command))
    }

    @Test
    fun alreadyAcknowledgedCommand_isNotReapplied() {
        val command = complete(1, at = 2_000)
        val acked = onSnapshot(onLocalCommand(initial, command), snapshot(2, Fixtures.session(), CommandAck(cmdId(1))))
        // Telefon hat den Haken zurückgenommen (snapshot v2 ohne Haken); erneute Zustellung ändert nichts.
        val replayed = onLocalCommand(acked, command)
        assertSame(acked, replayed)
        assertFalse(replayed.set1.isCompleted)
    }

    @Test
    fun sameSnapshotTwice_isNoOp() {
        val s = snapshot(2, Fixtures.session(restEndsAt = 5))
        val once = onSnapshot(initial, s)
        assertSame(once, onSnapshot(once, s))
    }

    @Test
    fun rememberedAcks_areBounded() {
        var state = initial
        for (v in 2..400) {
            state = onSnapshot(state, snapshot(v.toLong(), Fixtures.session(), CommandAck(cmdId(v))))
        }
        assertEquals(WatchWorkoutReducer.MAX_REMEMBERED_ACKS, state.acknowledgedIds.size)
        assertEquals(cmdId(400), state.acknowledgedIds.last())
    }

    // --- Konflikte am selben Satz ---------------------------------------------------------

    @Test
    fun laterPhoneChange_winsOverOlderPendingCommand() {
        val pending = onLocalCommand(initial, update(1, at = 2_000, weight = 85.0))
        // Am Telefon wurde der Satz später (3000) geändert, Kommando noch nicht quittiert.
        val phone = Fixtures.session(set1 = set(SET_1, 1, weightKg = 90.0, reps = 8, updatedAt = 3_000))
        val state = onSnapshot(pending, snapshot(2, phone))
        assertEquals(90.0, state.set1.weightKg!!, 0.0)
    }

    @Test
    fun laterCommand_winsOverOlderPhoneChange() {
        val pending = onLocalCommand(initial, update(1, at = 4_000, weight = 85.0))
        val phone = Fixtures.session(set1 = set(SET_1, 1, weightKg = 90.0, reps = 8, updatedAt = 3_000))
        val state = onSnapshot(pending, snapshot(2, phone))
        assertEquals(85.0, state.set1.weightKg!!, 0.0)
    }

    @Test
    fun conflictIsResolvedPerField() {
        // Uhr ändert Wiederholungen (2000) und Gewicht (2000); Telefon meldet nur das Gewicht später (3000).
        val pending = onLocalCommand(initial, update(1, at = 2_000, weight = 85.0, reps = 5))
        val phoneSet = set(SET_1, 1, weightKg = 90.0, reps = 8, updatedAt = 3_000)
            .copy(fieldUpdatedAt = SetFieldTimes(weightKg = 3_000, reps = 1_000, completed = 1_000))
        val state = onSnapshot(pending, snapshot(2, Fixtures.session(set1 = phoneSet)))
        assertEquals("Gewicht: Telefon später", 90.0, state.set1.weightKg!!, 0.0)
        assertEquals("Wiederholungen: Uhr später", 5, state.set1.reps)
    }

    @Test
    fun twoCommandsOnSameField_laterTimestampWinsRegardlessOfArrivalOrder() {
        val early = update(1, at = 2_000, weight = 82.5)
        val late = update(2, at = 3_000, weight = 85.0)
        val inOrder = onLocalCommand(onLocalCommand(initial, early), late)
        val reversed = onLocalCommand(onLocalCommand(initial, late), early)
        assertEquals(85.0, inOrder.set1.weightKg!!, 0.0)
        assertEquals(85.0, reversed.set1.weightKg!!, 0.0)
        assertEquals(inOrder.session, reversed.session)
    }

    @Test
    fun commandsOnDifferentFields_bothApply() {
        val weight = update(1, at = 3_000, weight = 85.0)
        val reps = update(2, at = 2_000, reps = 6)
        val state = onLocalCommand(onLocalCommand(initial, weight), reps)
        assertEquals(85.0, state.set1.weightKg!!, 0.0)
        assertEquals(6, state.set1.reps)
    }

    @Test
    fun completion_laterTimestampWins() {
        val tick = complete(1, at = 3_000)
        val untick = complete(2, at = 2_000, completed = false)
        val state = onLocalCommand(onLocalCommand(initial, tick), untick)
        assertTrue(state.set1.isCompleted)
    }

    @Test
    fun completionByPhoneLater_winsOverOlderUntick() {
        val phone = Fixtures.session(set1 = set(SET_1, 1, weightKg = 80.0, reps = 8, updatedAt = 1_000, completed = true))
        val ticked = onSnapshot(WatchWorkoutState(), snapshot(1, phone))
        val pending = onLocalCommand(ticked, complete(1, at = 2_000, completed = false))
        assertFalse(pending.set1.isCompleted)
        val phoneLater = Fixtures.session(set1 = set(SET_1, 1, weightKg = 80.0, reps = 8, updatedAt = 2_500, completed = true))
        val state = onSnapshot(pending, snapshot(2, phoneLater))
        assertTrue(state.set1.isCompleted)
    }

    @Test
    fun lostCompletion_doesNotStartRest() {
        val phone = Fixtures.session(set1 = set(SET_1, 1, weightKg = 80.0, reps = 8, updatedAt = 9_000))
        val base = onSnapshot(WatchWorkoutState(), snapshot(1, phone))
        val state = onLocalCommand(base, complete(1, at = 2_000))
        assertFalse(state.set1.isCompleted)
        assertNull(state.session!!.restEndsAt)
    }

    @Test
    fun equalTimestamps_areDeterministic() {
        val a = update(1, at = 2_000, weight = 81.0)
        val b = update(2, at = 2_000, weight = 82.0)
        val ab = onLocalCommand(onLocalCommand(initial, a), b)
        val ba = onLocalCommand(onLocalCommand(initial, b), a)
        assertEquals(ab.session, ba.session)
        assertEquals(82.0, ab.set1.weightKg!!, 0.0) // höhere commandId gewinnt bei Gleichstand
    }

    @Test
    fun applyCommand_singleStep() {
        val session = Fixtures.session()
        val updated = WatchWorkoutReducer.applyCommand(session, update(1, at = 2_000, weight = 70.0, setId = SET_2))
        assertEquals(70.0, updated!!.set(SET_2).weightKg!!, 0.0)
        assertNull(WatchWorkoutReducer.applyCommand(session, Finish(cmdId(2), 1, SESSION)))
        assertTrue(WatchWorkoutReducer.commandWins(2_000, 2_000))
        assertFalse(WatchWorkoutReducer.commandWins(1_999, 2_000))
    }
}
