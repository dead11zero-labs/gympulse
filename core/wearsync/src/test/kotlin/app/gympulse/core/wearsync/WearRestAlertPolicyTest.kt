package app.gympulse.core.wearsync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearRestAlertPolicyTest {
    private val gate = FakeFeatureGate()
    private val watch = FakeWatchAppStatus()
    private val policy = WearRestAlertPolicy(gate, watch)

    @Test
    fun connectedWatchTrackingRest_onlyWatchVibrates() = runTest {
        watch.state.value = WatchAppState(installed = true, connected = true, restAlertsOnWatch = true)
        assertFalse(policy.alertOnPhone())
    }

    @Test
    fun connectedWatchWithoutRunningService_phoneVibrates() = runTest {
        // Uhr verbunden, aber ihr Workout-Dienst läuft nicht (z. B. Start im Hintergrund verweigert).
        watch.state.value = WatchAppState(installed = true, connected = true, restAlertsOnWatch = false)
        assertTrue(policy.alertOnPhone())
    }

    @Test
    fun staleRestCapabilityOfDisconnectedWatch_phoneVibrates() = runTest {
        watch.state.value = WatchAppState(installed = true, connected = false, restAlertsOnWatch = true)
        assertTrue(policy.alertOnPhone())
    }

    @Test
    fun watchAway_phoneVibrates() = runTest {
        watch.state.value = WatchAppState(installed = true, connected = false)
        assertTrue(policy.alertOnPhone())
    }

    @Test
    fun featureOff_phoneVibrates() = runTest {
        watch.state.value = WatchAppState(installed = true, connected = true, restAlertsOnWatch = true)
        gate.wearApp.value = false
        assertTrue(policy.alertOnPhone())
    }
}
