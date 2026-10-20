package app.gympulse.feature.dashboard

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

/** Tageszeit für die Begrüßung. */
enum class Greeting { MORNING, AFTERNOON, EVENING }

fun greetingFor(hourOfDay: Int): Greeting = when (hourOfDay) {
    in 5..11 -> Greeting.MORNING
    in 12..17 -> Greeting.AFTERNOON
    else -> Greeting.EVENING
}

/**
 * Veränderung gegenüber der Vorwoche in ganzen Prozent.
 * Null, wenn die Vorwoche kein Volumen hat (kein sinnvoller Vergleich möglich).
 */
fun percentChange(current: Double, previous: Double): Int? {
    if (previous <= 0.0) return null
    return ((current - previous) / previous * 100.0).roundToInt()
}

/** Fortschritt in Richtung Wochenziel, begrenzt auf 0..1. Ein Ziel <= 0 ergibt 0. */
fun goalProgress(completed: Int, goal: Int): Float {
    if (goal <= 0) return 0f
    return (completed.toFloat() / goal).coerceIn(0f, 1f)
}

/** Erster Tag der Woche, in der [date] liegt. */
fun weekStartOf(date: LocalDate, weekStartsOn: DayOfWeek): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(weekStartsOn))

/** Anzahl abgeschlossener Sessions je Woche, Schlüssel ist der Wochenstart. */
fun sessionsPerWeek(
    endTimes: List<Instant>,
    zone: ZoneId,
    weekStartsOn: DayOfWeek,
): Map<LocalDate, Int> =
    endTimes.groupingBy { weekStartOf(it.atZone(zone).toLocalDate(), weekStartsOn) }.eachCount()

/**
 * Wochen in Folge mit erreichtem Ziel. Die laufende Woche zählt mit, sobald das Ziel erreicht ist;
 * ist sie noch offen, unterbricht sie die Serie nicht.
 */
fun weeklyStreak(
    sessionsPerWeek: Map<LocalDate, Int>,
    currentWeekStart: LocalDate,
    goal: Int,
): Int {
    if (goal <= 0) return 0
    var streak = if ((sessionsPerWeek[currentWeekStart] ?: 0) >= goal) 1 else 0
    var week = currentWeekStart.minusWeeks(1)
    while ((sessionsPerWeek[week] ?: 0) >= goal) {
        streak++
        week = week.minusWeeks(1)
    }
    return streak
}

/** Kompakte Gewichtsangabe für Balkenbeschriftungen, z. B. "850" oder "4.2k". */
fun formatCompactKg(valueKg: Double, locale: Locale): String =
    if (valueKg >= 1000.0) {
        String.format(locale, "%.1fk", valueKg / 1000.0)
    } else {
        String.format(locale, "%.0f", valueKg)
    }
