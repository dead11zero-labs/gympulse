package app.gympulse.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class IntegrationSettingsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: IntegrationSettings

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "settings.preferences_pb") }
        settings = DataStoreIntegrationSettings(dataStore)
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun healthMetrics_defaultOn() = runTest {
        HealthMetric.entries.forEach { assertTrue(settings.isHealthMetricEnabled(it)) }
    }

    @Test
    fun healthMetric_isStoredPerMetric() = runTest {
        settings.setHealthMetricEnabled(HealthMetric.STEPS, false)

        assertFalse(settings.isHealthMetricEnabled(HealthMetric.STEPS))
        assertFalse(settings.observeHealthMetric(HealthMetric.STEPS).first())
        assertTrue(settings.isHealthMetricEnabled(HealthMetric.HEART_RATE))

        settings.setHealthMetricEnabled(HealthMetric.STEPS, true)
        assertTrue(settings.isHealthMetricEnabled(HealthMetric.STEPS))
    }

    @Test
    fun autoCloudBackup_defaultsOffAndRoundTrips() = runTest {
        assertFalse(settings.autoCloudBackup.first())

        settings.setAutoCloudBackup(true)
        assertTrue(settings.autoCloudBackup.first())

        settings.setAutoCloudBackup(false)
        assertFalse(settings.autoCloudBackup.first())
    }
}
