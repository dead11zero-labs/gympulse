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

    /**
     * Workouts nach Health Connect schreiben, Körpergewicht, Herzfrequenz, Schritte und aktive
     * Kalorien lesen; Opt-in in den Einstellungen.
     */
    HEALTH_CONNECT(premium = false, enabledByDefault = false, userToggleable = true),

    /** Unterschalter zu [HEALTH_CONNECT]: Schlafdaten lesen; wirkt nur, wenn Health Connect aktiv ist. */
    HEALTH_SLEEP(premium = false, enabledByDefault = false, userToggleable = true),

    /** Google-Anmeldung und manuelles Cloud-Backup über Supabase; Opt-in in den Einstellungen. */
    CLOUD_BACKUP(premium = false, enabledByDefault = false, userToggleable = true),

    /**
     * Lokaler KI-Coach: lädt ein On-Device-Sprachmodell (Opt-in, nur WLAN) und formuliert damit die
     * Coach-Karte im Dashboard. Ohne Modell oder bei Fehlern gelten Textbausteine. Die klassische
     * Auswertung (Insight-Karten) hängt nicht daran.
     */
    AI_COACH(premium = false, enabledByDefault = false, userToggleable = true),
}
