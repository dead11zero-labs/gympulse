package app.gympulse.core.wearsync

import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.wearprotocol.AckResult
import app.gympulse.core.wearprotocol.AdjustRest
import app.gympulse.core.wearprotocol.CommandAck
import app.gympulse.core.wearprotocol.CompleteSet
import app.gympulse.core.wearprotocol.Discard
import app.gympulse.core.wearprotocol.Finish
import app.gympulse.core.wearprotocol.HeartRateSummary
import app.gympulse.core.wearprotocol.SessionCommand
import app.gympulse.core.wearprotocol.SkipRest
import app.gympulse.core.wearprotocol.StartWorkout
import app.gympulse.core.wearprotocol.UpdateSet
import app.gympulse.core.wearprotocol.WatchWorkoutReducer
import app.gympulse.core.wearprotocol.WearCommand
import app.gympulse.core.wearprotocol.WearPaths
import app.gympulse.core.wearprotocol.WearProtocol
import app.gympulse.core.workout.ActiveWorkoutForeground
import app.gympulse.core.workout.SetEditResult
import app.gympulse.core.workout.SetEditStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wendet Kommandos der Uhr (`/cmd/<uuid>`) über den ActiveWorkoutController an.
 *
 * - Idempotent: Verarbeitete Kommando-IDs stehen im [WearSyncStore] (überlebt Prozess-Neustarts);
 *   ein bekanntes Kommando wird nicht erneut angewendet, nur sein Item gelöscht.
 * - Jedes Kommando wird quittiert ([CommandAck] im nächsten Snapshot), danach wird sein DataItem gelöscht.
 * - Reihenfolge innerhalb eines Stapels nach (issuedAt, commandId), wie im Reducer der Uhr.
 * - Konflikte am selben Satz: [WatchWorkoutReducer.commandWins] gegen die Feld-Uhr aus [SetClockBook].
 *   Verliert ein Kommando, bleibt der Telefon-Stand und das Kommando gilt trotzdem als verarbeitet.
 * - Kommandos auf eine beendete oder fremde Session werden ignoriert und als solche quittiert.
 * - Puls-Zusammenfassungen ([HeartRateSummary]) gelten der Session von vorhin und werden auch ohne laufende Session gespeichert.
 * - Ist [Feature.WEAR_APP] aus, werden Kommandos verworfen, ohne sie anzuwenden.
 */
