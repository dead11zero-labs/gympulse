package app.gympulse.core.wearsync

import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.WorkoutSession
import app.gympulse.core.wearprotocol.CommandAck
import app.gympulse.core.wearprotocol.PlanSummaryList
import app.gympulse.core.wearprotocol.WearPaths
import app.gympulse.core.wearprotocol.WearProtocol
import app.gympulse.core.wearprotocol.WorkoutSnapshot
import app.gympulse.core.wearprotocol.buildWorkoutSnapshot
import app.gympulse.core.wearprotocol.toPlanSummaryList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Veröffentlicht den Stand des Telefons für die Uhr:
 * - `/workout/active`: [WorkoutSnapshot] der laufenden Session (oder ohne Session), debounced auf
 *   Session, Pause, Quittungen, Katalog und Pausendauer. Jede Veröffentlichung bekommt eine neue,
 *   monotone Version aus dem [WearSyncStore]; unveränderte Inhalte werden nicht erneut geschrieben.
 * - `/plans/summary`: [PlanSummaryList] für die Planauswahl.
 *
 * Läuft nur, solange [Feature.WEAR_APP] aktiv und die Uhr-App auf einer gekoppelten Uhr installiert
 * ist. Wird das Feature abgeschaltet, löscht der Publisher beide Items.
 */
@OptIn(FlowPreview::class)
@Singleton
class WearSnapshotPublisher @Inject constructor(
    private val workout: WearWorkoutPort,
    private val sources: WearSnapshotSources,
    private val dataClient: WearDataClient,
    private val store: WearSyncStore,
    private val clocks: SetClockBook,
    private val featureGate: FeatureGate,
    private val watchAppStatus: WatchAppStatus,
    private val time: TimeProvider,
) {
    /** Zuletzt geschriebener Inhalt (mit Version 0), um doppelte Veröffentlichungen zu sparen. */
    private var lastSnapshot: WorkoutSnapshot? = null
    private var lastPlans: PlanSummaryList? = null

    /** Cache der „Vorher“-Werte je Übung; gilt nur für [previousSessionId]. */
    private var previousSessionId: String? = null
    private val previousCache = HashMap<String, List<SetEntry>>()

    /** Läuft, bis der Aufrufer abbricht. */
    suspend fun run() {
        combine(featureGate.observe(Feature.WEAR_APP), watchAppStatus.state.map { it.installed }) { enabled, installed ->
            enabled to installed
        }
            .distinctUntilChanged()
            .collectLatest { (enabled, installed) ->
                when {
                    !enabled -> clear()
                    installed -> coroutineScope {
                        launch { publishSnapshots() }
                        launch { publishPlans() }
                    }
                    // Keine Uhr-App: nichts zu tun; sobald sie auftaucht, wird alles neu veröffentlicht.
                    else -> Unit
                }
            }
    }

    private suspend fun publishSnapshots() {
        lastSnapshot = null
        val workoutState = combine(workout.activeSession, workout.restEndsAt, store.processed) { session, rest, acks ->
            WorkoutState(session, rest, acks)
        }
        val context = combine(sources.exercises, sources.defaultRestSeconds, clocks.revision) { catalog, rest, _ ->
            catalog to rest
        }
        combine(workoutState, context) { state, (catalog, defaultRest) -> Inputs(state, catalog, defaultRest) }
            .debounce(DEBOUNCE_MILLIS)
            .collect { publishSnapshot(it) }
    }

    private suspend fun publishSnapshot(inputs: Inputs) {
        val session = inputs.state.session
        val content = buildWorkoutSnapshot(
            version = 0,
            session = session,
            catalog = inputs.catalog,
            previousSets = session?.let { previousSets(it) }.orEmpty(),
            restEndsAt = inputs.state.restEndsAt,
            defaultRestSeconds = inputs.defaultRestSeconds,
            setUpdatedAt = session?.let { s -> clocks.updatedAtFor(s.exercises.flatMap { it.sets }) }.orEmpty(),
            acks = inputs.state.acks,
        )
        if (content == lastSnapshot) return
        try {
            val version = store.nextVersion(time.now().toEpochMilli())
            dataClient.put(WearPaths.ACTIVE_WORKOUT, WearProtocol.encodeSnapshot(content.copy(version = version)))
            lastSnapshot = content
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Kein Data Layer (z. B. ohne Play Services): beim nächsten Stand erneut versuchen.
        }
    }

    private suspend fun publishPlans() {
        lastPlans = null
        sources.plans
            .map { it.toPlanSummaryList(Instant.EPOCH) }
            .distinctUntilChanged()
            .debounce(DEBOUNCE_MILLIS)
            .collect { plans ->
                if (plans == lastPlans) return@collect
                try {
                    val stamped = plans.copy(generatedAt = time.now().toEpochMilli())
                    dataClient.put(WearPaths.PLANS_SUMMARY, WearProtocol.encodePlans(stamped), urgent = false)
                    lastPlans = plans
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Siehe publishSnapshot.
                }
            }
    }

    /** „Vorher“-Werte je Übungs-ID; sie ändern sich während einer Session nicht und werden gecacht. */
    private suspend fun previousSets(session: WorkoutSession): Map<String, List<SetEntry>> {
        if (previousSessionId != session.id) {
            previousCache.clear()
            previousSessionId = session.id
        }
        return session.exercises.map { it.exerciseId }.distinct().associateWith { exerciseId ->
            previousCache.getOrPut(exerciseId) { sources.previousSets(exerciseId, session.id) }
        }
    }

    private suspend fun clear() {
        lastSnapshot = null
        lastPlans = null
        for (path in listOf(WearPaths.ACTIVE_WORKOUT, WearPaths.PLANS_SUMMARY)) {
            try {
                dataClient.delete(path)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ohne Data Layer gibt es auch nichts zu löschen.
            }
        }
    }

    private data class WorkoutState(val session: WorkoutSession?, val restEndsAt: Instant?, val acks: List<CommandAck>)

    private data class Inputs(val state: WorkoutState, val catalog: Map<String, Exercise>, val defaultRestSeconds: Int)

    companion object {
        /** Bündelt schnelle Folgen (Drehkrone, mehrere Kommandos), ohne spürbar zu verzögern. */
        const val DEBOUNCE_MILLIS = 300L
    }
}
