package app.gympulse.core.wearprotocol

/**
 * Capability-Namen der Wearable Data Layer API.
 *
 * - [WATCH_APP]: statisch in `wear/src/main/res/values/wear.xml`; das Telefon erkennt daran, ob die Uhr-App installiert ist.
 * - [WATCH_REST_ALERTS]: meldet die Uhr dynamisch an, solange ihr Workout-Dienst läuft und das Pausenende
 *   selbst signalisiert (Vibration). Nur dann unterdrückt das Telefon seinen eigenen Pausen-Alarm.
 */
object WearCapabilities {
    const val WATCH_APP = "gympulse_watch_app"
    const val WATCH_REST_ALERTS = "gympulse_watch_rest_alerts"
}
