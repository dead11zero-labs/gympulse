package app.gympulse.core.insights.narration

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList

/**
 * Formuliert eine [InsightSummary] als Text. Implementierungen: `TemplateNarrator` (Textbausteine,
 * ohne Modell) und später ein On-Device-LLM. Der Narrator berechnet nichts, er formuliert nur.
 */
interface InsightNarrator {
    /**
     * Streamt den Text in Teilen (Deltas zum Anhängen). Fehler kommen als Exception aus dem Flow, damit
     * der Aufrufer auf den nächsten Narrator der Fallback-Kette wechseln kann.
     */
    fun narrate(summary: InsightSummary): Flow<String>
}

/** Sammelt den gesamten Stream zu einem Text. */
suspend fun InsightNarrator.narrateToString(summary: InsightSummary): String =
    narrate(summary).toList().joinToString("")
