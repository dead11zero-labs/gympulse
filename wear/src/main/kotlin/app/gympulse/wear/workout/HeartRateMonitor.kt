package app.gympulse.wear.workout

import kotlinx.coroutines.flow.StateFlow

/** Aktueller Zustand der Pulsmessung auf der Uhr. */
sealed interface HeartRateReading {
    /** Es läuft keine Messung (kein Workout oder beendet). */
    data object Off : HeartRateReading

    /** Messung läuft, es gibt aber noch keinen Wert (Sensor sucht Hautkontakt). */
    data object Searching : HeartRateReading

    /** Letzter Messwert. [sequence] zählt hoch, damit zwei gleiche Werte hintereinander beide ankommen. */
    data class Bpm(val value: Int, val sequence: Long) : HeartRateReading

    /** Der Nutzer hat die Pulsberechtigung nicht erteilt; das Workout läuft ohne Puls weiter. */
    data object NoPermission : HeartRateReading

    /** Die Uhr kann keinen Puls liefern (Health Services fehlen, Sensor belegt, Messung abgebrochen). */
    data object Unavailable : HeartRateReading
}

/**
 * Pulsmessung der Uhr. Die Implementierung ([HealthServicesHeartRateMonitor]) hängt an den Health
 * Services; Tests nutzen einen Fake.
 */
interface HeartRateMonitor {
    val reading: StateFlow<HeartRateReading>

    /**
     * Startet die Messung. Idempotent: läuft sie schon, passiert nichts.
     *
     * @return false, wenn sie nicht starten konnte; [reading] nennt dann den Grund. Ein späterer
     *   Aufruf versucht es erneut (z. B. nachdem die Berechtigung erteilt wurde).
     */
    suspend fun start(): Boolean

    /** Beendet die Messung und setzt [reading] auf [HeartRateReading.Off]. Darf aus jedem Scope kommen. */
    fun stop()
}

/**
 * Welche Berechtigung die Pulsmessung braucht. Ab Wear OS 6 (API 36) ersetzt die granulare
 * Health-Berechtigung `READ_HEART_RATE` die frühere `BODY_SENSORS`.
 */
object HeartRatePermission {
    const val BODY_SENSORS = "android.permission.BODY_SENSORS"
    const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
    private const val FIRST_SDK_WITH_HEALTH_PERMISSION = 36

    fun requiredFor(sdkInt: Int): String =
        if (sdkInt >= FIRST_SDK_WITH_HEALTH_PERMISSION) READ_HEART_RATE else BODY_SENSORS
}
