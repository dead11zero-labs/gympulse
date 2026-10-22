package app.gympulse.core.workout

import app.gympulse.core.data.health.FinishedWorkoutExporter
import app.gympulse.core.data.health.WorkoutHealthReader
import app.gympulse.core.data.repository.ExerciseRepository
import app.gympulse.core.data.repository.WorkoutRepository
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.SetType
import app.gympulse.core.model.WorkoutSession
import app.gympulse.core.workout.di.ActiveWorkoutScope
import app.gympulse.core.workout.timer.RestTimer
import app.gympulse.core.workout.timer.RestTimerSettings
import app.gympulse.core.workout.timer.RestTimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single write path of the running workout, shared by the workout screen, the notification and
 * (later) the watch. The session state (active, completed, discarded) lives in Room only, so a
 * running session survives process death. All writes run one after another through
 * [writeMutex] and always start from the latest stored session; calls on a missing session,
 * exercise or set are ignored.
 *
 * The controller works without an open screen: the rest timer is persistent and follow-up work
 * of [finishWorkout] (Health Connect) runs in the process-wide [scope].
 */
@Singleton
class ActiveWorkoutController @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val restTimer: RestTimer,
    private val restTimerSettings: RestTimerSettings,
    private val healthExporter: FinishedWorkoutExporter,
    private val healthReader: WorkoutHealthReader,
    private val time: TimeProvider,
    @ActiveWorkoutScope private val scope: CoroutineScope,
) {
    private val writeMutex = Mutex()

    private val _summary = MutableStateFlow<WorkoutSummary?>(null)

    /** Running session from Room, null if none is active. */
    val activeSession: Flow<WorkoutSession?> = workoutRepository.observeActiveSession()

    /** Running rest period; null or finished (see [app.gympulse.core.workout.timer.runningAt]) when there is none. */
    val restTimerState: StateFlow<RestTimerState?> = restTimer.state

    /** Summary of the last finished workout until [dismissSummary]. */
    val summary: StateFlow<WorkoutSummary?> = _summary.asStateFlow()

    /** Starts a workout from a plan, or an empty one with null; resumes a running session instead. */
    suspend fun startWorkout(planId: String?): String = writeMutex.withLock {
        workoutRepository.startOrResumeSession(planId)
    }

    /** Applies [transform] to a set of the active session and stores it if anything changed. */
    suspend fun updateSet(sessionExerciseId: String, setId: String, transform: (SetEntry) -> SetEntry) = write {
        val session = workoutRepository.getActiveSession() ?: return@write
        val set = session.exercises
            .firstOrNull { it.id == sessionExerciseId }
            ?.sets
            ?.firstOrNull { it.id == setId }
            ?: return@write
        val updated = transform(set)
        if (updated != set) {
            workoutRepository.saveSet(sessionExerciseId, updated)
        }
    }

    /** Ticks a set off (and starts the rest timer) or reopens it. */
    suspend fun toggleSetCompleted(sessionExerciseId: String, setId: String) = write {
        val session = workoutRepository.getActiveSession() ?: return@write
        val exercise = session.exercises.firstOrNull { it.id == sessionExerciseId } ?: return@write
        val set = exercise.sets.firstOrNull { it.id == setId } ?: return@write
        val completed = !set.isCompleted
        workoutRepository.saveSet(sessionExerciseId, set.copy(isCompleted = completed, completedAt = null))
        if (completed) {
            restTimer.start(restSecondsFor(exercise.exerciseId))
        }
    }

    suspend fun toggleWarmup(sessionExerciseId: String, setId: String) =
        updateSet(sessionExerciseId, setId) {
            it.copy(setType = if (it.setType == SetType.WARMUP) SetType.WORKING else SetType.WARMUP)
        }

    suspend fun addSet(sessionExerciseId: String) = write {
        val session = workoutRepository.getActiveSession() ?: return@write
        workoutRepository.addSet(session.id, sessionExerciseId)
    }

    suspend fun deleteSet(setId: String) = write {
        workoutRepository.deleteSet(setId)
    }

    suspend fun addExercise(exerciseId: String) = write {
        val session = workoutRepository.getActiveSession() ?: return@write
        workoutRepository.addExercise(session.id, exerciseId)
    }

    suspend fun removeExercise(sessionExerciseId: String) = write {
        workoutRepository.removeExercise(sessionExerciseId)
    }

    suspend fun updateNotes(sessionExerciseId: String, notes: String) = write {
        val session = workoutRepository.getActiveSession() ?: return@write
        workoutRepository.updateExerciseNotes(session.id, sessionExerciseId, notes)
    }

    fun skipRest() = restTimer.skip()

    fun adjustRest(deltaSeconds: Int) = restTimer.adjust(deltaSeconds)

    /**
     * Completes the whole session at once; open sets stay stored but do not count. Builds the
     * summary with personal records, publishes it in [summary] and starts the Health Connect
     * export (opt-in) and the read of the session's health values in the background; the values
     * are added to the summary once loaded.
     *
     * @return the summary, or null if no session was active
     */
    suspend fun finishWorkout(): WorkoutSummary? = finish(durationSeconds = null)

    /**
     * Estimated duration for "mark everything as done": the longer of the elapsed time and
     * [FinishAllPlanner.estimatedSeconds] with the configured rest. Null without an active session.
     */
    suspend fun proposeFinishAll(): FinishAllProposal? = writeMutex.withLock {
        val session = workoutRepository.getActiveSession() ?: return@withLock null
        val restSeconds = restTimerSettings.restSeconds.first()
        val counted = session.countedSets()
        FinishAllProposal(
            durationSeconds = FinishAllPlanner.durationSeconds(
                elapsedSeconds = Duration.between(session.startedAt, time.now()).seconds,
                setCount = counted.size,
                restSeconds = restSeconds,
            ),
            setCount = counted.size,
            openSets = session.exercises.sumOf { exercise -> exercise.sets.count { !it.isCompleted } },
        )
    }

    /**
     * Marks every open set as done, spreads their timestamps over a window of [durationSeconds]
     * ending now, moves the start time to the start of that window and finishes like
     * [finishWorkout].
     */
    suspend fun finishAll(durationSeconds: Long): WorkoutSummary? = finish(durationSeconds)

    /** With [durationSeconds] the sets are completed first and the start time is moved to end - duration. */
    private suspend fun finish(durationSeconds: Long?): WorkoutSummary? = writeMutex.withLock {
        val session = workoutRepository.getActiveSession() ?: return@withLock null
        var startedAt: Instant? = null
        if (durationSeconds != null) {
            val windowEnd = time.now()
            val windowStart = windowEnd.minusSeconds(durationSeconds)
            completeOpenSets(session, windowStart, durationSeconds, windowEnd)
            startedAt = windowStart
        }
        workoutRepository.finishSession(session.id, startedAt = startedAt)
        restTimer.skip()
        val finished = workoutRepository.observeSession(session.id).first()
        val endedAt = finished?.endedAt ?: session.startedAt
        val history = workoutRepository.setHistory(session.exercises.map { it.exerciseId }.distinct())
        val summary = buildWorkoutSummary(
            session = finished ?: session,
            endedAt = endedAt,
            volumeKg = finished?.totalVolumeKg ?: 0.0,
            catalog = catalog(),
            records = PersonalRecordDetector().detect(session.id, history),
        )
        _summary.value = summary
        // Health Connect (opt-in); runs outside the write lock and never blocks the summary.
        scope.launch { healthExporter.export(session.id) }
        scope.launch {
            val metrics = healthReader.read(finished?.startedAt ?: session.startedAt, endedAt) ?: return@launch
            // Only fill in the summary of this session, if it is still shown.
            _summary.update { current -> current?.takeIf { it === summary }?.copy(health = metrics) ?: current }
        }
        summary
    }

    private suspend fun completeOpenSets(
        session: WorkoutSession,
        windowStart: Instant,
        durationSeconds: Long,
        windowEnd: Instant,
    ) {
        val counted = session.countedSets()
        val times = FinishAllPlanner.completionTimes(windowStart, durationSeconds, counted.size)
        counted.forEachIndexed { index, (sessionExerciseId, set) ->
            if (!set.isCompleted) {
                workoutRepository.saveSet(sessionExerciseId, set.copy(isCompleted = true, completedAt = times[index]))
            }
        }
        // Open sets without values stay empty; they are only ticked off.
        session.exercises.forEach { exercise ->
            exercise.sets.filter { !it.isCompleted && !it.hasValues() }.forEach { set ->
                workoutRepository.saveSet(exercise.id, set.copy(isCompleted = true, completedAt = windowEnd))
            }
        }
    }

    suspend fun discardWorkout() = write {
        val session = workoutRepository.getActiveSession() ?: return@write
        workoutRepository.discardSession(session.id)
        restTimer.skip()
    }

    fun dismissSummary() {
        _summary.value = null
    }

    private suspend fun write(block: suspend () -> Unit) {
        writeMutex.withLock { block() }
    }

    private suspend fun restSecondsFor(exerciseId: String): Int =
        exerciseRepository.observeExercise(exerciseId, locale()).first()?.defaultRestSeconds
            ?: restTimerSettings.restSeconds.first()

    /** Exercises in the device language, as shown in the workout screen. */
    private suspend fun catalog() =
        exerciseRepository.observeExercises(locale()).first().associateBy { it.id }

    private fun locale(): String = Locale.getDefault().language
}
