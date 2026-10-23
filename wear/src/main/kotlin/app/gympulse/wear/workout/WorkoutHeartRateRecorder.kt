package app.gympulse.wear.workout

import app.gympulse.core.wearprotocol.Discard
import app.gympulse.core.wearprotocol.WatchWorkoutState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Nimmt die Puls-Zusammenfassung eines beendeten Workouts entgegen und schickt sie ans Telefon. */
fun interface HeartRateSummarySink {
    /** Darf nicht blockieren; Fehler beim Senden behandelt die Outbox. */
    fun deliver(sessionId: String, stats: HeartRateStats)
}

/**
 * Misst den Puls, solange eine Session läuft, und meldet Ø/Max erst danach.
 *
 * - Neue Session: Aggregator leeren, Messung starten.
 * - Session weg (auf Uhr oder Telefon beendet): Messung stoppen, Ø/Max an [sink] geben, außer die
 *   Uhr hat das Workout selbst verworfen oder es lagen zu wenige Messwerte vor.
 * - Während des Workouts geht nichts ans Telefon; der Live-Puls bleibt auf der Uhr.
 * - Schlägt der Start fehl (Berechtigung verweigert, Sensor belegt), läuft das Workout ohne Puls
 *   weiter; [resume] versucht es später erneut.
 */
class WorkoutHeartRateRecorder(
    private val monitor: HeartRateMonitor,
    private val sink: HeartRateSummarySink,
    private val aggregator: HeartRateAggregator = HeartRateAggregator(),
) {
    @Volatile
    private var activeSessionId: String? = null

    /** Läuft, bis der Aufrufer abbricht. */
    suspend fun run(states: Flow<WatchWorkoutState>) = coroutineScope {
        launch {
            monitor.reading.collect { reading ->
                if (reading is HeartRateReading.Bpm && activeSessionId != null) aggregator.add(reading.value)
            }
        }
        states.collect { onState(it) }
    }

    /** Versucht die Messung erneut zu starten, falls eine Session läuft; sonst ohne Wirkung. */
    suspend fun resume() {
        if (activeSessionId != null) monitor.start()
    }

    private suspend fun onState(state: WatchWorkoutState) {
        val id = state.session?.sessionId
        val previous = activeSessionId
        if (id == previous) return
        if (previous != null) {
            val discarded = state.pending.any { it is Discard && it.sessionId == previous }
            end(previous, discarded)
        }
        activeSessionId = id
        if (id != null) {
            aggregator.reset()
            monitor.start()
        }
    }

    private fun end(sessionId: String, discarded: Boolean) {
        monitor.stop()
        val stats = aggregator.stats()
        aggregator.reset()
        if (!discarded && stats != null) sink.deliver(sessionId, stats)
    }
}
