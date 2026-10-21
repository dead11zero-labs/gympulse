package app.gympulse.core.designsystem

import app.gympulse.core.designsystem.component.nextStepperRepeatDelayMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberStepperRepeatTest {
    @Test
    fun repeatDelayShrinksEveryStep() {
        var delay = 250L
        repeat(3) {
            val next = nextStepperRepeatDelayMillis(delay)
            assertTrue(next < delay)
            delay = next
        }
    }

    @Test
    fun repeatDelayNeverDropsBelowTheMinimum() {
        var delay = 250L
        repeat(50) { delay = nextStepperRepeatDelayMillis(delay) }
        assertEquals(50L, delay)
        assertEquals(50L, nextStepperRepeatDelayMillis(delay))
    }
}
