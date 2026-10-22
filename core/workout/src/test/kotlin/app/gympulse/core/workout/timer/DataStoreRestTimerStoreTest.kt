package app.gympulse.core.workout.timer

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class DataStoreRestTimerStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun writeReadAndClear() = runTest {
        val file = File(tmp.root, "rest_timer.preferences_pb")
        val store = DataStoreRestTimerStore(PreferenceDataStoreFactory.create(scope = scope) { file })
        assertNull(store.read())

        val state = RestTimerState(endsAt = Instant.parse("2026-10-19T10:01:30.250Z"), totalSeconds = 90)
        store.write(state)
        assertEquals(state, store.read())

        store.write(null)
        assertNull(store.read())
    }
}
