package app.gympulse.core.wearsync

import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.SessionExercise
import app.gympulse.core.model.SessionStatus
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.SetType
import app.gympulse.core.model.TrackingType
import app.gympulse.core.model.WorkoutPlan
import app.gympulse.core.model.WorkoutSession
import app.gympulse.core.wearprotocol.CommandAck
import app.gympulse.core.wearprotocol.WearCommand
import app.gympulse.core.wearprotocol.WearPaths
import app.gympulse.core.wearprotocol.WearProtocol
import app.gympulse.core.workout.ActiveWorkoutForeground
import app.gympulse.core.workout.SetEditResult
import app.gympulse.core.workout.SetEditStatus
import app.gympulse.core.workout.WorkoutForegroundMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class FakeTime(var instant: Instant = Instant.ofEpochMilli(10_000_000)) : TimeProvider {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = ZoneOffset.UTC
    fun advanceMillis(millis: Long) {
        instant = instant.plusMillis(millis)
    }
}

/** Data Layer im Speicher; merkt sich jede Schreib- und Löschoperation. */
class FakeWearDataClient : WearDataClient {
    val items = LinkedHashMap<String, ByteArray>()
    val puts = mutableListOf<Pair<String, ByteArray>>()
    val deletes = mutableListOf<String>()
    var failing = false

    override suspend fun put(path: String, data: ByteArray, urgent: Boolean) {
        if (failing) error("no data layer")
        items[path] = data
        puts += path to data
    }

    override suspend fun delete(path: String) {
        if (failing) error("no data layer")
        items.remove(path)
        deletes += path
    }

    override suspend fun items(pathPrefix: String): List<WearDataItem> {
        if (failing) error("no data layer")
        return items.filterKeys { it.startsWith(pathPrefix) }.map { (path, data) -> WearDataItem(path, data) }
    }

    fun putCommand(command: WearCommand): WearDataItem {
        val item = WearDataItem(WearPaths.command(command.commandId), WearProtocol.encodeCommand(command))
        items[item.path] = item.data!!
        return item
    }

    fun snapshots() = puts.filter { it.first == WearPaths.ACTIVE_WORKOUT }.map { WearProtocol.decodeSnapshot(it.second)!! }

    fun plans() = puts.filter { it.first == WearPaths.PLANS_SUMMARY }.map { WearProtocol.decodePlans(it.second)!! }
}

/**
 * Controller im Speicher mit denselben Regeln wie der echte (Session-Prüfung, Konfliktregel im
 * Lock, `updated_at` = Telefonzeit beim Speichern, Pause beim Abhaken).
 */
class FakeWorkoutPort(private val time: FakeTime, private val restSeconds: Int = 90) : WearWorkoutPort {
    override val activeSession = MutableStateFlow<WorkoutSession?>(null)
    override val restEndsAt = MutableStateFlow<Instant?>(null)
    val started = mutableListOf<String?>()
    var finished = 0
    var discarded = 0
    var adjusted = 0

    override suspend fun startWorkout(planId: String?): String {
        activeSession.value?.let { return it.id }
        started += planId
        val session = session(id = "session-${started.size}", planId = planId, startedAt = time.now())
        activeSession.value = session
        return session.id
    }

    override suspend fun editSet(sessionId: String, setId: String, transform: (SetEntry) -> SetEntry?): SetEditResult =
        edit(sessionId, setId) { set ->
            val updated = transform(set)
            when {
                updated == null -> SetEditStatus.REJECTED to set
                updated == set -> SetEditStatus.UNCHANGED to set
                else -> SetEditStatus.CHANGED to updated
            }
        }

    override suspend fun setSetCompleted(
        sessionId: String,
        setId: String,
        completed: Boolean,
        completedAt: Instant?,
        accept: (SetEntry) -> Boolean,
    ): SetEditResult = edit(sessionId, setId) { set ->
        when {
            !accept(set) -> SetEditStatus.REJECTED to set
            set.isCompleted == completed -> SetEditStatus.UNCHANGED to set
            else -> {
                if (completed) restEndsAt.value = (completedAt ?: time.now()).plusSeconds(restSeconds.toLong())
                SetEditStatus.CHANGED to set.copy(isCompleted = completed, completedAt = if (completed) completedAt ?: time.now() else null)
            }
        }
    }

    private fun edit(sessionId: String, setId: String, block: (SetEntry) -> Pair<SetEditStatus, SetEntry>): SetEditResult {
        val session = activeSession.value ?: return SetEditResult(SetEditStatus.NO_SESSION)
        if (session.id != sessionId) return SetEditResult(SetEditStatus.OTHER_SESSION)
        val set = session.exercises.flatMap { it.sets }.firstOrNull { it.id == setId }
            ?: return SetEditResult(SetEditStatus.SET_NOT_FOUND)
        val (status, result) = block(set)
        if (status != SetEditStatus.CHANGED) return SetEditResult(status, set)
        val stored = result.copy(updatedAt = time.now())
        replaceSet(stored)
        return SetEditResult(status, stored)
    }

