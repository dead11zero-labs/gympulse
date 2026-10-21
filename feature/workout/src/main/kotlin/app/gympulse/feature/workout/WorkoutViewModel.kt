package app.gympulse.feature.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.data.health.FinishedWorkoutExporter
import app.gympulse.core.data.health.WorkoutHealthReader
import app.gympulse.core.data.repository.ExerciseRepository
import app.gympulse.core.data.repository.PlanRepository
import app.gympulse.core.data.repository.WorkoutRepository
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.SetType
import app.gympulse.core.model.WorkoutSession
import app.gympulse.feature.workout.timer.RestTimer
import app.gympulse.feature.workout.timer.RestTimerSettings
import app.gympulse.feature.workout.timer.RestTimerState
import app.gympulse.feature.workout.timer.Ticker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

/**
 * Active workout. The session state (active, completed, discarded) lives in Room only, so the
 * screen resumes a running session after process death. All writes run one after another
 * through [writeMutex] and always start from the latest stored session.
 */
@HiltViewModel
internal class WorkoutViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    planRepository: PlanRepository,
    exerciseRepository: ExerciseRepository,
    private val restTimer: RestTimer,
    private val restTimerSettings: RestTimerSettings,
    ticker: Ticker,
    time: TimeProvider,
    private val healthExporter: FinishedWorkoutExporter,
    private val healthReader: WorkoutHealthReader,
) : ViewModel() {

    private val writeMutex = Mutex()

    /** Previous sets per exercise id, valid for one session. */
    private val previousSets = MutableStateFlow(PreviousSetsCache(sessionId = null, sets = emptyMap()))

    private val _summary = MutableStateFlow<WorkoutSummary?>(null)

    /** Set after finishing a workout until the dialog is dismissed. */
    val summary: StateFlow<WorkoutSummary?> = _summary.asStateFlow()

    private val catalog: SharedFlow<Map<String, Exercise>> =
        exerciseRepository.observeExercises(Locale.getDefault().language)
            .map { list -> list.associateBy { it.id } }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    val uiState: StateFlow<WorkoutUiState> = combine(
        workoutRepository.observeActiveSession().onEach { session -> session?.let { loadPreviousSets(it) } },
        planRepository.observePlans(),
        catalog,
        previousSets,
    ) { session, plans, exercises, previous ->
        if (session == null) {
            WorkoutUiState.SelectPlan(
                plans = plans
                    .filterNot { it.isArchived }
                    .map { PlanOption(id = it.id, name = it.name, exerciseCount = it.exercises.size) },
            )
        } else {
            val previousForSession = if (previous.sessionId == session.id) previous.sets else emptyMap()
            WorkoutUiState.Active(session.toUi(exercises, previousForSession))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WorkoutUiState.Loading)

    /** Exercises for the picker: favorites first, then alphabetical. */
    val exerciseOptions: StateFlow<List<ExerciseOption>> = catalog
        .map { exercises ->
            exercises.values
                .sortedWith(compareByDescending<Exercise> { it.isFavorite }.thenBy { it.name.lowercase() })
                .map { ExerciseOption(id = it.id, name = it.name, isFavorite = it.isFavorite) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val restTimerState: StateFlow<RestTimerState?> = restTimer.state

    /** Current time for elapsed time and rest countdown; ticks only while the screen is visible. */
    val now: StateFlow<Instant> = ticker.ticks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), time.now())

    init {
        // Started from the dashboard with a plan: start it once (or resume a running session).
        val planId = savedStateHandle.get<String>(KEY_PLAN_ID)
        if (planId != null && savedStateHandle.get<Boolean>(KEY_AUTO_START_HANDLED) != true) {
            savedStateHandle[KEY_AUTO_START_HANDLED] = true
            startWorkout(planId)
        }
    }

    /** Starts a workout from a plan, or an empty one with null; resumes a running session instead. */
    fun startWorkout(planId: String?) = mutate {
        workoutRepository.startOrResumeSession(planId)
    }

    fun onWeightChange(sessionExerciseId: String, setId: String, text: String) {
        val value = WorkoutFormat.parseDecimal(text)
        updateSet(sessionExerciseId, setId) { it.copy(weightKg = value) }
    }

    fun onRepsChange(sessionExerciseId: String, setId: String, text: String) {
        val value = WorkoutFormat.parseInteger(text)
        updateSet(sessionExerciseId, setId) { it.copy(reps = value) }
    }

    fun onDurationChange(sessionExerciseId: String, setId: String, text: String) {
        val value = WorkoutFormat.parseInteger(text)
        updateSet(sessionExerciseId, setId) { it.copy(durationSeconds = value) }
    }

    fun onDistanceChange(sessionExerciseId: String, setId: String, text: String) {
        val value = WorkoutFormat.parseDecimal(text)
        updateSet(sessionExerciseId, setId) { it.copy(distanceMeters = value) }
    }

    /** Ticks a set off (and starts the rest timer) or reopens it. */
    fun toggleSetCompleted(sessionExerciseId: String, setId: String) = mutate {
        val session = workoutRepository.getActiveSession() ?: return@mutate
        val exercise = session.exercises.firstOrNull { it.id == sessionExerciseId } ?: return@mutate
        val set = exercise.sets.firstOrNull { it.id == setId } ?: return@mutate
        val completed = !set.isCompleted
        workoutRepository.saveSet(sessionExerciseId, set.copy(isCompleted = completed, completedAt = null))
        if (completed) {
            restTimer.start(restSecondsFor(exercise.exerciseId))
        }
    }

    fun toggleWarmup(sessionExerciseId: String, setId: String) {
        updateSet(sessionExerciseId, setId) {
            it.copy(setType = if (it.setType == SetType.WARMUP) SetType.WORKING else SetType.WARMUP)
        }
    }

    fun addSet(sessionExerciseId: String) = mutate {
        val session = workoutRepository.getActiveSession() ?: return@mutate
        workoutRepository.addSet(session.id, sessionExerciseId)
    }

    fun deleteSet(setId: String) = mutate {
        workoutRepository.deleteSet(setId)
    }

    fun addExercise(exerciseId: String) = mutate {
        val session = workoutRepository.getActiveSession() ?: return@mutate
        workoutRepository.addExercise(session.id, exerciseId)
    }

    fun removeExercise(sessionExerciseId: String) = mutate {
        workoutRepository.removeExercise(sessionExerciseId)
    }

    fun onNotesChange(sessionExerciseId: String, notes: String) = mutate {
        val session = workoutRepository.getActiveSession() ?: return@mutate
        workoutRepository.updateExerciseNotes(session.id, sessionExerciseId, notes)
    }

    fun skipRest() = restTimer.skip()

    fun adjustRest(deltaSeconds: Int) = restTimer.adjust(deltaSeconds)

    /** Completes the whole session at once; open sets stay stored but do not count. */
    fun finishWorkout() = mutate {
        val session = workoutRepository.getActiveSession() ?: return@mutate
        workoutRepository.finishSession(session.id)
        restTimer.skip()
        val finished = workoutRepository.observeSession(session.id).first()
        val endedAt = finished?.endedAt ?: session.startedAt
        val history = workoutRepository.setHistory(session.exercises.map { it.exerciseId }.distinct())
        val summary = buildWorkoutSummary(
            session = session,
            endedAt = endedAt,
            volumeKg = finished?.totalVolumeKg ?: 0.0,
            catalog = catalog.first(),
            records = PersonalRecordDetector().detect(session.id, history),
        )
        _summary.value = summary
        // Health Connect (opt-in); runs outside the write lock and never blocks the summary.
        viewModelScope.launch { healthExporter.export(session.id) }
        viewModelScope.launch {
            val metrics = healthReader.read(session.startedAt, endedAt) ?: return@launch
            // Only fill in the summary of this session, if it is still shown.
            _summary.update { current -> current?.takeIf { it === summary }?.copy(health = metrics) ?: current }
        }
    }

    fun discardWorkout() = mutate {
        val session = workoutRepository.getActiveSession() ?: return@mutate
        workoutRepository.discardSession(session.id)
        restTimer.skip()
    }

    fun dismissSummary() {
        _summary.value = null
    }

    private fun updateSet(sessionExerciseId: String, setId: String, transform: (SetEntry) -> SetEntry) = mutate {
        val session = workoutRepository.getActiveSession() ?: return@mutate
        val set = session.exercises
            .firstOrNull { it.id == sessionExerciseId }
            ?.sets
            ?.firstOrNull { it.id == setId }
            ?: return@mutate
        val updated = transform(set)
        if (updated != set) {
            workoutRepository.saveSet(sessionExerciseId, updated)
        }
    }

    private suspend fun restSecondsFor(exerciseId: String): Int =
        catalog.replayCache.firstOrNull()?.get(exerciseId)?.defaultRestSeconds
            ?: restTimerSettings.restSeconds.first()

    private suspend fun loadPreviousSets(session: WorkoutSession) {
        val cache = previousSets.value
        val known = if (cache.sessionId == session.id) cache.sets else emptyMap()
        val missing = session.exercises.map { it.exerciseId }.distinct().filterNot { it in known }
        if (missing.isEmpty() && cache.sessionId == session.id) return
        val loaded = missing.associateWith { workoutRepository.previousSets(it, session.id) }
        previousSets.value = PreviousSetsCache(sessionId = session.id, sets = known + loaded)
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch { writeMutex.withLock { block() } }
    }

    private data class PreviousSetsCache(
        val sessionId: String?,
        val sets: Map<String, List<SetEntry>>,
    )

    companion object {
        /** Argument name of [WorkoutRoute.planId] in the saved state. */
        const val KEY_PLAN_ID = "planId"
        private const val KEY_AUTO_START_HANDLED = "autoStartHandled"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
