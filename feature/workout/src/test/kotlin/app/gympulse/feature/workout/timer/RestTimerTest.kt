package app.gympulse.feature.workout.timer

import app.gympulse.core.data.util.TimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class RestTimerTest {
    private val clock = FakeTime(Instant.parse("2026-10-19T10:00:00Z"))
    private val timer = RestTimer(clock)

    @Test
    fun start_setsEndTimeFromNow() {
        timer.start(90)

        val state = timer.state.value!!
        assertEquals(Instant.parse("2026-10-19T10:01:30Z"), state.endsAt)
        assertEquals(90, state.remainingSeconds(clock.now()))
        assertEquals(1f, state.remainingFraction(clock.now()), 0.001f)
    }

    @Test
    fun remaining_isDerivedFromEndTime_evenAfterLongBackgroundGap() {
        timer.start(90)
        val state = timer.state.value!!

        clock.advanceMillis(30_500)
        assertEquals(60, state.remainingSeconds(clock.now()))
        assertFalse(state.isFinished(clock.now()))

        // No ticks while in the background: the state still answers correctly.
        clock.advanceMillis(10 * 60_000L)
        assertEquals(0, state.remainingSeconds(clock.now()))
        assertTrue(state.isFinished(clock.now()))
        assertNull(timer.state.value.runningAt(clock.now()))
    }

    @Test
    fun adjust_movesEndTimeAndTotal() {
        timer.start(90)
        clock.advanceMillis(30_000)

        timer.adjust(RestTimer.ADJUST_STEP_SECONDS)
        assertEquals(75, timer.state.value!!.remainingSeconds(clock.now()))
        assertEquals(105, timer.state.value!!.totalSeconds)

        timer.adjust(-RestTimer.ADJUST_STEP_SECONDS)
        assertEquals(60, timer.state.value!!.remainingSeconds(clock.now()))
    }

    @Test
    fun adjust_belowZero_stopsTimer() {
        timer.start(10)

        timer.adjust(-RestTimer.ADJUST_STEP_SECONDS)

        assertNull(timer.state.value)
    }

    @Test
    fun adjust_withoutRunningTimer_doesNothing() {
        timer.adjust(RestTimer.ADJUST_STEP_SECONDS)
        assertNull(timer.state.value)

        timer.start(10)
        clock.advanceMillis(11_000)
        timer.adjust(RestTimer.ADJUST_STEP_SECONDS)
        assertNull(timer.state.value)
    }

    @Test
    fun skip_andNonPositiveStart_clearTimer() {
        timer.start(90)
        timer.skip()
        assertNull(timer.state.value)

        timer.start(0)
        assertNull(timer.state.value)
    }

    @Test
    fun remainingSeconds_roundsUp() {
        timer.start(5)
        clock.advanceMillis(4_100)

        assertEquals(1, timer.state.value!!.remainingSeconds(clock.now()))
    }

    private class FakeTime(var instant: Instant) : TimeProvider {
        override fun now(): Instant = instant
        override fun zone(): ZoneId = ZoneOffset.UTC
        fun advanceMillis(millis: Long) {
            instant = instant.plusMillis(millis)
        }
    }
}
