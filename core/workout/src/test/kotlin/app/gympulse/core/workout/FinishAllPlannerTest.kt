package app.gympulse.core.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FinishAllPlannerTest {
    private val start = Instant.parse("2026-10-19T10:00:00Z")

    @Test
    fun estimate_isWorkTimePlusRestBetweenSets() {
        // 10 sets: 10 x 45 s + 9 x 90 s.
        assertEquals(1_260L, FinishAllPlanner.estimatedSeconds(setCount = 10, restSeconds = 90))
        assertEquals(45L, FinishAllPlanner.estimatedSeconds(setCount = 1, restSeconds = 90))
        assertEquals(375L, FinishAllPlanner.estimatedSeconds(setCount = 3, restSeconds = 120))
    }

    @Test
    fun estimate_isZeroWithoutSets() {
        assertEquals(0L, FinishAllPlanner.estimatedSeconds(setCount = 0, restSeconds = 90))
        assertEquals(0L, FinishAllPlanner.estimatedSeconds(setCount = -1, restSeconds = 90))
    }

    @Test
    fun estimate_fallsBackToDefaultRestWhenNotConfigured() {
        assertEquals(
            FinishAllPlanner.estimatedSeconds(setCount = 4, restSeconds = FinishAllPlanner.DEFAULT_REST_SECONDS),
            FinishAllPlanner.estimatedSeconds(setCount = 4, restSeconds = 0),
        )
    }

    @Test
    fun duration_isTheLongerOfElapsedAndEstimate() {
        assertEquals(1_260L, FinishAllPlanner.durationSeconds(elapsedSeconds = 300, setCount = 10, restSeconds = 90))
        assertEquals(3_000L, FinishAllPlanner.durationSeconds(elapsedSeconds = 3_000, setCount = 10, restSeconds = 90))
        assertEquals(45L, FinishAllPlanner.durationSeconds(elapsedSeconds = -5, setCount = 1, restSeconds = 90))
    }

    @Test
    fun completionTimes_areEvenlySpreadAndEndAtTheWindowEnd() {
        val times = FinishAllPlanner.completionTimes(start, durationSeconds = 420, slots = 3)

        assertEquals(listOf(start.plusSeconds(140), start.plusSeconds(280), start.plusSeconds(420)), times)
    }

    @Test
    fun completionTimes_stayInsideTheWindowAndAscend() {
        val times = FinishAllPlanner.completionTimes(start, durationSeconds = 1_000, slots = 7)

        assertEquals(7, times.size)
        assertTrue(times.zipWithNext().all { (a, b) -> a.isBefore(b) })
        assertTrue(times.all { !it.isBefore(start) && !it.isAfter(start.plusSeconds(1_000)) })
        assertEquals(start.plusSeconds(1_000), times.last())
    }

    @Test
    fun completionTimes_emptyWithoutSlots() {
        assertTrue(FinishAllPlanner.completionTimes(start, 600, 0).isEmpty())
    }
}
