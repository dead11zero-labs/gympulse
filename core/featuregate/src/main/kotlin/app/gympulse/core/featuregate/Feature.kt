package app.gympulse.core.featuregate

/** Funktionen, die später hinter einem Premium-Modell oder Remote-Schalter liegen können. */
enum class Feature(val premium: Boolean) {
    CALENDAR(premium = true),
    CLOUD_SYNC(premium = true),
    ADVANCED_STATS(premium = true),
    WEAR_APP(premium = true),
    PROGRESS_PHOTOS(premium = true),
}
