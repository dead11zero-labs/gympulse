package app.gympulse.wear.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRatePermissionTest {
    @Test
    fun upToApi35_usesBodySensors() {
        assertEquals(HeartRatePermission.BODY_SENSORS, HeartRatePermission.requiredFor(33))
        assertEquals(HeartRatePermission.BODY_SENSORS, HeartRatePermission.requiredFor(34))
        assertEquals(HeartRatePermission.BODY_SENSORS, HeartRatePermission.requiredFor(35))
    }

    @Test
    fun fromApi36_usesGranularHealthPermission() {
        assertEquals(HeartRatePermission.READ_HEART_RATE, HeartRatePermission.requiredFor(36))
        assertEquals(HeartRatePermission.READ_HEART_RATE, HeartRatePermission.requiredFor(37))
    }
}
