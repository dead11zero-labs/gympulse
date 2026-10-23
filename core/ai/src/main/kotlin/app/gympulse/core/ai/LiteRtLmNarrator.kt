package app.gympulse.core.ai

import app.gympulse.core.insights.narration.InsightNarrator
import app.gympulse.core.insights.narration.InsightPrompt
import app.gympulse.core.insights.narration.InsightSummary
import app.gympulse.core.insights.narration.TemplateNarrator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.timeout

/** Die Generierung brach ab, nachdem schon Text gestreamt wurde. Ein Fallback würde Text doppeln. */
class NarrationInterruptedException(cause: Throwable) : Exception("On-device narration interrupted", cause)

/**
 * Formuliert die Zusammenfassung mit dem On-Device-LLM (LiteRT-LM, Gemma 4 E2B) in der App-Sprache.
 *
 * Fallback-Kette: GPU → CPU (im [LlmEngineManager]) → [fallback] (Textbausteine). Auf den Fallback
 * wechselt der Narrator, solange noch kein Text gestreamt wurde: Modell fehlt oder ist beschädigt,
 * zu wenig RAM, Ladefehler, Zeitüberschreitung oder leere Antwort. Bricht der Stream danach ab,
 * kommt [NarrationInterruptedException] aus dem Flow.
 *
 * @param tokenTimeout maximale Pause bis zum ersten Delta (inklusive Laden) und zwischen Deltas.
 * @param onFallback Hook für Diagnose; nie mit Trainingsdaten loggen.
 */
class LiteRtLmNarrator(
    private val engines: LlmEngineManager,
    private val fallback: InsightNarrator = TemplateNarrator(),
    private val tokenTimeout: Duration = 30.seconds,
    private val onFallback: (Throwable?) -> Unit = {},
) : InsightNarrator {

    @OptIn(FlowPreview::class)
    override fun narrate(summary: InsightSummary): Flow<String> {
        if (summary.isEmpty) return fallback.narrate(summary)
        val prompt = InsightPrompt.from(summary)
        return flow {
            var streamed = false
            val llm = engines.withEngine { it.generate(prompt.system, prompt.user) }
                .filter { it.isNotEmpty() }
                // Vor timeout() markieren: timeout() arbeitet über einen Channel, ein Fehler direkt nach
                // dem ersten Delta könnte sonst ankommen, bevor das Delta unten verarbeitet ist.
                .onEach { streamed = true }
                .timeout(tokenTimeout)
                .catch { e ->
                    if (streamed) throw NarrationInterruptedException(e)
                    streamed = true
                    onFallback(e)
                    emitAll(fallback.narrate(summary))
                }
                .onCompletion { cause ->
                    // Leere Antwort ohne Fehler: lieber Textbausteine als eine leere Coach-Karte.
                    if (cause == null && !streamed) {
                        streamed = true
                        onFallback(null)
                        emitAll(fallback.narrate(summary))
                    }
                }
            emitAll(llm)
        }
    }
}
