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
    fun connectedWatch_onlyWatchVibrates() = runTest {
        watch.state.value = WatchAppState(installed = true, connected = true)
        assertFalse(policy.alertOnPhone())
    }

    @Test
    fun watchAway_phoneVibrates() = runTest {
        watch.state.value = WatchAppState(installed = true, connected = false)
        assertTrue(policy.alertOnPhone())
    }

    @Test
    fun featureOff_phoneVibrates() = runTest {
        gate.wearApp.value = false
        assertTrue(policy.alertOnPhone())
    }
}
