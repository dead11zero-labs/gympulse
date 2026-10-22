package app.gympulse.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

class DataStoreIntegrationSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : IntegrationSettings {

    private val preferences: Flow<Preferences> = dataStore.data
        // Eine unlesbare Datei darf die App nicht blockieren: dann gelten die Standardwerte.
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }

    override fun observeHealthMetric(metric: HealthMetric): Flow<Boolean> =
        preferences.map { it[metricKey(metric)] ?: true }.distinctUntilChanged()

    override suspend fun isHealthMetricEnabled(metric: HealthMetric): Boolean =
        observeHealthMetric(metric).first()

    override suspend fun setHealthMetricEnabled(metric: HealthMetric, enabled: Boolean) {
        dataStore.edit { it[metricKey(metric)] = enabled }
    }

    override val autoCloudBackup: Flow<Boolean> =
        preferences.map { it[AUTO_CLOUD_BACKUP] ?: false }.distinctUntilChanged()

    override suspend fun setAutoCloudBackup(enabled: Boolean) {
        dataStore.edit { it[AUTO_CLOUD_BACKUP] = enabled }
    }

    private fun metricKey(metric: HealthMetric) = booleanPreferencesKey("health_metric_${metric.name.lowercase()}")

    private companion object {
        val AUTO_CLOUD_BACKUP = booleanPreferencesKey("auto_cloud_backup")
    }
}
