package app.gympulse.wear.data

import app.gympulse.core.wearprotocol.PlanSummaryList
import app.gympulse.core.wearprotocol.SessionSnapshot
import app.gympulse.core.wearprotocol.WatchWorkoutReducer
import app.gympulse.core.wearprotocol.WatchWorkoutState
import app.gympulse.core.wearprotocol.WearCommand
import app.gympulse.core.wearprotocol.WearCommands
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Einzige Quelle für den Workout-Zustand auf der Uhr.
 *
 * - Snapshots des Telefons laufen durch den [WatchWorkoutReducer]; die UI liest [state] bzw. [session].
 * - Lokale Kommandos wirken sofort (optimistisch) und gehen in die Outbox, also als DataItem an den
 *   [WearChannel]. Sie bleiben in `state.pending`, bis ein Snapshot sie quittiert.
 * - Schlägt das Senden fehl oder kehrt die Verbindung zurück, werden die offenen Kommandos in
 *   Reihenfolge erneut abgelegt. Die Kommando-ID macht das idempotent: das Telefon wendet jede ID nur einmal an.
 */
@Singleton
class WatchWorkoutRepository @Inject constructor(
    private val channel: WearChannel,
    @WearAppScope scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(WatchWorkoutState())
    private val _plans = MutableStateFlow<PlanSummaryList?>(null)
    private val _connection = MutableStateFlow(WearConnection.UNKNOWN)

    /** Kommando-IDs, deren Ablegen im Channel gescheitert ist. Nur unter [outboxLock] ändern. */
    private val unsent = LinkedHashSet<String>()
    private val outboxLock = Mutex()

    val state: StateFlow<WatchWorkoutState> = _state.asStateFlow()
    val plans: StateFlow<PlanSummaryList?> = _plans.asStateFlow()
    val connection: StateFlow<WearConnection> = _connection.asStateFlow()

    /** Ansicht des laufenden Workouts (optimistisch); null, wenn keines läuft. */
    val session: Flow<SessionSnapshot?> = state.map { it.session }.distinctUntilChanged()

    init {
        scope.launch {
            channel.snapshots.collect { snapshot -> _state.update { WatchWorkoutReducer.onSnapshot(it, snapshot) } }
        }
        scope.launch { channel.plans.collect { _plans.value = it } }
        scope.launch {
            channel.connection.collect { connection ->
                _connection.value = connection
                if (connection == WearConnection.CONNECTED) flushOutbox()
            }
        }
    }

    /** Wendet [command] sofort an und legt es in die Outbox. Ein bekanntes oder schon quittiertes Kommando wird ignoriert. */
    suspend fun send(command: WearCommand) {
        var accepted = false
        _state.update { current ->
            val next = WatchWorkoutReducer.onLocalCommand(current, command)
            accepted = next !== current
            next
        }
        if (!accepted) return
        outboxLock.withLock {
            deliver(command)
            // Ein früherer Fehlschlag soll nicht bis zum nächsten Reconnect warten, wenn es jetzt wieder klappt.
            if (command.commandId !in unsent) {
                for (retry in _state.value.pending) if (retry.commandId in unsent) deliver(retry)
            }
        }
    }

    suspend fun startWorkout(planId: String?, now: Long = System.currentTimeMillis()) =
        send(WearCommands.startWorkout(planId, now))

    suspend fun completeSet(setId: String, completed: Boolean = true, now: Long = System.currentTimeMillis()) =
        withSession { send(WearCommands.completeSet(it, setId, completed, now)) }

    suspend fun updateSet(
        setId: String,
        weightKg: Double? = null,
        reps: Int? = null,
        now: Long = System.currentTimeMillis(),
    ) = withSession { send(WearCommands.updateSet(it, setId, weightKg, reps, now)) }

    suspend fun adjustRest(deltaSeconds: Int, now: Long = System.currentTimeMillis()) =
        withSession { send(WearCommands.adjustRest(it, deltaSeconds, now)) }

    suspend fun skipRest(now: Long = System.currentTimeMillis()) =
        withSession { send(WearCommands.skipRest(it, now)) }

    suspend fun finish(now: Long = System.currentTimeMillis()) =
        withSession { send(WearCommands.finish(it, now)) }

    suspend fun discard(now: Long = System.currentTimeMillis()) =
        withSession { send(WearCommands.discard(it, now)) }

    fun dismissNotices() = _state.update(WatchWorkoutReducer::dismissNotices)

    private suspend inline fun withSession(block: (sessionId: String) -> Unit) {
        val sessionId = _state.value.session?.sessionId ?: return
        block(sessionId)
    }

    /** Nach einem Reconnect alle offenen Kommandos in Reihenfolge erneut ablegen (gleiche ID, kein Doppeleffekt). */
    private suspend fun flushOutbox() = outboxLock.withLock {
        for (command in _state.value.pending) deliver(command)
    }

    /** Legt [command] ab, sofern es noch offen ist. Fehler werden gemerkt statt geworfen. */
    private suspend fun deliver(command: WearCommand) {
        if (_state.value.pending.none { it.commandId == command.commandId }) {
            unsent.remove(command.commandId)
            return
        }
        try {
            channel.sendCommand(command)
            unsent.remove(command.commandId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            unsent.add(command.commandId)
        }
    }
}
