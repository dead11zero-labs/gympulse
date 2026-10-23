package app.gympulse.core.wearsync

import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.workout.ActiveWorkoutForeground
import app.gympulse.core.workout.RestAlertPolicy
import app.gympulse.core.wearsync.di.WearSyncScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Einstieg der Wear-Anbindung am Telefon; die Application ruft [start] in `onCreate` auf. Das gilt
 * auch, wenn erst der [WearCommandListenerService] den Prozess geweckt hat: Dann läuft der Publisher
 * mit an und die Quittungen erreichen die Uhr.
 */
@Singleton
class WearSync @Inject constructor(
    private val publisher: WearSnapshotPublisher,
    private val processor: WearCommandProcessor,
    private val workout: WearWorkoutPort,
    private val foreground: ActiveWorkoutForeground,
    private val clocks: SetClockBook,
    @WearSyncScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    /** Idempotent. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { publisher.run() }
        // Kommandos, die angekommen sind, während kein Prozess lief und der Listener nicht zum Zug kam.
        scope.launch { processor.processPending() }
        scope.launch {
            // Endet die Session (egal wo), gehören Feld-Uhren und eine Plan-B-Benachrichtigung weg.
            workout.activeSession.map { it?.id }.distinctUntilChanged().collect { id ->
                if (id == null) {
                    clocks.clear()
                    foreground.clearNotificationOnly()
                }
            }
        }
    }
}

/**
 * Pausen-Vibration nur auf der Uhr, solange sie verbunden ist (Jörg); sonst wie bisher am Telefon.
 *
 * „Verbunden“ reicht allein nicht: Die Uhr muss den Timer auch tatsächlich mitführen. Das meldet sie
 * über [WatchAppState.restAlertsOnWatch], solange ihr Workout-Dienst läuft. Konnte der Dienst nicht
 * starten (z. B. Hintergrund-Startverbot) oder ist die Uhr weg, vibriert das Telefon. Im Zweifel
 * (keine Antwort der Play Services) ebenfalls.
 */
class WearRestAlertPolicy @Inject constructor(
    private val featureGate: FeatureGate,
    private val watchAppStatus: WatchAppStatus,
) : RestAlertPolicy {
    override suspend fun alertOnPhone(): Boolean {
        if (!featureGate.isEnabled(Feature.WEAR_APP)) return true
        val state = withTimeoutOrNull(STATUS_TIMEOUT_MILLIS) { watchAppStatus.state.first() } ?: return true
        return !(state.connected && state.restAlertsOnWatch)
    }

    private companion object {
        const val STATUS_TIMEOUT_MILLIS = 2_000L
    }
}
