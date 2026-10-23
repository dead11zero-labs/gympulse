package app.gympulse.core.insights.narration

/** Fertiger Prompt: [system] enthält die Regeln, [user] die Zusammenfassung. */
data class InsightPrompt(val system: String, val user: String) {
    /** Beides in einem Text, für Modelle ohne getrennte System-Rolle. */
    val fullText: String get() = system + "\n\n" + user

    val estimatedTokens: Int get() = TokenEstimator.estimate(fullText)

    companion object {
        /**
         * Das Sprachmodell formuliert nur: Zahlen kommen ausschließlich aus der Zusammenfassung
         * (Schicht 1), medizinische Aussagen sind ausgeschlossen, Antwort in der App-Sprache.
         */
        fun system(language: AppLanguage): String = when (language) {
            AppLanguage.EN ->
                "You are a concise strength training coach inside an app. " +
                    "Use only the numbers provided below, never calculate or invent numbers. " +
                    "No medical statements or diagnoses. " +
                    "Answer in English with at most 5 short bullet points, starting with the most important finding."
            AppLanguage.DE ->
                "Du bist ein knapper Krafttraining-Coach in einer App. " +
                    "Nutze ausschließlich die unten gelieferten Zahlen, rechne nichts selbst und erfinde keine Zahlen. " +
                    "Keine medizinischen Aussagen oder Diagnosen. " +
                    "Antworte auf Deutsch mit höchstens 5 kurzen Stichpunkten, der wichtigste Befund zuerst."
        }

        fun from(summary: InsightSummary): InsightPrompt = InsightPrompt(
            system = system(summary.language),
            user = summary.text,
        )
    }
}
