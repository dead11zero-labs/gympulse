package app.gympulse.core.workout

import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.WorkoutSession
import java.time.Instant

/** Proposal for "mark everything as done": the duration the session will be finished with. */
data class FinishAllProposal(
    val durationSeconds: Long,
    /** Sets that count for the estimate: completed ones and open ones carrying values. */
    val setCount: Int,
    val openSets: Int,
)

/**
 * Plausible duration for a session whose sets were all entered afterwards. Pure functions, no
 * clock or database access.
 */
object FinishAllPlanner {
    const val WORK_SECONDS_PER_SET = 45L
    const val DEFAULT_REST_SECONDS = 90

    /** Sets x work time plus one rest between each pair of sets. */
    fun estimatedSeconds(setCount: Int, restSeconds: Int): Long {
        if (setCount <= 0) return 0
        val rest = if (restSeconds > 0) restSeconds else DEFAULT_REST_SECONDS
        return setCount * WORK_SECONDS_PER_SET + (setCount - 1).toLong() * rest
    }

    /** The longer of the really elapsed time and the estimate. */
    fun durationSeconds(elapsedSeconds: Long, setCount: Int, restSeconds: Int): Long =
        maxOf(elapsedSeconds.coerceAtLeast(0), estimatedSeconds(setCount, restSeconds))

    /**
     * Evenly spread completion times: slot k of [slots] ends at start + duration * (k + 1) / slots,
     * so the last set finishes exactly at the end of the window.
     */
    fun completionTimes(windowStart: Instant, durationSeconds: Long, slots: Int): List<Instant> {
        if (slots <= 0) return emptyList()
        val durationMillis = durationSeconds * MILLIS_PER_SECOND
        return List(slots) { index ->
            windowStart.plusMillis(durationMillis * (index + 1) / slots)
        }
    }

    private const val MILLIS_PER_SECOND = 1_000L
}

/** Sets that count for a finished session: completed ones and open ones with values, with their exercise id. */
fun WorkoutSession.countedSets(): List<Pair<String, SetEntry>> =
    exercises.flatMap { exercise -> exercise.sets.map { exercise.id to it } }
        .filter { (_, set) -> set.isCompleted || set.hasValues() }
