package app.gympulse.feature.settings.health

import app.gympulse.core.data.health.HealthAvailability
import app.gympulse.core.data.health.HealthReadType
import app.gympulse.core.settings.HealthMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectUiStateTest {

    private val active = HealthConnectUiState(
        availability = HealthAvailability.AVAILABLE,
        enabled = true,
        permissionsGranted = true,
        grantedReadTypes = setOf(HealthReadType.HEART_RATE, HealthReadType.STEPS),
    )

    @Test
    fun allSwitchesAreOffWhileConnectionIsInactive() {
        val inactive = active.copy(enabled = false, sleepEnabled = true)
        HealthToggle.entries.forEach { assertFalse(inactive.isOn(it)) }
    }

    @Test
    fun metricsDefaultToOnWhenPermitted() {
        assertTrue(active.isOn(HealthToggle.WORKOUTS))
        assertTrue(active.isOn(HealthToggle.HEART_RATE))
        assertTrue(active.isOn(HealthToggle.STEPS))
    }

    @Test
    fun switchIsOffWithoutReadPermission() {
        assertFalse(active.isOn(HealthToggle.CALORIES))
        assertTrue(active.needsPermission(HealthToggle.CALORIES))
        assertFalse(active.needsPermission(HealthToggle.STEPS))
        // Writing workouts needs no read permission.
        assertFalse(active.needsPermission(HealthToggle.WORKOUTS))
    }

    @Test
    fun disabledMetricIsOffEvenWithPermission() {
        val state = active.copy(enabledMetrics = HealthMetric.entries.toSet() - HealthMetric.STEPS)
        assertFalse(state.isOn(HealthToggle.STEPS))
        assertTrue(state.isOn(HealthToggle.HEART_RATE))
    }

    @Test
    fun sleepFollowsItsOwnSwitchAndPermission() {
        assertFalse(active.isOn(HealthToggle.SLEEP))

        val permitted = active.copy(grantedReadTypes = active.grantedReadTypes + HealthReadType.SLEEP)
        assertFalse(permitted.isOn(HealthToggle.SLEEP))
        assertTrue(permitted.copy(sleepEnabled = true).isOn(HealthToggle.SLEEP))
        assertFalse(active.copy(sleepEnabled = true).isOn(HealthToggle.SLEEP))
    }

    @Test
    fun toggleMappingCoversEveryMetricExactlyOnce() {
        val mapped = HealthToggle.entries.mapNotNull { it.metric }
        assertEquals(HealthMetric.entries.toSet(), mapped.toSet())
        assertEquals(mapped.size, mapped.toSet().size)
    }
}
