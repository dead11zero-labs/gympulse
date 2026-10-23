package app.gympulse.wear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.wearprotocol.CommandAck
import app.gympulse.core.wearprotocol.ExerciseSnapshot
import app.gympulse.core.wearprotocol.SessionSnapshot
import app.gympulse.core.wearprotocol.SetSnapshot
import app.gympulse.core.wearprotocol.WearTrackingType
import app.gympulse.wear.data.WatchWorkoutRepository
import app.gympulse.wear.data.WearConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Welcher Wert des aktuellen Satzes von der Drehkrone verändert wird. */
enum class EditField { WEIGHT, REPS }

/** Noch nicht gesendete Änderung am aktuellen Satz; null heißt "unverändert". */
data class SetDraft(val setId: String, val weightKg: Double? = null, val reps: Int? = null)

data class WorkoutUiState(
    val session: SessionSnapshot? = null,
    val selectedExerciseId: String? = null,
    val field: EditField = EditField.WEIGHT,
    val draft: SetDraft? = null,
    val connection: WearConnection = WearConnection.UNKNOWN,
    val notices: List<CommandAck> = emptyList(),
    val incompatibleProtocol: Boolean = false,
) {
    val exercises: List<ExerciseSnapshot> get() = session?.exercises.orEmpty()

    /** Gewählte Übung; ohne Auswahl die erste mit offenen Sätzen. */
    val exerciseIndex: Int
        get() = exercises.indexOfFirst { it.sessionExerciseId == selectedExerciseId }.takeIf { it >= 0 }
            ?: exercises.indexOfFirst { exercise -> exercise.sets.any { !it.isCompleted } }.takeIf { it >= 0 }
            ?: 0

    val exercise: ExerciseSnapshot? get() = exercises.getOrNull(exerciseIndex)

    /** Erster offener Satz der Übung, sonst der letzte (alle erledigt). */
    val currentSet: SetSnapshot?
        get() = exercise?.sets?.let { sets -> sets.firstOrNull { !it.isCompleted } ?: sets.lastOrNull() }

    val setPosition: Int get() = exercise?.sets?.indexOf(currentSet)?.plus(1) ?: 0
    val setCount: Int get() = exercise?.sets?.size ?: 0
    val exerciseDone: Boolean get() = exercise?.sets?.all { it.isCompleted } == true
    val allDone: Boolean get() = exercises.isNotEmpty() && exercises.all { e -> e.sets.all { it.isCompleted } }

    val trackingType: WearTrackingType get() = exercise?.trackingType ?: WearTrackingType.WEIGHT_REPS
    val usesWeight: Boolean get() = trackingType == WearTrackingType.WEIGHT_REPS
    val usesReps: Boolean get() = trackingType == WearTrackingType.WEIGHT_REPS || trackingType == WearTrackingType.REPS

    private val activeDraft: SetDraft? get() = draft?.takeIf { it.setId == currentSet?.setId }

    /**
     * Angezeigtes Gewicht: Entwurf, sonst Wert des Satzes, sonst "Vorher", sonst der zuletzt abgehakte
     * Satz der Übung (damit der nächste Satz sinnvoll vorbelegt ist).
     */
    val displayWeightKg: Double
        get() {
            val set = currentSet ?: return 0.0
            return activeDraft?.weightKg ?: set.weightKg ?: set.previous?.weightKg
                ?: lastCompletedSet()?.weightKg ?: 0.0
        }

    val displayReps: Int
        get() {
            val set = currentSet ?: return 0
            return activeDraft?.reps ?: set.reps ?: set.previous?.reps ?: lastCompletedSet()?.reps ?: 0
        }

    private fun lastCompletedSet(): SetSnapshot? = exercise?.sets?.lastOrNull { it.isCompleted }
}

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val repository: WatchWorkoutRepository,
) : ViewModel() {

    private val selectedExerciseId = MutableStateFlow<String?>(null)
    private val field = MutableStateFlow(EditField.WEIGHT)
    private val draft = MutableStateFlow<SetDraft?>(null)
    private var flushJob: Job? = null
    private val flushMutex = Mutex()

    val uiState: StateFlow<WorkoutUiState> = combine(
        repository.state,
        repository.connection,
        selectedExerciseId,
        field,
        draft,
    ) { state, connection, selected, editField, currentDraft ->
        build(state.session, connection, selected, editField, currentDraft, state.notices, state.incompatibleProtocol)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WorkoutUiState())

    private fun current(): WorkoutUiState {
        val state = repository.state.value
        return build(
            state.session,
            repository.connection.value,
            selectedExerciseId.value,
            field.value,
            draft.value,
            state.notices,
            state.incompatibleProtocol,
        )
    }

    private fun build(
        session: SessionSnapshot?,
        connection: WearConnection,
        selected: String?,
        editField: EditField,
        currentDraft: SetDraft?,
        notices: List<CommandAck>,
        incompatible: Boolean,
    ) = WorkoutUiState(session, selected, editField, currentDraft, connection, notices, incompatible)

    fun selectField(newField: EditField) {
        field.value = newField
    }

    /** Eine Raste der Krone = [steps] Schritte (positiv: erhöhen). Gesendet wird gebündelt nach kurzer Ruhe. */
    fun rotate(steps: Int) {
        if (steps == 0) return
        val state = current()
        val set = state.currentSet ?: return
        val existing = state.draft?.takeIf { it.setId == set.setId }
        val next = when (state.field) {
            EditField.WEIGHT -> {
                if (!state.usesWeight) return
                val weight = (state.displayWeightKg + steps * WEIGHT_STEP_KG).coerceIn(0.0, MAX_WEIGHT_KG)
                (existing ?: SetDraft(set.setId)).copy(weightKg = weight)
            }
            EditField.REPS -> {
                if (!state.usesReps) return
                val reps = (state.displayReps + steps).coerceIn(0, MAX_REPS)
                (existing ?: SetDraft(set.setId)).copy(reps = reps)
            }
        }
        draft.value = next
        scheduleFlush()
    }

    /** Hakt den aktuellen Satz ab (oder nimmt ihn zurück, wenn schon erledigt) und springt ggf. zur nächsten Übung. */
    fun completeSet() {
        viewModelScope.launch {
            flushJob?.cancel()
            val state = current()
            val set = state.currentSet ?: return@launch
            val completing = !set.isCompleted
            if (completing) {
                // Was der Satz anzeigt, soll auch gespeichert werden (z. B. aus "Vorher" vorbelegte Werte).
                val needsWeight = state.usesWeight && set.weightKg == null
                val needsReps = state.usesReps && set.reps == null
                if (needsWeight || needsReps) {
                    draft.update { old ->
                        val same = old?.takeIf { it.setId == set.setId }
                        SetDraft(
                            setId = set.setId,
                            weightKg = same?.weightKg ?: state.displayWeightKg.takeIf { needsWeight },
                            reps = same?.reps ?: state.displayReps.takeIf { needsReps },
                        )
                    }
                }
            }
            flushDraft()
            repository.completeSet(set.setId, completed = completing)
            if (completing) advanceIfExerciseDone(state.exercise?.sessionExerciseId)
        }
    }

    fun selectExercise(sessionExerciseId: String) = changeExercise { sessionExerciseId }

    fun nextExercise() = changeExercise { state -> state.exercises.getOrNull(state.exerciseIndex + 1)?.sessionExerciseId }

    fun previousExercise() = changeExercise { state -> state.exercises.getOrNull(state.exerciseIndex - 1)?.sessionExerciseId }

    fun adjustRest(deltaSeconds: Int) {
        viewModelScope.launch { repository.adjustRest(deltaSeconds) }
    }

    fun skipRest() {
        viewModelScope.launch { repository.skipRest() }
    }

    fun finish() {
        viewModelScope.launch {
            flushJob?.cancel()
            flushDraft()
            repository.finish()
        }
    }

    fun discard() {
        viewModelScope.launch {
            flushJob?.cancel()
            draft.value = null
            repository.discard()
        }
    }

    fun dismissNotices() = repository.dismissNotices()

    private fun changeExercise(target: (WorkoutUiState) -> String?) {
        viewModelScope.launch {
            flushJob?.cancel()
            flushDraft()
            val id = target(current()) ?: return@launch
            selectedExerciseId.value = id
        }
    }

    private fun advanceIfExerciseDone(exerciseId: String?) {
        val state = current()
        val index = state.exercises.indexOfFirst { it.sessionExerciseId == exerciseId }
        val exercise = state.exercises.getOrNull(index) ?: return
        if (exercise.sets.any { !it.isCompleted }) return
        val next = state.exercises.drop(index + 1).firstOrNull { e -> e.sets.any { !it.isCompleted } }
            ?: state.exercises.take(index).firstOrNull { e -> e.sets.any { !it.isCompleted } }
        if (next != null) selectedExerciseId.value = next.sessionExerciseId
    }

    private fun scheduleFlush() {
        flushJob?.cancel()
        flushJob = viewModelScope.launch {
            delay(FLUSH_DELAY_MS)
            flushDraft()
        }
    }

    /** Sendet den Entwurf als ein Kommando. Nicht abbrechbar, damit ein Wechsel mittendrin nichts halb sendet. */
    private suspend fun flushDraft() = withContext(NonCancellable) {
        flushMutex.withLock {
            val pending = draft.value ?: return@withContext
            repository.updateSet(pending.setId, pending.weightKg, pending.reps)
            // Wurde währenddessen weitergedreht, bleibt der neuere Entwurf stehen.
            draft.update { if (it == pending) null else it }
        }
    }

    companion object {
        const val WEIGHT_STEP_KG = 2.5
        const val MAX_WEIGHT_KG = 999.0
        const val MAX_REPS = 999
        const val FLUSH_DELAY_MS = 500L
    }
}
