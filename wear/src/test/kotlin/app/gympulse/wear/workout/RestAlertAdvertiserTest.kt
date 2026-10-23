package app.gympulse.wear.workout

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeCapability : RestAlertCapability {
    val calls = mutableListOf<Boolean>()

    override suspend fun setAdvertised(active: Boolean) {
        calls += active
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RestAlertAdvertiserTest {
    @Test
    fun removesStaleCapabilityOnStart_thenFollowsService() = runTest {
        val capability = FakeCapability()
        val advertiser = RestAlertAdvertiser(capability, backgroundScope)
        runCurrent()
        // Beim Prozessstart läuft kein Dienst: eine liegen gebliebene Anmeldung wird entfernt.
        assertEquals(listOf(false), capability.calls)

        advertiser.setActive(true)
        runCurrent()
        advertiser.setActive(false)
        runCurrent()

        assertEquals(listOf(false, true, false), capability.calls)
    }

    @Test
    fun repeatedValue_isWrittenOnce() = runTest {
        val capability = FakeCapability()
        val advertiser = RestAlertAdvertiser(capability, backgroundScope)
        advertiser.setActive(true)
        advertiser.setActive(true)
        runCurrent()

        assertEquals(listOf(true), capability.calls)
    }
}
