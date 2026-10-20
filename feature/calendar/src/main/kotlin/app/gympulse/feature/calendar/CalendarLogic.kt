package app.gympulse.feature.calendar

import app.gympulse.core.data.repository.CompletedSessionSummary
import app.gympulse.core.model.ScheduleOverride
import app.gympulse.core.model.ScheduleOverrideType
import app.gympulse.core.model.ScheduleRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class CalendarMode { WEEK, MONTH }

enum class DayStatus {
    /** Nichts geplant, nichts trainiert. */
    REST,

    /** Geplant, heute oder in der Zukunft. */
    PLANNED,

    /** Mindestens eine abgeschlossene Session. */
    COMPLETED,

    /** Geplant, vergangen, ohne Session. */
    MISSED,

    /** Per Ausnahme ausgefallen. */
    SKIPPED,
}

data class CalendarDay(
    val date: LocalDate,
    /** Pläne dieses Tages (Wochenschema und Ausnahmen), ohne Duplikate. */
    val plannedPlanIds: List<String>,
    /** Art der Ausnahme für diesen Tag, null ohne Ausnahme. */
    val override: ScheduleOverrideType?,
    val sessions: List<CompletedSessionSummary>,
    val status: DayStatus,
    val isToday: Boolean,
    /** False für Tage aus dem Vor-/Folgemonat in der Monatsansicht. */
    val inPeriod: Boolean,
)

/** Erster Tag der Woche, die [date] enthält. */
fun weekStartOf(date: LocalDate, weekStartsOn: DayOfWeek): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(weekStartsOn))

/** Sieben Tage ab [weekStart]. */
fun weekDates(weekStart: LocalDate): List<LocalDate> = List(DAYS_PER_WEEK) { weekStart.plusDays(it.toLong()) }

/** Wochen (je sieben Tage) des Monatsrasters, inklusive der Randtage aus den Nachbarmonaten. */
fun monthWeeks(month: YearMonth, weekStartsOn: DayOfWeek): List<List<LocalDate>> {
    val first = weekStartOf(month.atDay(1), weekStartsOn)
    val last = month.atEndOfMonth()
    val weeks = mutableListOf<List<LocalDate>>()
    var start = first
    while (!start.isAfter(last)) {
        weeks += weekDates(start)
        start = start.plusWeeks(1)
    }
    return weeks
}

/** Gilt [rule] am Datum [date]? */
fun ScheduleRule.appliesTo(date: LocalDate): Boolean {
    val until = validUntil
    return weekday == date.dayOfWeek.value && !validFrom.isAfter(date) && (until == null || !until.isBefore(date))
}

/**
 * Pläne eines Tages: Plan aus dem Wochenschema, außer der Tag fällt per Ausnahme aus; dazu
 * verschobene oder hinzugefügte Pläne.
 */
fun plannedPlanIds(date: LocalDate, rules: List<ScheduleRule>, overrides: List<ScheduleOverride>): List<String> {
    val dayOverrides = overrides.filter { it.date == date }
    val skipped = dayOverrides.any { it.type == ScheduleOverrideType.SKIPPED }
    val fromRules = if (skipped) emptyList() else rules.filter { it.appliesTo(date) }.map { it.planId }
    val extra = dayOverrides
        .filter { it.type != ScheduleOverrideType.SKIPPED }
        .mapNotNull { it.planId }
    return (fromRules + extra).distinct()
}

fun dayStatus(
    date: LocalDate,
    today: LocalDate,
    planned: List<String>,
    skipped: Boolean,
    sessions: List<CompletedSessionSummary>,
): DayStatus = when {
    sessions.isNotEmpty() -> DayStatus.COMPLETED
    skipped && planned.isEmpty() -> DayStatus.SKIPPED
    planned.isEmpty() -> DayStatus.REST
    date.isBefore(today) -> DayStatus.MISSED
    else -> DayStatus.PLANNED
}

/**
 * Baut die Tage der Wochen. Sessions zählen an dem Tag, an dem sie endeten (in [zone]).
 *
 * @param period Monat, dem Tage in der Monatsansicht angehören; null in der Wochenansicht (alle Tage zählen).
 */
fun buildCalendarWeeks(
    weeks: List<List<LocalDate>>,
    today: LocalDate,
    zone: ZoneId,
    rules: List<ScheduleRule>,
    overrides: List<ScheduleOverride>,
    sessions: List<CompletedSessionSummary>,
    period: YearMonth?,
): List<List<CalendarDay>> {
    val sessionsByDate = sessions.groupBy { it.endedAt.atZone(zone).toLocalDate() }
    return weeks.map { week ->
        week.map { date ->
            val planned = plannedPlanIds(date, rules, overrides)
            val dayOverrides = overrides.filter { it.date == date }
            val done = sessionsByDate[date].orEmpty().sortedBy { it.endedAt }
            CalendarDay(
                date = date,
                plannedPlanIds = planned,
                override = dayOverrides.firstOrNull()?.type,
                sessions = done,
                status = dayStatus(
                    date = date,
                    today = today,
                    planned = planned,
                    skipped = dayOverrides.any { it.type == ScheduleOverrideType.SKIPPED },
                    sessions = done,
                ),
                isToday = date == today,
                inPeriod = period == null || YearMonth.from(date) == period,
            )
        }
    }
}

/** Plan, der laut Wochenschema heute für [weekday] gilt, sonst null. */
fun currentPlanForWeekday(rules: List<ScheduleRule>, weekday: DayOfWeek, today: LocalDate): String? =
    rules.firstOrNull { rule ->
        val until = rule.validUntil
        rule.weekday == weekday.value && !rule.validFrom.isAfter(today) && (until == null || !until.isBefore(today))
    }?.planId

/** Standardauswahl nach Blättern: heute, falls im Zeitraum, sonst der erste Tag. */
fun defaultSelection(periodDays: List<LocalDate>, today: LocalDate, firstOfPeriod: LocalDate): LocalDate =
    if (today in periodDays) today else firstOfPeriod

private const val DAYS_PER_WEEK = 7
