package app.gympulse.core.insights.narration

import java.util.Locale

/** Sprachen, in denen Zusammenfassung, Prompt und Textbausteine vorliegen. */
enum class AppLanguage(val tag: String) {
    EN("en"),
    DE("de");

    companion object {
        /** Alles außer Deutsch fällt auf Englisch zurück (wie die App-Ressourcen). */
        fun fromLocale(locale: Locale): AppLanguage = if (locale.language == "de") DE else EN
    }
}
