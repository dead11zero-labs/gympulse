package app.gympulse.feature.calendar

import app.gympulse.core.data.repository.CompletedSessionSummary
import app.gympulse.core.model.ScheduleOverride
import app.gympulse.core.model.ScheduleOverrideType
import app.gympulse.core.model.ScheduleRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CalendarLogicTest {
    private val zone = ZoneId.of("UTC")

    // Mittwoch, 21.10.2026
    private val today = LocalDate.of(2026, 10, 21)

    private fun rule(planId: String, weekday: DayOfWeek, from: String = "2026-01-01", until: String? = null) =
        ScheduleRule("r-$planId-$weekday-$from", planId, weekday.value, LocalDate.parse(from), until?.let(LocalDate::parse))

    private fun override(date: String, type: ScheduleOverrideType, planId: String? = null) =
        ScheduleOverride("o-$date", LocalDate.parse(date), planId, type)

    private fun session(id: String, date: String, hour: Int = 18) = CompletedSessionSummary(
        id = id,
        title = "Push",
        startedAt = LocalDate.parse(date).atTime(hour - 1, 0).atZone(zone).toInstant(),
        endedAt = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant(),
        volumeKg = 1000.0,
    )

    @Test
    fun weekStartOf_respectsConfiguredStartDay() {
        assertEquals(LocalDate.of(2026, 10, 19), weekStartOf(today, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 10, 18), weekStartOf(today, DayOfWeek.SUNDAY))
        assertEquals(today, weekStartOf(today, DayOfWeek.WEDNESDAY))
    }

    @Test
    fun weekDates_returnsSevenConsecutiveDays() {
        val dates = weekDates(LocalDate.of(2026, 10, 19))
        assertEquals(7, dates.size)
        assertEquals(LocalDate.of(2026, 10, 25), dates.last())
    }

    @Test
    fun monthWeeks_mondayStart_coversWholeMonthWithFullWeeks() {
        val weeks = monthWeeks(YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(5, weeks.size)
        assertTrue(weeks.all { it.size == 7 })
        assertEquals(LocalDate.of(2026, 9, 28), weeks.first().first())
        assertEquals(LocalDate.of(2026, 11, 1), weeks.last().last())
    }

    @Test
    fun monthWeeks_sundayStart_shiftsGrid() {
        val weeks = monthWeeks(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 9, 27), weeks.first().first())
        assertEquals(LocalDate.of(2026, 10, 31), weeks.last().last())
    }

    @Test
    fun monthWeeks_monthStartingOnWeekStart_hasNoLeadingDays() {
        val weeks = monthWeeks(YearMonth.of(2026, 6), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 6, 1), weeks.first().first())
    }

    @Test
    fun rule_appliesOnlyInsideValidity() {
        val r = rule("push", DayOfWeek.MONDAY, from = "2026-10-05", until = "2026-10-19")
        assertFalse(r.appliesTo(LocalDate.of(2026, 9, 28)))
        assertTrue(r.appliesTo(LocalDate.of(2026, 10, 5)))
        assertTrue(r.appliesTo(LocalDate.of(2026, 10, 19)))
        assertFalse(r.appliesTo(LocalDate.of(2026, 10, 26)))
        assertFalse(r.appliesTo(LocalDate.of(2026, 10, 20)))
    }

    @Test
    fun plannedPlanIds_usesWeeklyRule() {
        val rules = listOf(rule("push", DayOfWeek.WEDNESDAY))
        assertEquals(listOf("push"), plannedPlanIds(today, rules, emptyList()))
        assertEquals(emptyList<String>(), plannedPlanIds(today.plusDays(1), rules, emptyList()))
    }

    @Test
    fun plannedPlanIds_skippedOverrideRemovesRulePlan() {
        val rules = listOf(rule("push", DayOfWeek.WEDNESDAY))
        val overrides = listOf(override("2026-10-21", ScheduleOverrideType.SKIPPED))
        assertEquals(emptyList<String>(), plannedPlanIds(today, rules, overrides))
    }

    @Test
    fun plannedPlanIds_movedAndAddedOverridesAddPlans() {
        val rules = listOf(rule("push", DayOfWeek.THURSDAY))
        val overrides = listOf(
            override("2026-10-22", ScheduleOverrideType.MOVED, "pull"),
            override("2026-10-24", ScheduleOverrideType.ADDED, "legs"),
        )
        assertEquals(listOf("push", "pull"), plannedPlanIds(LocalDate.of(2026, 10, 22), rules, overrides))
        assertEquals(listOf("legs"), plannedPlanIds(LocalDate.of(2026, 10, 24), rules, overrides))
    }

    @Test
    fun plannedPlanIds_removesDuplicates() {
        val rules = listOf(rule("push", DayOfWeek.WEDNESDAY))
        val overrides = listOf(override("2026-10-21", ScheduleOverrideType.ADDED, "push"))
        assertEquals(listOf("push"), plannedPlanIds(today, rules, overrides))
    }

    @Test
    fun dayStatus_coversAllCases() {
        val done = listOf(session("s", "2026-10-20"))
        assertEquals(DayStatus.COMPLETED, dayStatus(today.minusDays(1), today, listOf("p"), false, done))
        assertEquals(DayStatus.COMPLETED, dayStatus(today.minusDays(1), today, emptyList(), false, done))
        assertEquals(DayStatus.MISSED, dayStatus(today.minusDays(1), today, listOf("p"), false, emptyList()))
        assertEquals(DayStatus.PLANNED, dayStatus(today, today, listOf("p"), false, emptyList()))
        assertEquals(DayStatus.PLANNED, dayStatus(today.plusDays(2), today, listOf("p"), false, emptyList()))
        assertEquals(DayStatus.SKIPPED, dayStatus(today, today, emptyList(), true, emptyList()))
        assertEquals(DayStatus.REST, dayStatus(today, today, emptyList(), false, emptyList()))
    }

    @Test
    fun buildCalendarWeeks_combinesRulesOverridesAndSessions() {
        val rules = listOf(rule("push", DayOfWeek.MONDAY), rule("pull", DayOfWeek.WEDNESDAY))
        val overrides = listOf(
            override("2026-10-21", ScheduleOverrideType.SKIPPED),
            override("2026-10-22", ScheduleOverrideType.MOVED, "pull"),
        )
        val sessions = listOf(session("s1", "2026-10-19"))
        val week = buildCalendarWeeks(
            weeks = listOf(weekDates(LocalDate.of(2026, 10, 19))),
            today = today,
            zone = zone,
            rules = rules,
            overrides = overrides,
            sessions = sessions,
            period = null,
        ).single()

        assertEquals(DayStatus.COMPLETED, week[0].status)
        assertEquals(listOf("s1"), week[0].sessions.map { it.id })
        assertEquals(DayStatus.REST, week[1].status)
        assertEquals(DayStatus.SKIPPED, week[2].status)
        assertEquals(ScheduleOverrideType.SKIPPED, week[2].override)
        assertTrue(week[2].isToday)
        assertEquals(DayStatus.PLANNED, week[3].status)
        assertEquals(listOf("pull"), week[3].plannedPlanIds)
        assertNull(week[4].override)
    }

    @Test
    fun buildCalendarWeeks_assignsSessionsByLocalDay() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 23:30 UTC ist in Berlin bereits der nächste Tag.
        val late = CompletedSessionSummary(
            id = "late",
            title = null,
            startedAt = Instant.parse("2026-10-20T22:30:00Z"),
            endedAt = Instant.parse("2026-10-20T23:30:00Z"),
            volumeKg = 0.0,
        )
        val week = buildCalendarWeeks(
            weeks = listOf(weekDates(LocalDate.of(2026, 10, 19))),
            today = today,
            zone = berlin,
            rules = emptyList(),
            overrides = emptyList(),
            sessions = listOf(late),
            period = null,
        ).single()
        assertTrue(week[1].sessions.isEmpty())
        assertEquals(listOf("late"), week[2].sessions.map { it.id })
    }

    @Test
    fun buildCalendarWeeks_marksDaysOutsideMonth() {
        val weeks = monthWeeks(YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        val days = buildCalendarWeeks(
            weeks, today, zone, emptyList(), emptyList(), emptyList(), YearMonth.of(2026, 10),
        )
        assertFalse(days.first().first().inPeriod)
        assertTrue(days[1][0].inPeriod)
    }

    @Test
    fun currentPlanForWeekday_ignoresExpiredRules() {
        val rules = listOf(
            rule("old", DayOfWeek.MONDAY, from = "2026-01-01", until = "2026-09-30"),
            rule("new", DayOfWeek.MONDAY, from = "2026-10-01"),
        )
        assertEquals("new", currentPlanForWeekday(rules, DayOfWeek.MONDAY, today))
        assertNull(currentPlanForWeekday(rules, DayOfWeek.TUESDAY, today))
    }

    @Test
    fun defaultSelection_prefersTodayWhenInPeriod() {
        val days = weekDates(LocalDate.of(2026, 10, 19))
        assertEquals(today, defaultSelection(days, today, days.first()))
        assertEquals(days.first(), defaultSelection(days, today.plusWeeks(2), days.first()))
    }
}
