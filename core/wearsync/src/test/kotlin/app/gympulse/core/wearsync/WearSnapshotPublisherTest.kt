package app.gympulse.core.wearsync

import app.gympulse.core.model.WorkoutPlan
import app.gympulse.core.wearprotocol.AckResult
import app.gympulse.core.wearprotocol.CommandAck
import app.gympulse.core.wearprotocol.PreviousSet
import app.gympulse.core.wearprotocol.WearPaths
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WearSnapshotPublisherTest {
    private val time = FakeTime()
    private val workout = FakeWorkoutPort(time)
    private val sources = FakeSources()
    private val data = FakeWearDataClient()
    private val store = InMemoryWearSyncStore()
    private val clocks = SetClockBook()
    private val gate = FakeFeatureGate()
    private val watch = FakeWatchAppStatus()
    private val publisher = WearSnapshotPublisher(workout, sources, data, store, clocks, gate, watch, time)

    private fun TestScope.startPublisher() {
        backgroundScope.launch { publisher.run() }
        runCurrent()
    }

    private fun TestScope.settle() {
        advanceTimeBy(WearSnapshotPublisher.DEBOUNCE_MILLIS + 1)
        runCurrent()
    }

    @Test
    fun publishesSessionWithPreviousValuesRestAndVersion() = runTest {
        sources.previous[SQUAT] = listOf(entry("p1", 0, weight = 97.5, reps = 5), entry("p2", 1, weight = 95.0, reps = 6))
        workout.activeSession.value = session()
        val restEnd = time.now().plusSeconds(75)
        workout.restEndsAt.value = restEnd
        startPublisher()
        settle()

        val snapshot = data.snapshots().single()
        val session = snapshot.session!!
        assertEquals(SESSION_ID, session.sessionId)
        assertEquals(restEnd.toEpochMilli(), session.restEndsAt)
        val exercise = session.exercises.single()
        assertEquals("Kniebeuge", exercise.name)
        assertEquals(180, exercise.restSeconds)
        assertEquals(PreviousSet(97.5, 5), exercise.sets[0].previous)
        assertEquals(PreviousSet(95.0, 6), exercise.sets[1].previous)
        assertEquals(1_000L, exercise.sets[0].updatedAt)
        assertTrue("Version mindestens Wanduhr", snapshot.version >= time.now().toEpochMilli())
    }

    @Test
    fun debouncesRapidChangesAndIncreasesVersion() = runTest {
        workout.activeSession.value = session()
        startPublisher()
        settle()

        repeat(5) { i ->
            workout.phoneEdit("k1") { it.copy(reps = 6 + i) }
            advanceTimeBy(50)
        }
        settle()

        val snapshots = data.snapshots()
        assertEquals(2, snapshots.size)
        assertEquals(10, snapshots.last().session!!.exercises.single().sets[0].reps)
        assertTrue(snapshots.last().version > snapshots.first().version)
        assertEquals("Vorher-Werte werden je Session nur einmal geladen", 1, sources.previousCalls)
    }

    @Test
    fun acksAppearInNextSnapshot() = runTest {
        workout.activeSession.value = session()
        startPublisher()
        settle()

        store.remember(CommandAck("cmd-1", AckResult.APPLIED))
        settle()

        assertEquals(listOf(CommandAck("cmd-1", AckResult.APPLIED)), data.snapshots().last().acks)
    }

    @Test
    fun unchangedContentIsNotRepublished() = runTest {
        workout.activeSession.value = session()
        startPublisher()
        settle()

        // Die Übung hat eine eigene Pause, die Einstellung ändert den Snapshot also nicht.
        sources.defaultRestSeconds.value = 120
        settle()

        assertEquals(1, data.snapshots().size)
    }

    @Test
    fun endedSessionPublishesEmptySnapshot() = runTest {
        workout.activeSession.value = session()
        startPublisher()
        settle()

        workout.activeSession.value = null
        settle()

        assertNull(data.snapshots().last().session)
    }

    @Test
    fun publishesPlansWithoutArchived() = runTest {
        sources.plans.value = listOf(
            WorkoutPlan("push", "Push", null, 0xFF00FF00, false, emptyList()),
            WorkoutPlan("old", "Alt", null, null, true, emptyList()),
        )
        startPublisher()
        settle()

        val plans = data.plans().single()
        assertEquals(listOf("push"), plans.plans.map { it.planId })
        assertEquals(time.now().toEpochMilli(), plans.generatedAt)
    }

    @Test
    fun waitsForWatchAppInstallation() = runTest {
        watch.state.value = WatchAppState.NONE
        workout.activeSession.value = session()
        startPublisher()
        settle()
        assertTrue(data.puts.isEmpty())

        watch.state.value = WatchAppState(installed = true, connected = false)
        settle()

        assertEquals(1, data.snapshots().size)
        assertEquals(1, data.plans().size)
    }

    @Test
    fun featureDisabledDeletesItems() = runTest {
        workout.activeSession.value = session()
        startPublisher()
        settle()

        gate.wearApp.value = false
        runCurrent()
        workout.phoneEdit("k1") { it.copy(reps = 12) }
        settle()

        assertEquals(1, data.snapshots().size)
        assertTrue(WearPaths.ACTIVE_WORKOUT in data.deletes)
        assertTrue(WearPaths.PLANS_SUMMARY in data.deletes)
        assertTrue(data.items.isEmpty())
    }

    @Test
    fun failedWriteIsRetriedWithNextChange() = runTest {
        data.failing = true
        workout.activeSession.value = session()
        startPublisher()
        settle()

        data.failing = false
        workout.phoneEdit("k1") { it.copy(reps = 7) }
        settle()

        assertEquals(7, data.snapshots().single().session!!.exercises.single().sets[0].reps)
    }
}
