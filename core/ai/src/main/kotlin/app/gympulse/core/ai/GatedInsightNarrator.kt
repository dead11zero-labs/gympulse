package app.gympulse.core.ai

import app.gympulse.core.insights.narration.InsightNarrator
import app.gympulse.core.insights.narration.InsightSummary
import app.gympulse.core.insights.narration.NarrationChunk
import app.gympulse.core.insights.narration.NarrationSource
import app.gympulse.core.insights.narration.SourceAwareNarrator
import app.gympulse.core.insights.narration.TemplateNarrator
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/** Sagt, ob gerade ein Workout läuft. Dann wird kein Modell geladen (Speicher und Akku gehören dem Training). */
fun interface ActiveWorkoutProbe {
    suspend fun isWorkoutActive(): Boolean
}

/**
 * Wählt pro Aufruf den Narrator: Das Sprachmodell kommt nur zum Zug, wenn
 *  1. der Nutzer den KI-Coach aktiviert hat ([isCoachEnabled]),
 *  2. das Modell vollständig vorliegt und das Gerät es tragen kann ([precheck] liefert `null`),
 *  3. kein Workout läuft ([workoutProbe]) und
 *  4. die Zusammenfassung etwas zu erzählen hat.
 * Sonst formuliert der [template]-Narrator. Fällt das Modell während der Generierung aus, bevor Text
 * kam, übernimmt ebenfalls das Template; die Chunks tragen dann [NarrationSource.TEMPLATE].
 *
 * @param llmFactory baut den Modell-Narrator; der übergebene Hook wird gerufen, sobald er auf das
 *   Template zurückfällt.
 * @param decisionDispatcher für die Prüfung (Dateizugriff, ActivityManager), nicht für das Streamen.
 */
class GatedInsightNarrator(
    private val isCoachEnabled: () -> Boolean,
    private val workoutProbe: ActiveWorkoutProbe,
    private val precheck: () -> UnavailableReason?,
    private val llmFactory: (onFallback: (Throwable?) -> Unit) -> InsightNarrator,
    private val template: InsightNarrator = TemplateNarrator(),
    private val decisionDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SourceAwareNarrator {

    override fun narrateWithSource(summary: InsightSummary): Flow<NarrationChunk> = flow {
        if (summary.isEmpty || !shouldUseModel()) {
            template.narrate(summary).collect { emit(NarrationChunk(it, NarrationSource.TEMPLATE)) }
            return@flow
        }
        var fellBack = false
        llmFactory { fellBack = true }.narrate(summary).collect { delta ->
            emit(NarrationChunk(delta, if (fellBack) NarrationSource.TEMPLATE else NarrationSource.AI))
        }
    }

    private suspend fun shouldUseModel(): Boolean = withContext(decisionDispatcher) {
        if (!isCoachEnabled()) return@withContext false
        if (precheck() != null) return@withContext false
        // Im Zweifel (Fehler beim Lesen) lieber nichts laden.
        try {
            !workoutProbe.isWorkoutActive()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}
