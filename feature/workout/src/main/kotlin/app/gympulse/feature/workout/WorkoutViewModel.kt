package app.gympulse.feature.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.data.repository.ExerciseRepository
import app.gympulse.core.data.repository.PlanRepository
import app.gympulse.core.data.repository.WorkoutRepository
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.WorkoutSession
import app.gympulse.core.workout.ActiveWorkoutController
import app.gympulse.core.workout.FinishAllProposal
import app.gympulse.core.workout.WorkoutSummary
import app.gympulse.core.wearsync.WatchHeartRate
import app.gympulse.core.wearsync.WatchHeartRateStore
import app.gympulse.core.workout.timer.RestTimerState
import app.gympulse.feature.workout.timer.Ticker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

/**
 * Active workout screen. Reads the session from Room and delegates every write (and the rest
 * timer) to the [ActiveWorkoutController], which serializes them and also serves the
 * notification and, later, the watch.
 */
@HiltViewModel
internal class WorkoutViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val controller: ActiveWorkoutController,
    private val workoutRepository: WorkoutRepository,
    planRepository: PlanRepository,
    exerciseRepository: ExerciseRepository,
    watchHeartRates: WatchHeartRateStore,
    ticker: Ticker,
    time: TimeProvider,
) : ViewModel() {

    /** Previous sets per exercise id, valid for one session. */
    private val previousSets = MutableStateFlow(PreviousSetsCache(sessionId = null, sets = emptyMap()))

    /** Set after finishing a workout until the dialog is dismissed. */
    val summary: StateFlow<WorkoutSummary?> = controller.summary

    /** Avg/max heart rate the watch reports after the workout; null until it arrives (or without a watch). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val watchHeartRate: StateFlow<WatchHeartRate?> = controller.summary
        .map { it?.sessionId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else watchHeartRates.observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val _finishAllProposal = MutableStateFlow<FinishAllProposal?>(null)

    /** Estimated duration shown in the "mark everything as done" dialog; null while no dialog is open. */
    val finishAllProposal: StateFlow<FinishAllProposal?> = _finishAllProposal.asStateFlow()

    private val catalog: SharedFlow<Map<String, Exercise>> =
        exerciseRepository.observeExercises(Locale.getDefault().language)
            .map { list -> list.associateBy { it.id } }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    val uiState: StateFlow<WorkoutUiState> = combine(
        controller.activeSession.onEach { session -> session?.let { loadPreviousSets(it) } },
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

    val restTimerState: StateFlow<RestTimerState?> = controller.restTimerState

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
    fun startWorkout(planId: String?) = dispatch { controller.startWorkout(planId) }

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

    /** Cardio input is in minutes (decimals allowed); sets store whole seconds. */
    fun onDurationMinutesChange(sessionExerciseId: String, setId: String, text: String) {
        val seconds = WorkoutFormat.parseDecimal(text)?.let { Math.round(it * SECONDS_PER_MINUTE).toInt() }
        updateSet(sessionExerciseId, setId) { it.copy(durationSeconds = seconds) }
    }

    fun onDistanceChange(sessionExerciseId: String, setId: String, text: String) {
        val value = WorkoutFormat.parseDecimal(text)
        updateSet(sessionExerciseId, setId) { it.copy(distanceMeters = value) }
    }

    /** Ticks a set off (and starts the rest timer) or reopens it. */
    fun toggleSetCompleted(sessionExerciseId: String, setId: String) =
        dispatch { controller.toggleSetCompleted(sessionExerciseId, setId) }

    fun toggleWarmup(sessionExerciseId: String, setId: String) =
        dispatch { controller.toggleWarmup(sessionExerciseId, setId) }

    fun addSet(sessionExerciseId: String) = dispatch { controller.addSet(sessionExerciseId) }

    fun deleteSet(setId: String) = dispatch { controller.deleteSet(setId) }

    fun addExercise(exerciseId: String) = dispatch { controller.addExercise(exerciseId) }

    fun removeExercise(sessionExerciseId: String) = dispatch { controller.removeExercise(sessionExerciseId) }

    fun onNotesChange(sessionExerciseId: String, notes: String) =
        dispatch { controller.updateNotes(sessionExerciseId, notes) }

    fun skipRest() = controller.skipRest()

    fun adjustRest(deltaSeconds: Int) = controller.adjustRest(deltaSeconds)

    /** Completes the whole session at once; open sets stay stored but do not count. */
    fun finishWorkout() = dispatch { controller.finishWorkout() }

    /** Computes the estimated duration for "mark everything as done" and opens the confirmation. */
    fun requestFinishAll() = dispatch {
        controller.proposeFinishAll()?.let { _finishAllProposal.value = it }
    }

    fun dismissFinishAll() {
        _finishAllProposal.value = null
    }

    /**
     * Marks every open set as done, spreads their timestamps over a window of [durationMinutes]
     * ending now and finishes the session as usual.
     */
    fun confirmFinishAll(durationMinutes: Int) {
        _finishAllProposal.value = null
        dispatch { controller.finishAll(durationMinutes.coerceAtLeast(1) * SECONDS_PER_MINUTE) }
    }

    fun discardWorkout() = dispatch { controller.discardWorkout() }

    fun dismissSummary() = controller.dismissSummary()

    override fun onCleared() {
        // The summary belongs to this screen, as before the controller existed.
        controller.dismissSummary()
    }

    private fun updateSet(sessionExerciseId: String, setId: String, transform: (SetEntry) -> SetEntry) =
        dispatch { controller.updateSet(sessionExerciseId, setId, transform) }

    private suspend fun loadPreviousSets(session: WorkoutSession) {
        val cache = previousSets.value
        val known = if (cache.sessionId == session.id) cache.sets else emptyMap()
        val missing = session.exercises.map { it.exerciseId }.distinct().filterNot { it in known }
        if (missing.isEmpty() && cache.sessionId == session.id) return
        val loaded = missing.associateWith { workoutRepository.previousSets(it, session.id) }
        previousSets.value = PreviousSetsCache(sessionId = session.id, sets = known + loaded)
    }

    private fun dispatch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
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
        private const val SECONDS_PER_MINUTE = 60L
    }
}
