package app.gympulse.wear.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {
    @Test
    fun `kg drops trailing zeros and respects locale`() {
        assertEquals("80", formatKg(80.0, Locale.US))
        assertEquals("82.5", formatKg(82.5, Locale.US))
        assertEquals("82,5", formatKg(82.5, Locale.GERMANY))
    }

    @Test
    fun `countdown is formatted as m ss`() {
        assertEquals("1:05", formatCountdown(65))
        assertEquals("0:00", formatCountdown(-3))
    }

    @Test
    fun `rest remaining rounds up and ends at zero`() {
        assertNull(restRemainingSeconds(null, 1_000))
        assertEquals(2, restRemainingSeconds(3_000, 1_001))
        assertEquals(1, restRemainingSeconds(2_000, 1_001))
        assertNull(restRemainingSeconds(2_000, 2_000))
        assertNull(restRemainingSeconds(2_000, 5_000))
    }
}
