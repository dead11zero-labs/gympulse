package app.gympulse.core.ai

import app.gympulse.core.insights.narration.InsightNarrator
import app.gympulse.core.insights.narration.InsightPrompt
import app.gympulse.core.insights.narration.InsightSummary
import app.gympulse.core.insights.narration.TemplateNarrator
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/** Die Generierung brach ab, nachdem schon Text gestreamt wurde. Ein Fallback würde Text doppeln. */
class NarrationInterruptedException(cause: Throwable) : Exception("On-device narration interrupted", cause)

/** Kein Delta innerhalb der erlaubten Pause (inklusive Laden des Modells). */
class TokenTimeoutException(timeout: Duration) : Exception("No token within $timeout")

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

    override fun narrate(summary: InsightSummary): Flow<String> {
        if (summary.isEmpty) return fallback.narrate(summary)
        val prompt = InsightPrompt.from(summary)
        return flow {
            var streamed = false
            val failure: Throwable? = try {
                engines.withEngine { it.generate(prompt.system, prompt.user) }
                    .collectWithGapTimeout(tokenTimeout) { delta ->
                        if (delta.isNotEmpty()) {
                            streamed = true
                            // Fehler des Empfängers nicht als Modellfehler behandeln (Exception-Transparenz).
                            try {
                                emit(delta)
                            } catch (e: Throwable) {
                                throw DownstreamFailure(e)
                            }
                        }
                    }
                null
            } catch (e: DownstreamFailure) {
                throw e.cause ?: e
            } catch (e: CancellationException) {
                // Abbruch durch den Aufrufer weiterreichen, sonst als Modellfehler werten.
                currentCoroutineContext().ensureActive()
                e
            } catch (e: Exception) {
                e
            }
            if (streamed) {
                if (failure != null) throw NarrationInterruptedException(failure)
            } else {
                // Fehler oder leere Antwort: lieber Textbausteine als eine leere Coach-Karte.
                onFallback(failure)
                emitAll(fallback.narrate(summary))
            }
        }
    }

    private class DownstreamFailure(cause: Throwable) : Exception(cause)
}

/**
 * Sammelt im selben Coroutine (kein Channel, Reihenfolge von Deltas und Fehlern bleibt exakt) und
 * bricht mit [TokenTimeoutException] ab, wenn zwischen zwei Elementen mehr als [timeout] vergeht.
 */
internal suspend fun <T> Flow<T>.collectWithGapTimeout(timeout: Duration, action: suspend (T) -> Unit) =
    coroutineScope {
        val ticks = MutableStateFlow(0L)
        val watchdog = launch {
            ticks.collectLatest {
                delay(timeout)
                throw TokenTimeoutException(timeout)
            }
        }
        try {
            collect { value ->
                ticks.value++
                action(value)
                ticks.value++
            }
        } finally {
            watchdog.cancel()
        }
    }
