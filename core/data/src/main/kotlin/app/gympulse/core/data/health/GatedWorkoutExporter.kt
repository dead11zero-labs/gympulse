package app.gympulse.core.data.health

import app.gympulse.core.settings.HealthMetric
import app.gympulse.core.settings.IntegrationSettings
import javax.inject.Inject

/** Exportiert Workouts nur, solange der Nutzer [HealthMetric.WORKOUTS] nicht abgeschaltet hat. */
class GatedWorkoutExporter @Inject constructor(
    private val delegate: HealthConnectSync,
    private val settings: IntegrationSettings,
) : FinishedWorkoutExporter {
    override suspend fun export(sessionId: String) {
        if (settings.isHealthMetricEnabled(HealthMetric.WORKOUTS)) delegate.export(sessionId)
    }
}
