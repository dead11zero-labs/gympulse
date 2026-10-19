package app.gympulse.feature.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Test

class DashboardCalculationsTest {
    private val zone = ZoneId.of("UTC")

    // 2026-10-19 ist ein Montag.
    private val monday = LocalDate.of(2026, 10, 19)

    private fun at(date: LocalDate): Instant = date.atTime(12, 0).atZone(zone).toInstant()

    @Test
    fun percentChangeComparesWithPreviousWeek() {
        assertEquals(12, percentChange(current = 1120.0, previous = 1000.0))
        assertEquals(-50, percentChange(current = 500.0, previous = 1000.0))
        assertEquals(0, percentChange(current = 1000.0, previous = 1000.0))
        assertEquals(-100, percentChange(current = 0.0, previous = 1000.0))
    }

    @Test
    fun percentChangeIsNullWithoutPreviousVolume() {
        assertNull(percentChange(current = 800.0, previous = 0.0))
        assertNull(percentChange(current = 0.0, previous = 0.0))
    }

    @Test
    fun goalProgressIsClampedAndHandlesInvalidGoal() {
        assertEquals(0f, goalProgress(0, 3), 0.0001f)
        assertEquals(2f / 3f, goalProgress(2, 3), 0.0001f)
        assertEquals(1f, goalProgress(5, 3), 0.0001f)
        assertEquals(0f, goalProgress(2, 0), 0.0001f)
    }

    @Test
    fun weekStartRespectsConfiguredDay() {
        val wednesday = LocalDate.of(2026, 10, 21)
        assertEquals(monday, weekStartOf(wednesday, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 10, 18), weekStartOf(wednesday, DayOfWeek.SUNDAY))
        assertEquals(monday, weekStartOf(monday, DayOfWeek.MONDAY))
    }

    @Test
    fun sessionsAreGroupedPerWeek() {
        val ends = listOf(
            at(monday),
            at(monday.plusDays(2)),
            at(monday.minusDays(1)), // Sonntag der Vorwoche
        )
        val perWeek = sessionsPerWeek(ends, zone, DayOfWeek.MONDAY)
        assertEquals(2, perWeek[monday])
        assertEquals(1, perWeek[monday.minusWeeks(1)])
    }

    @Test
    fun streakCountsConsecutiveWeeksReachingGoal() {
        val perWeek = mapOf(
            monday to 3,
            monday.minusWeeks(1) to 4,
            monday.minusWeeks(2) to 3,
            monday.minusWeeks(3) to 1,
            monday.minusWeeks(4) to 5,
        )
        assertEquals(3, weeklyStreak(perWeek, monday, goal = 3))
    }

    @Test
    fun openCurrentWeekDoesNotBreakStreak() {
        val perWeek = mapOf(
            monday to 1,
            monday.minusWeeks(1) to 3,
            monday.minusWeeks(2) to 3,
        )
        assertEquals(2, weeklyStreak(perWeek, monday, goal = 3))
    }

    @Test
    fun missedPreviousWeekResetsStreak() {
        val perWeek = mapOf(
            monday to 3,
            monday.minusWeeks(1) to 2,
            monday.minusWeeks(2) to 3,
        )
        assertEquals(1, weeklyStreak(perWeek, monday, goal = 3))
        assertEquals(0, weeklyStreak(emptyMap(), monday, goal = 3))
        assertEquals(0, weeklyStreak(perWeek, monday, goal = 0))
    }

    @Test
    fun greetingFollowsTimeOfDay() {
        assertEquals(Greeting.EVENING, greetingFor(2))
        assertEquals(Greeting.MORNING, greetingFor(5))
        assertEquals(Greeting.MORNING, greetingFor(11))
        assertEquals(Greeting.AFTERNOON, greetingFor(12))
        assertEquals(Greeting.AFTERNOON, greetingFor(17))
        assertEquals(Greeting.EVENING, greetingFor(18))
    }

    @Test
    fun compactKgFormatting() {
        assertEquals("850", formatCompactKg(850.0, Locale.US))
        assertEquals("4.2k", formatCompactKg(4200.0, Locale.US))
        assertEquals("4,2k", formatCompactKg(4200.0, Locale.GERMANY))
    }
}
