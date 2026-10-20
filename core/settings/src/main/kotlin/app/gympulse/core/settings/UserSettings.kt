package app.gympulse.core.settings

import java.time.DayOfWeek

/** Darstellung der App: folgt dem System oder ist fest hell bzw. dunkel. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Erster Tag der Woche für Dashboard und Kalender. */
enum class WeekStart(val dayOfWeek: DayOfWeek) {
    MONDAY(DayOfWeek.MONDAY),
    SUNDAY(DayOfWeek.SUNDAY),
}

/** App-Sprache. [languageTag] ist leer für "Systemsprache" (AppCompat-Konvention). */
enum class AppLanguage(val languageTag: String) {
    SYSTEM(""),
    GERMAN("de"),
    ENGLISH("en"),
    ;

    companion object {
        /** Ordnet ein BCP-47-Tag (z. B. "de-DE") einer unterstützten Sprache zu, sonst [SYSTEM]. */
        fun fromLanguageTag(tag: String): AppLanguage {
            val language = tag.substringBefore(',').substringBefore('-').substringBefore('_').lowercase()
            return entries.firstOrNull { it != SYSTEM && it.languageTag == language } ?: SYSTEM
        }
    }
}

/** Grenzen und Standardwerte der Einstellungen. Setter begrenzen Werte auf diese Bereiche. */
object SettingsLimits {
    const val DEFAULT_REST_TIMER_SECONDS = 90
    const val DEFAULT_WEEKLY_GOAL = 3

    /** Pausentimer: 10 s bis 10 min. */
    val REST_TIMER_SECONDS: IntRange = 10..600

    /** Wochenziel: 1 bis 14 Trainings pro Woche. */
    val WEEKLY_GOAL: IntRange = 1..14
}

/** Alle Einstellungen auf einmal. Gewicht und Profil liegen bewusst nicht hier, siehe ProfileRepository. */
data class UserSettings(
    val restTimerSeconds: Int = SettingsLimits.DEFAULT_REST_TIMER_SECONDS,
    val weekStart: WeekStart = WeekStart.MONDAY,
    val weeklyGoal: Int = SettingsLimits.DEFAULT_WEEKLY_GOAL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val language: AppLanguage = AppLanguage.SYSTEM,
    /** Ob das Onboarding beim ersten Start abgeschlossen oder übersprungen wurde. */
    val onboardingCompleted: Boolean = false,
)
