package app.gympulse.core.insights.narration

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Womit ein Text entstanden ist. Davon hängt der Hinweis "KI-generiert" ab. */
enum class NarrationSource {
    /** Das lokale Sprachmodell hat den Text formuliert. */
    AI,

    /** Textbausteine ohne Modell (Modell fehlt, Fehler, Workout aktiv oder Feature aus). */
    TEMPLATE,
}

/** Ein Delta zum Anhängen samt Herkunft. Alle Chunks eines Streams haben dieselbe [source]. */
data class NarrationChunk(val delta: String, val source: NarrationSource)

/**
 * [InsightNarrator], der zusätzlich meldet, ob der Text vom Modell stammt oder aus Textbausteinen.
 * Fällt die KI aus, bevor Text gestreamt wurde, kommen die Chunks mit [NarrationSource.TEMPLATE].
 */
interface SourceAwareNarrator : InsightNarrator {
    fun narrateWithSource(summary: InsightSummary): Flow<NarrationChunk>

    override fun narrate(summary: InsightSummary): Flow<String> = narrateWithSource(summary).map { it.delta }
}
