package app.gympulse.feature.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class OptimisticValueTest {
    @Test
    fun echoesOfOwnStepsDoNotResetTheValue() {
        val value = OptimisticValue<Double?>(50.0)
        value.onStep(52.5)
        value.onStep(55.0)
        value.onStored(52.5) // late echo of the first step
        assertEquals(55.0, value.value)
        value.onStored(55.0)
        assertEquals(55.0, value.value)
    }

    @Test
    fun foreignChangesWin() {
        val value = OptimisticValue<Int?>(10)
        value.onStep(11)
        value.onStored(20)
        assertEquals(20, value.value)
    }
}
