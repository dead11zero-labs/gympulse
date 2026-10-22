package app.gympulse.core.settings

import kotlinx.coroutines.flow.Flow

/**
 * Health-Connect-Datentypen, die einzeln abgeschaltet werden können. Schlaf hat bewusst keinen
 * Eintrag: Er hängt am Feature-Gate `HEALTH_SLEEP` (eigener Opt-in) und wird nicht doppelt geführt.
 */
enum class HealthMetric {
    /** Beendete Workouts nach Health Connect schreiben. */
    WORKOUTS,

    /** Herzfrequenz lesen. */
    HEART_RATE,

    /** Schritte lesen. */
    STEPS,

    /** Aktive Kalorien lesen. */
    CALORIES,
}

/**
 * Einstellungen der optionalen Anbindungen (Health Connect, Cloud-Backup) in derselben DataStore-Datei
 * wie [UserSettingsRepository]. Eigene Schnittstelle, weil nur die Integrationen sie brauchen.
 *
 * - Health-Metriken: Standard an; sie wirken erst, wenn Health Connect selbst aktiv ist.
 * - Automatisches Cloud-Backup: Standard aus.
 */
interface IntegrationSettings {
    fun observeHealthMetric(metric: HealthMetric): Flow<Boolean>

    suspend fun isHealthMetricEnabled(metric: HealthMetric): Boolean

    suspend fun setHealthMetricEnabled(metric: HealthMetric, enabled: Boolean)

    /** Ob beim App-Start automatisch ein Cloud-Backup angestoßen wird (Standard aus). */
    val autoCloudBackup: Flow<Boolean>

    suspend fun setAutoCloudBackup(enabled: Boolean)
}
