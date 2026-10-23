package app.gympulse.wear.workout

import app.gympulse.core.wearprotocol.WearCommands
import app.gympulse.wear.data.WatchWorkoutRepository
import app.gympulse.wear.data.WearAppScope
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Verdrahtet [WorkoutHeartRateRecorder] mit dem Zustand der Uhr und dem Versand ans Telefon.
 * Läuft im Prozess-Scope, damit die Zusammenfassung auch dann rausgeht, wenn der
 * [WatchWorkoutService] mit dem Workout-Ende schon beendet ist.
 */
@Singleton
class HeartRateRecording @Inject constructor(
    private val repository: WatchWorkoutRepository,
    monitor: HeartRateMonitor,
    @WearAppScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)
    private val recorder = WorkoutHeartRateRecorder(
        monitor = monitor,
        sink = HeartRateSummarySink { sessionId, stats ->
            scope.launch {
                repository.send(
                    WearCommands.heartRateSummary(
                        sessionId = sessionId,
                        averageBpm = stats.averageBpm,
                        maxBpm = stats.maxBpm,
                        sampleCount = stats.sampleCount,
                    ),
                )
            }
        },
    )

    /** Idempotent; die Application ruft das in `onCreate` auf. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { recorder.run(repository.state) }
    }

    /**
     * Versucht eine gescheiterte Messung erneut, z. B. nach erteilter Berechtigung, beim Öffnen der
     * App oder sobald der Workout-Dienst im Vordergrund läuft.
     */
    fun resume() {
        scope.launch { recorder.resume() }
    }
}
