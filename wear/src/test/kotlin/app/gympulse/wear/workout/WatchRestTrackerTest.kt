package app.gympulse.wear.workout

import app.gympulse.core.wearprotocol.SessionSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Startzeit der virtuellen Uhr; die Testzeit von runTest beginnt bei 0. */
private const val T0 = 1_700_000_000_000L

private class FakeEffects : WatchRestEffects {
    val shown = mutableListOf<WorkoutOngoingState>()
    var vibrations = 0
    var awakeUntil: Long? = null
    var stopped = 0

    override fun show(state: WorkoutOngoingState) {
        shown += state
    }

    override fun keepAwakeUntil(endsAt: Long) {
        awakeUntil = endsAt
    }

    override fun releaseAwake() {
        awakeUntil = null
    }

    override fun restFinished() {
        vibrations++
    }

    override fun stop() {
        stopped++
    }
}

private fun session(restEndsAt: Long? = null, title: String? = "Push") = SessionSnapshot(
    sessionId = "s1",
    title = title,
    startedAt = T0 - 600_000,
    restEndsAt = restEndsAt,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WatchRestTrackerTest {
    private val effects = FakeEffects()
    private val sessions = MutableStateFlow<SessionSnapshot?>(null)

    private fun TestScope.startTracker() {
        val clock = WearClock { T0 + testScheduler.currentTime }
        backgroundScope.launch { WatchRestTracker(clock, effects).run(sessions) }
        runCurrent()
    }

    @Test
    fun noSession_stopsService() = runTest {
        startTracker()
        assertEquals(1, effects.stopped)
        assertTrue(effects.shown.isEmpty())
    }

    @Test
    fun restEnd_vibratesOnceAndShowsTrainingAgain() = runTest {
        sessions.value = session(restEndsAt = T0 + 90_000)
        startTracker()

        assertEquals(T0 + 90_000, effects.shown.last().restEndsAt)
        assertEquals(T0 + 90_000, effects.awakeUntil)

        advanceTimeBy(89_999)
        runCurrent()
        assertEquals(0, effects.vibrations)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, effects.vibrations)
        assertNull(effects.shown.last().restEndsAt)
        assertNull(effects.awakeUntil)

        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(1, effects.vibrations)
    }

    @Test
    fun adjustedRest_reschedules() = runTest {
        sessions.value = session(restEndsAt = T0 + 60_000)
        startTracker()

        advanceTimeBy(30_000)
        sessions.value = session(restEndsAt = T0 + 75_000) // +15 s
        runCurrent()
        assertEquals(T0 + 75_000, effects.awakeUntil)

        advanceTimeBy(30_000) // alter Termin verstrichen
        runCurrent()
        assertEquals(0, effects.vibrations)

        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(1, effects.vibrations)
    }

    @Test
    fun skippedRest_doesNotVibrate() = runTest {
        sessions.value = session(restEndsAt = T0 + 60_000)
        startTracker()

        advanceTimeBy(10_000)
        sessions.value = session(restEndsAt = null)
        runCurrent()
        assertNull(effects.awakeUntil)
        assertNull(effects.shown.last().restEndsAt)

        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(0, effects.vibrations)
    }

    @Test
    fun alreadyExpiredRest_doesNotVibrate() = runTest {
        sessions.value = session(restEndsAt = T0 - 1)
        startTracker()

        assertEquals(0, effects.vibrations)
        assertNull(effects.shown.last().restEndsAt)
        assertNull(effects.awakeUntil)
    }

    @Test
    fun unrelatedSnapshotChange_keepsTimerRunning() = runTest {
        sessions.value = session(restEndsAt = T0 + 60_000)
        startTracker()
        val shownBefore = effects.shown.size

        // Gleicher Kopf und gleiche Pause (z. B. nur ein Satzwert geändert): kein Neustart, kein neues Update.
        advanceTimeBy(20_000)
        sessions.value = session(restEndsAt = T0 + 60_000).copy(planId = "p1")
        runCurrent()
        assertEquals(shownBefore, effects.shown.size)

        advanceTimeBy(40_000)
        runCurrent()
        assertEquals(1, effects.vibrations)
    }

    @Test
    fun sessionEndsDuringRest_stopsWithoutVibration() = runTest {
        sessions.value = session(restEndsAt = T0 + 60_000)
        startTracker()

        advanceTimeBy(10_000)
        sessions.value = null
        runCurrent()
        assertEquals(1, effects.stopped)
        assertNull(effects.awakeUntil)

        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(0, effects.vibrations)
    }

    @Test
    fun nextRest_vibratesAgain() = runTest {
        sessions.value = session(restEndsAt = T0 + 60_000)
        startTracker()
        advanceTimeBy(60_000)
        runCurrent()

        sessions.value = session(restEndsAt = T0 + 180_000)
        runCurrent()
        advanceTimeBy(120_000)
        runCurrent()

        assertEquals(2, effects.vibrations)
        assertFalse(effects.stopped > 0)
    }

    @Test
    fun epochToElapsedRealtime_keepsOffset() {
        assertEquals(5_090_000L, epochToElapsedRealtime(T0 + 90_000, nowEpochMillis = T0, nowElapsedMillis = 5_000_000))
        assertEquals(4_400_000L, epochToElapsedRealtime(T0 - 600_000, nowEpochMillis = T0, nowElapsedMillis = 5_000_000))
    }
}