@Singleton
class WearCommandProcessor @Inject constructor(
    private val workout: WearWorkoutPort,
    private val sources: WearSnapshotSources,
    private val dataClient: WearDataClient,
    private val store: WearSyncStore,
    private val clocks: SetClockBook,
    private val heartRates: WatchHeartRateStore,
    private val foreground: ActiveWorkoutForeground,
    private val featureGate: FeatureGate,
    private val time: TimeProvider,
) {
    private val mutex = Mutex()

    /** Liest liegengebliebene Kommando-Items (z. B. nach einem Neustart) und verarbeitet sie. */
    suspend fun processPending() {
        val items = try {
            dataClient.items(WearPaths.COMMAND_PREFIX)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        process(items)
    }

    /** Verarbeitet [items]; Items mit anderem Pfad als `/cmd/<uuid>` werden übergangen. */
    suspend fun process(items: List<WearDataItem>) = mutex.withLock {
        val enabled = featureGate.isEnabled(Feature.WEAR_APP)
        val known = store.processed.first().mapTo(HashSet()) { it.commandId.lowercase() }
        val incoming = items.mapNotNull { item ->
            val pathId = WearPaths.commandIdOf(item.path) ?: return@mapNotNull null
            Incoming(item.path, pathId, item.data?.let(WearProtocol::decodeCommand))
        }
        val ordered = incoming.sortedWith(compareBy<Incoming>({ it.command?.issuedAt ?: Long.MIN_VALUE }, { it.pathId }))
        for (entry in ordered) {
            val command = entry.command
            val id = command?.commandId?.lowercase() ?: entry.pathId
            if (enabled && id !in known) {
                val result = if (command == null) AckResult.INVALID else applySafely(command)
                store.remember(CommandAck(command?.commandId ?: entry.pathId, result))
                known += id
            }
            delete(entry.path)
        }
    }

    private suspend fun applySafely(command: WearCommand): AckResult = try {
        apply(command)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Ein kaputtes Kommando darf die übrigen nicht blockieren.
        AckResult.INVALID
    }

    private suspend fun apply(command: WearCommand): AckResult = when (command) {
        is StartWorkout -> start(command)
        is HeartRateSummary -> recordHeartRate(command)
        is SessionCommand -> {
            val session = workout.activeSession.first()
            when {
                session == null -> AckResult.SESSION_ENDED
                session.id != command.sessionId -> AckResult.SESSION_MISMATCH
                else -> applyToSession(command)
            }
        }
    }

    private suspend fun applyToSession(command: SessionCommand): AckResult = when (command) {
        is CompleteSet -> completeSet(command)
        is UpdateSet -> updateSet(command)
        is AdjustRest -> {
            workout.adjustRest(command.deltaSeconds)
            AckResult.APPLIED
        }
        is SkipRest -> {
            workout.skipRest()
            AckResult.APPLIED
        }
        is Finish -> {
            val finished = workout.finish()
            ended()
            if (finished) AckResult.APPLIED else AckResult.SESSION_ENDED
        }
        is Discard -> {
            workout.discard()
            ended()
            AckResult.APPLIED
        }
    }

    /**
     * Puls-Zusammenfassung der Uhr: kommt nach dem Workout, deshalb ohne Prüfung auf eine laufende
     * Session. Unplausible Werte werden abgelehnt.
     */
    private suspend fun recordHeartRate(command: HeartRateSummary): AckResult {
        if (!command.isPlausible) return AckResult.INVALID
        heartRates.record(WatchHeartRate(command.sessionId, command.averageBpm, command.maxBpm))
        return AckResult.APPLIED
    }

    /** Startet das Workout wie die Handy-UI; läuft schon eins, wird es fortgesetzt. */
    private suspend fun start(command: StartWorkout): AckResult {
        if (workout.activeSession.first() != null) return AckResult.APPLIED
        val planId = command.planId
        if (planId != null && !sources.planExists(planId)) return AckResult.INVALID
        val sessionId = workout.startWorkout(planId)
        val session = workout.activeSession.first()?.takeIf { it.id == sessionId }
        // Plan B steckt im Foreground: ohne erlaubten Foreground Service nur eine Benachrichtigung.
        foreground.show(session?.title, session?.startedAt ?: time.now())
        return AckResult.APPLIED
    }

    private suspend fun completeSet(command: CompleteSet): AckResult {
        val completedAt = if (command.completed) watchTime(command.issuedAt) else null
        val result = workout.setSetCompleted(command.sessionId, command.setId, command.completed, completedAt) { set ->
            WatchWorkoutReducer.commandWins(command.issuedAt, clocks.clockOf(set))
        }
        return result.toAck(command.issuedAt)
    }

    private suspend fun updateSet(command: UpdateSet): AckResult {
        val weight = command.weightKg
        val reps = command.reps
        if (weight != null && (!weight.isFinite() || weight < 0.0)) return AckResult.INVALID
        if (reps != null && reps < 0) return AckResult.INVALID
        if (weight == null && reps == null) return AckResult.APPLIED
        val result = workout.editSet(command.sessionId, command.setId) { set ->
            if (WatchWorkoutReducer.commandWins(command.issuedAt, clocks.clockOf(set))) {
                set.copy(weightKg = weight ?: set.weightKg, reps = reps ?: set.reps)
            } else {
                null
            }
        }
        return result.toAck(command.issuedAt)
    }

    private fun SetEditResult.toAck(issuedAt: Long): AckResult = when (status) {
        SetEditStatus.CHANGED -> {
            set?.let { clocks.record(it, issuedAt) }
            AckResult.APPLIED
        }
        // Verloren gegen eine neuere Änderung am Telefon: verarbeitet, der Snapshot zeigt den Telefon-Stand.
        SetEditStatus.UNCHANGED, SetEditStatus.REJECTED -> AckResult.APPLIED
        SetEditStatus.SET_NOT_FOUND -> AckResult.INVALID
        SetEditStatus.NO_SESSION -> AckResult.SESSION_ENDED
        SetEditStatus.OTHER_SESSION -> AckResult.SESSION_MISMATCH
    }

    private fun ended() {
        clocks.clear()
        foreground.clearNotificationOnly()
    }

    /** Zeit der Uhr, höchstens jetzt (eine vorgehende Uhr soll keine Sätze in der Zukunft abhaken). */
    private fun watchTime(issuedAt: Long): Instant {
        val now = time.now()
        val issued = Instant.ofEpochMilli(issuedAt)
        return if (issued.isAfter(now)) now else issued
    }

    private suspend fun delete(path: String) {
        try {
            dataClient.delete(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Bleibt das Item liegen, verhindert die gemerkte ID ein zweites Anwenden.
        }
    }

    private class Incoming(val path: String, val pathId: String, val command: WearCommand?)
}
