package app.gympulse.wear.workout

import app.gympulse.core.wearprotocol.SessionSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Wanduhr der Uhr (Unix-Zeit in ms); austauschbar, damit der Tracker ohne Gerät testbar bleibt. */
fun interface WearClock {
    fun nowMillis(): Long
}

/** Was die Ongoing Activity bzw. Notification zeigt. Zeiten in Unix-Millisekunden. */
data class WorkoutOngoingState(
    val sessionId: String,
    val title: String?,
    val startedAt: Long,
    /** Ende der laufenden Pause; null, wenn keine Pause läuft. */
    val restEndsAt: Long?,
)

/**
 * Seiteneffekte des Workout-Dienstes. Die Implementierung ist der [WatchWorkoutService]
 * (Notification, Wake Lock, Vibration); Tests nutzen einen Fake.
 */
interface WatchRestEffects {
    /** Notification und Ongoing Activity auf [state] aktualisieren. */
    fun show(state: WorkoutOngoingState)

    /** CPU bis [endsAt] wach halten, damit das Pausenende auch bei ausgeschaltetem Display pünktlich kommt. */
    fun keepAwakeUntil(endsAt: Long)

    fun releaseAwake()

    /** Haptik bei Pausenende. */
    fun restFinished()

    /** Keine aktive Session mehr: Dienst beenden. */
    fun stop()
}

/**
 * Führt den Pausen-Timer der Uhr aus `restEndsAt` des (optimistischen) Snapshots mit.
 *
 * - Keine Session: [WatchRestEffects.stop].
 * - Session ohne laufende Pause: anzeigen, Wake Lock freigeben.
 * - Laufende Pause: anzeigen, wach halten, bis `restEndsAt` warten, dann einmal vibrieren und
 *   wieder die Trainingszeit zeigen. Ändert sich die Pause vorher (±15 s, Überspringen, neuer
 *   Satz), wird das Warten abgebrochen und neu berechnet.
 * - Eine Pause, die beim Eintreffen schon vorbei ist (z. B. verspäteter Snapshot), vibriert nicht.
 *
 * Gewartet wird mit [delay]; in Tests steuert der virtuelle Scheduler von `runTest` die Zeit.
 */
class WatchRestTracker(
    private val clock: WearClock,
    private val effects: WatchRestEffects,
) {
    suspend fun run(sessions: Flow<SessionSnapshot?>) {
        sessions
            .map { it?.toOngoingState() }
            .distinctUntilChanged()
            .collectLatest { state -> track(state) }
    }

    private suspend fun track(state: WorkoutOngoingState?) {
        if (state == null) {
            effects.releaseAwake()
            effects.stop()
            return
        }
        val endsAt = state.restEndsAt
        val now = clock.nowMillis()
        if (endsAt == null || endsAt <= now) {
            effects.releaseAwake()
            effects.show(state.copy(restEndsAt = null))
            return
        }
        effects.show(state)
        effects.keepAwakeUntil(endsAt)
        delay(endsAt - now)
        effects.restFinished()
        effects.show(state.copy(restEndsAt = null))
        effects.releaseAwake()
    }
}

internal fun SessionSnapshot.toOngoingState() = WorkoutOngoingState(
    sessionId = sessionId,
    title = title,
    startedAt = startedAt,
    restEndsAt = restEndsAt,
)

/**
 * Rechnet einen Unix-Zeitpunkt in die `elapsedRealtime`-Zeitbasis um, die Chronometer und
 * Ongoing-Activity-Status erwarten.
 */
fun epochToElapsedRealtime(epochMillis: Long, nowEpochMillis: Long, nowElapsedMillis: Long): Long =
    nowElapsedMillis + (epochMillis - nowEpochMillis)