    /** Änderung direkt am Telefon (Handy-UI). */
    fun phoneEdit(setId: String, transform: (SetEntry) -> SetEntry) {
        val set = activeSession.value!!.exercises.flatMap { it.sets }.first { it.id == setId }
        replaceSet(transform(set).copy(updatedAt = time.now()))
    }

    fun set(setId: String): SetEntry = activeSession.value!!.exercises.flatMap { it.sets }.first { it.id == setId }

    private fun replaceSet(stored: SetEntry) {
        activeSession.update { current ->
            current?.copy(
                exercises = current.exercises.map { exercise ->
                    exercise.copy(sets = exercise.sets.map { if (it.id == stored.id) stored else it })
                },
            )
        }
    }

    override fun adjustRest(deltaSeconds: Int) {
        adjusted += 1
        restEndsAt.update { it?.plusSeconds(deltaSeconds.toLong()) }
    }

    override fun skipRest() {
        restEndsAt.value = null
    }

    override suspend fun finish(): Boolean {
        if (activeSession.value == null) return false
        finished += 1
        activeSession.value = null
        restEndsAt.value = null
        return true
    }

    override suspend fun discard() {
        discarded += 1
        activeSession.value = null
        restEndsAt.value = null
    }
}

class FakeSources : WearSnapshotSources {
    override val exercises = MutableStateFlow(mapOf(SQUAT to exercise(SQUAT, "Kniebeuge", rest = 180)))
    override val plans = MutableStateFlow(emptyList<WorkoutPlan>())
    override val defaultRestSeconds = MutableStateFlow(90)
    val previous = mutableMapOf<String, List<SetEntry>>()
    var previousCalls = 0

    override suspend fun previousSets(exerciseId: String, currentSessionId: String): List<SetEntry> {
        previousCalls += 1
        return previous[exerciseId].orEmpty()
    }

    override suspend fun planExists(planId: String): Boolean = plans.value.any { it.id == planId && !it.isArchived }
}

class InMemoryWearSyncStore : WearSyncStore {
    override val processed = MutableStateFlow(emptyList<CommandAck>())
    private var version = 0L

    override suspend fun remember(ack: CommandAck) {
        processed.update { current ->
            if (current.any { it.commandId == ack.commandId }) current else (current + ack).takeLast(WearSyncStore.MAX_REMEMBERED)
        }
    }

    override suspend fun nextVersion(nowMillis: Long): Long {
        version = maxOf(version + 1, nowMillis)
        return version
    }
}

class FakeForeground(var mode: WorkoutForegroundMode = WorkoutForegroundMode.FOREGROUND_SERVICE) : ActiveWorkoutForeground {
    val shown = mutableListOf<String?>()
    var cleared = 0

    override fun show(title: String?, startedAt: Instant): WorkoutForegroundMode {
        shown += title
        return mode
    }

    override fun clearNotificationOnly() {
        cleared += 1
    }
}

class FakeFeatureGate(enabled: Boolean = true) : FeatureGate {
    val wearApp = MutableStateFlow(enabled)
    override fun isEnabled(feature: Feature): Boolean = feature != Feature.WEAR_APP || wearApp.value
    override fun observe(feature: Feature): Flow<Boolean> = if (feature == Feature.WEAR_APP) wearApp else MutableStateFlow(true)
}

class FakeWatchAppStatus(state: WatchAppState = WatchAppState(installed = true, connected = true)) : WatchAppStatus {
    override val state = MutableStateFlow(state)
}

const val SQUAT = "squat"
const val SESSION_ID = "session-1"
const val EXERCISE_ID = "se1"

fun exercise(id: String, name: String, rest: Int? = null) = Exercise(
    id = id, slug = null, name = name, category = ExerciseCategory.STRENGTH, equipmentType = EquipmentType.BARBELL,
    trackingType = TrackingType.WEIGHT_REPS, bodyweightFactor = null, primaryMuscles = emptyList(),
    secondaryMuscles = emptyList(), instructions = emptyList(), imageUrls = emptyList(), notes = null,
    isCustom = false, isFavorite = false, defaultRestSeconds = rest, equipmentId = null,
)

fun entry(id: String, index: Int, weight: Double? = 100.0, reps: Int? = 5, updatedAt: Instant? = Instant.ofEpochMilli(1_000)) = SetEntry(
    id = id, setIndex = index, setType = SetType.WORKING, weightKg = weight, reps = reps, durationSeconds = null,
    distanceMeters = null, rpe = null, isCompleted = false, completedAt = null, updatedAt = updatedAt,
)

fun session(
    id: String = SESSION_ID,
    planId: String? = null,
    startedAt: Instant = Instant.ofEpochMilli(500),
    sets: List<SetEntry> = listOf(entry("k1", 0), entry("k2", 1)),
) = WorkoutSession(
    id = id, planId = planId, title = planId?.let { "Plan $it" }, startedAt = startedAt, endedAt = null,
    status = SessionStatus.ACTIVE, bodyweightKg = null, rating = null, notes = null, totalVolumeKg = null,
    exercises = listOf(
        SessionExercise(
            id = EXERCISE_ID, exerciseId = SQUAT, sortOrder = 0, supersetGroup = null, notes = null,
            equipmentSettingsSnapshot = emptyMap(), sets = sets,
        ),
    ),
)
