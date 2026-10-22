package app.gympulse.core.workout.timer

import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.workout.di.ActiveWorkoutScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Running rest period. The state only stores the end time, so the remaining time is correct
 * even if the app was in the background and no tick happened in between.
 */
data class RestTimerState(
    val endsAt: Instant,
    /** Total length including adjustments, used for the progress indicator. */
    val totalSeconds: Int,
) {
    /** Remaining whole seconds, rounded up so "0" only shows when the rest is really over. */
    fun remainingSeconds(now: Instant): Int {
        val millis = Duration.between(now, endsAt).toMillis()
        if (millis <= 0) return 0
        return ((millis + 999) / 1000).toInt()
    }

    fun isFinished(now: Instant): Boolean = !now.isBefore(endsAt)

    /** Fraction of the rest period that is still left, between 0 and 1. */
    fun remainingFraction(now: Instant): Float {
        if (totalSeconds <= 0) return 0f
        val millis = Duration.between(now, endsAt).toMillis().coerceAtLeast(0)
        return (millis / (totalSeconds * 1000f)).coerceIn(0f, 1f)
    }
}

/** Returns the state if the rest period is still running at [now], otherwise null. */
fun RestTimerState?.runningAt(now: Instant): RestTimerState? = this?.takeIf { !it.isFinished(now) }

/**
 * App-wide rest timer, shared by the workout screen, the mini bar, the notification and (later)
 * the watch. Changes apply to [state] immediately; every state is also written to [store], so a
 * running rest period survives process death. On creation the stored period is restored if it
 * is still running and nothing changed the timer in the meantime.
 */
@Singleton
class RestTimer @Inject constructor(
    private val time: TimeProvider,
    private val store: RestTimerStore,
    @ActiveWorkoutScope scope: CoroutineScope,
) {
    private val lock = Any()
    private val _state = MutableStateFlow<RestTimerState?>(null)
    val state: StateFlow<RestTimerState?> = _state.asStateFlow()

    /** Set by the first change; a later restore must not overwrite it. */
    private var changed = false

    init {
        scope.launch {
            val stored = try {
                store.read()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            synchronized(lock) {
                if (!changed) _state.value = stored.runningAt(time.now())
            }
            // StateFlow is conflated: the last state is always written, intermediate ones may be skipped.
            _state.collect { value ->
                try {
                    store.write(value)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Persisting is best effort; the in-memory timer keeps working.
                }
            }
        }
    }

    /** Starts (or restarts) the rest period; a non-positive duration stops the timer. */
    fun start(seconds: Int) = change {
        if (seconds <= 0) {
            null
        } else {
            RestTimerState(endsAt = time.now().plusSeconds(seconds.toLong()), totalSeconds = seconds)
        }
    }

    /**
     * Moves the end time by [deltaSeconds] (e.g. +15 / -15). If the rest would end in the past,
     * the timer stops.
     */
    fun adjust(deltaSeconds: Int) = change { current ->
        val now = time.now()
        val running = current.runningAt(now) ?: return@change null
        val newEnd = running.endsAt.plusSeconds(deltaSeconds.toLong())
        if (!newEnd.isAfter(now)) {
            null
        } else {
            RestTimerState(endsAt = newEnd, totalSeconds = (running.totalSeconds + deltaSeconds).coerceAtLeast(1))
        }
    }

    fun skip() = change { null }

    private inline fun change(transform: (RestTimerState?) -> RestTimerState?) {
        synchronized(lock) {
            changed = true
            _state.value = transform(_state.value)
        }
    }

    companion object {
        const val ADJUST_STEP_SECONDS = 15
    }
}
