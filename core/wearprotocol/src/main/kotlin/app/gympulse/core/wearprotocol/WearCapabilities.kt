package app.gympulse.core.wearprotocol

/**
 * Capability-Namen der Wearable Data Layer API. Die Uhr-App meldet [WATCH_APP] in
 * `wear/src/main/res/values/wear.xml` an; das Telefon erkennt daran, ob sie installiert ist.
 */
object WearCapabilities {
    const val WATCH_APP = "gympulse_watch_app"
}
