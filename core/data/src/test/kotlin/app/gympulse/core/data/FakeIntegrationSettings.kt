package app.gympulse.core.data

import app.gympulse.core.settings.HealthMetric
import app.gympulse.core.settings.IntegrationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [IntegrationSettings] for tests: all metrics on, automatic backup off. */
class FakeIntegrationSettings : IntegrationSettings {
    val disabledMetrics = MutableStateFlow<Set<HealthMetric>>(emptySet())
    val autoBackup = MutableStateFlow(false)

    override fun observeHealthMetric(metric: HealthMetric): Flow<Boolean> =
        disabledMetrics.map { metric !in it }

    override suspend fun isHealthMetricEnabled(metric: HealthMetric): Boolean = metric !in disabledMetrics.value

    override suspend fun setHealthMetricEnabled(metric: HealthMetric, enabled: Boolean) {
        disabledMetrics.value = if (enabled) disabledMetrics.value - metric else disabledMetrics.value + metric
    }

    override val autoCloudBackup: Flow<Boolean> get() = autoBackup

    override suspend fun setAutoCloudBackup(enabled: Boolean) {
        autoBackup.value = enabled
    }
}
