package app.gympulse.core.wearsync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.gympulse.core.wearprotocol.AckResult
import app.gympulse.core.wearprotocol.CommandAck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreWearSyncStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    private fun store(name: String = "wear_sync") =
        DataStoreWearSyncStore(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "$name.preferences_pb") })

    @Test
    fun remembersEachCommandOnceAndCapsTheList() = runTest {
        val store = store()
        store.remember(CommandAck("a", AckResult.APPLIED))
        store.remember(CommandAck("a", AckResult.INVALID))
        store.remember(CommandAck("b", AckResult.SESSION_ENDED))
        assertEquals(listOf(CommandAck("a", AckResult.APPLIED), CommandAck("b", AckResult.SESSION_ENDED)), store.processed.first())

        repeat(WearSyncStore.MAX_REMEMBERED + 10) { store.remember(CommandAck("c$it")) }
        val processed = store.processed.first()
        assertEquals(WearSyncStore.MAX_REMEMBERED, processed.size)
        assertEquals("c${WearSyncStore.MAX_REMEMBERED + 9}", processed.last().commandId)
    }

    @Test
    fun versionIsMonotonicAndAtLeastWallClock() = runTest {
        val store = store()
        val first = store.nextVersion(nowMillis = 5_000)
        val second = store.nextVersion(nowMillis = 5_000)
        // Uhr des Telefons springt zurück: Version steigt trotzdem.
        val third = store.nextVersion(nowMillis = 1_000)
        val fourth = store.nextVersion(nowMillis = 9_000)

        assertEquals(5_000L, first)
        assertTrue(second > first)
        assertTrue(third > second)
        assertEquals(9_000L, fourth)
    }
}
