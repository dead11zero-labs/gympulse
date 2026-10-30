package app.gympulse.core.settings

import app.gympulse.core.designsystem.theme.AccentColor
import java.time.DayOfWeek

/** Darstellung der App: folgt dem System oder ist fest hell bzw. dunkel. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Erster Tag der Woche für Dashboard und Kalender. */
enum class WeekStart(val dayOfWeek: DayOfWeek) {
    MONDAY(DayOfWeek.MONDAY),
    SUNDAY(DayOfWeek.SUNDAY),
}

/**
 * App-Sprache. [languageTag] ist leer für "Systemsprache" (AppCompat-Konvention).
 * Neue Sprachen hier ergänzen sowie in `locales_config.xml` und den `localeFilters` von `app` und `wear`.
 */
enum class AppLanguage(val languageTag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    GERMAN("de"),
    SPANISH("es"),
    FRENCH("fr"),
    ITALIAN("it"),
    PORTUGUESE_BRAZIL("pt-BR"),
    DUTCH("nl"),
    POLISH("pl"),
    TURKISH("tr"),
    RUSSIAN("ru"),
    JAPANESE("ja"),
    KOREAN("ko"),
    CHINESE_SIMPLIFIED("zh-CN"),
    ;

    companion object {
        /**
         * Ordnet ein BCP-47-Tag (z. B. "de-DE") einer unterstützten Sprache zu, sonst [SYSTEM].
         * Ein exakter Treffer (z. B. "pt-BR") gewinnt vor dem reinen Sprachcode.
         */
        fun fromLanguageTag(tag: String): AppLanguage {
            val first = tag.substringBefore(',').replace('_', '-')
            entries.firstOrNull { it != SYSTEM && it.languageTag.equals(first, ignoreCase = true) }?.let { return it }
            val language = first.substringBefore('-').lowercase()
            return entries.firstOrNull { it != SYSTEM && it.languageTag.substringBefore('-') == language } ?: SYSTEM
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
    /** Standard dunkel: Kinetic Material ist AMOLED-first. */
    val themeMode: ThemeMode = ThemeMode.DARK,
    /** Material You ist optional; Standard ist das eigene Kinetic-Schema. */
    val dynamicColor: Boolean = false,
    /** Akzentfarbe des eigenen Schemas; mit Material You (dynamicColor) hat dieses Vorrang. */
    val accentColor: AccentColor = AccentColor.MINT,
    val language: AppLanguage = AppLanguage.SYSTEM,
    /** Ob das Onboarding beim ersten Start abgeschlossen oder übersprungen wurde. */
    val onboardingCompleted: Boolean = false,
)
