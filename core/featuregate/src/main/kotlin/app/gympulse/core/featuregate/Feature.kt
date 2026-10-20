package app.gympulse.core.featuregate

/**
 * Funktionen, die hinter einem Premium-Modell oder Remote-Schalter liegen können.
 *
 * [enabledByDefault] ist der lokale Standardwert. Bei [userToggleable] darf der Nutzer das Feature
 * selbst ein- und ausschalten (siehe [UserFeatureToggles]); ein Remote-Kill-Switch gewinnt trotzdem.
 */
enum class Feature(
    val premium: Boolean,
    val enabledByDefault: Boolean = true,
    val userToggleable: Boolean = false,
) {
    CALENDAR(premium = true),
    CLOUD_SYNC(premium = true),
    ADVANCED_STATS(premium = true),
    WEAR_APP(premium = true),
    PROGRESS_PHOTOS(premium = true),

    /** Workouts nach Health Connect schreiben und Körpergewicht lesen; Opt-in in den Einstellungen. */
    HEALTH_CONNECT(premium = false, enabledByDefault = false, userToggleable = true),
}
