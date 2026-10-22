package app.gympulse.core.data.health

import app.gympulse.core.model.SetEntry
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * Eine Übung einer Session mit ihren abgeschlossenen Sätzen in Satzreihenfolge.
 *
 * @param name Übungsname in App-Sprache, für die Notizen.
 */
data class TrainedExercise(
    val name: String,
    val type: HealthSegmentType,
    val sets: List<SetEntry>,
)

/**
 * Verteilt die Übungen einer Session als [HealthWorkoutSegment]s auf ihre Dauer.
 *
 * Haben alle Übungen Satz-Zeitstempel innerhalb der Session, endet jeder Abschnitt mit dem letzten
 * Satz der Übung und beginnt, wo der vorige endet (der erste beim Sessionstart); die Pause vor einer
 * Übung zählt also zu ihr. Die Reihenfolge folgt dem letzten Satz, sodass auch Supersätze nie
 * überlappen. Fehlen Zeitstempel oder wäre ein Abschnitt kürzer als [MIN_SEGMENT] (z. B. weil alle
 * Sätze erst am Ende abgehakt wurden), wird die Session gleichmäßig aufgeteilt.
 */
object HealthSegmentPlanner {

    val MIN_SEGMENT: Duration = Duration.ofSeconds(5)

    fun plan(start: Instant, end: Instant, exercises: List<TrainedExercise>): List<HealthWorkoutSegment> {
        if (exercises.isEmpty() || !end.isAfter(start)) return emptyList()
        return fromTimestamps(start, end, exercises) ?: evenly(start, end, exercises)
    }

    private fun fromTimestamps(
        start: Instant,
        end: Instant,
        exercises: List<TrainedExercise>,
    ): List<HealthWorkoutSegment>? {
        val lastDone = exercises.map { exercise ->
            exercise.sets.mapNotNull { it.completedAt }.maxOrNull() ?: return null
        }
        if (lastDone.any { it.isBefore(start) || it.isAfter(end) }) return null
        var cursor = start
        return exercises.indices.sortedBy { lastDone[it] }.map { index ->
            val segmentEnd = lastDone[index]
            if (Duration.between(cursor, segmentEnd) < MIN_SEGMENT) return null
            segment(cursor, segmentEnd, exercises[index]).also { cursor = segmentEnd }
        }
    }

    private fun evenly(start: Instant, end: Instant, exercises: List<TrainedExercise>): List<HealthWorkoutSegment> {
        val totalMillis = Duration.between(start, end).toMillis()
        val count = exercises.size
        return exercises.mapIndexedNotNull { index, exercise ->
            val segmentStart = start.plusMillis(totalMillis * index / count)
            val segmentEnd = start.plusMillis(totalMillis * (index + 1) / count)
            if (segmentStart.isBefore(segmentEnd)) segment(segmentStart, segmentEnd, exercise) else null
        }
    }

    private fun segment(start: Instant, end: Instant, exercise: TrainedExercise) = HealthWorkoutSegment(
        startTime = start,
        endTime = end,
        type = exercise.type,
        repetitions = exercise.sets.sumOf { (it.reps ?: 0).coerceAtLeast(0) },
    )
}

/**
 * Notizen einer Trainingseinheit: eigene Notizen der Session und darunter je Übung eine Zeile wie
 * `• Bankdrücken: 3 × 8 × 60 kg, 1 × 6 × 65 kg` (Sätze × Wiederholungen × Gewicht, gleiche
 * aufeinanderfolgende Sätze zusammengefasst). Apps wie Samsung Health zeigen Abschnitte nicht immer
 * an, die Notizen aber schon.
 */
object HealthWorkoutNotes {

    /** Obergrenze, damit lange Workouts nicht an Längenprüfungen von Health Connect scheitern. */
    const val MAX_LENGTH = 1000

    fun build(userNotes: String?, exercises: List<TrainedExercise>, locale: Locale): String? {
        val own = userNotes?.trim()?.takeIf { it.isNotEmpty() }?.let { truncate(it, MAX_LENGTH) }
        val lines = exercises.filter { it.sets.isNotEmpty() }.map { exerciseLine(it, locale) }
        val separator = "\n\n"
        val budget = MAX_LENGTH - (own?.let { it.length + separator.length } ?: 0)
        val list = listText(lines, budget)
        return listOfNotNull(own, list).joinToString(separator).takeIf { it.isNotEmpty() }
    }

    fun exerciseLine(exercise: TrainedExercise, locale: Locale): String {
        val groups = mutableListOf<Pair<String, Int>>()
        exercise.sets.forEach { set ->
            val spec = setSpec(set, locale)
            val last = groups.lastOrNull()
            if (last != null && last.first == spec) {
                groups[groups.lastIndex] = spec to last.second + 1
            } else {
                groups += spec to 1
            }
        }
        val sets = groups.joinToString(", ") { (spec, count) -> "$count × $spec" }
        return "$BULLET${exercise.name}: $sets"
    }

    /** `8 × 60 kg`, `12`, `45 s`, `20 min / 3 km` oder `–`, wenn nichts erfasst wurde. */
    fun setSpec(set: SetEntry, locale: Locale): String {
        val reps = set.reps?.takeIf { it > 0 }
        val weight = set.weightKg?.takeIf { it > 0.0 }
        val parts = buildList {
            when {
                reps != null && weight != null -> add("$reps × ${number(weight, locale)} kg")
                reps != null -> add(reps.toString())
                weight != null -> add("${number(weight, locale)} kg")
            }
            set.durationSeconds?.takeIf { it > 0 }?.let { add(duration(it)) }
            set.distanceMeters?.takeIf { it > 0.0 }?.let { add(distance(it, locale)) }
        }
        return parts.joinToString(" / ").ifEmpty { "–" }
    }

    private fun duration(seconds: Int): String = when {
        seconds < 60 -> "$seconds s"
        seconds % 60 == 0 -> "${seconds / 60} min"
        else -> "%d:%02d min".format(Locale.ROOT, seconds / 60, seconds % 60)
    }

    private fun distance(meters: Double, locale: Locale): String =
        if (meters < 1000.0) "${number(meters, locale)} m" else "${number(meters / 1000.0, locale)} km"

    private fun number(value: Double, locale: Locale): String =
        DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale)).format(value)

    /** Ganze Zeilen, solange sie in [budget] passen; abgeschnittene Zeilen werden durch `…` ersetzt. */
    private fun listText(lines: List<String>, budget: Int): String? {
        if (lines.isEmpty() || budget <= ELLIPSIS.length) return null
        val kept = StringBuilder()
        for ((index, line) in lines.withIndex()) {
            val prefix = if (kept.isEmpty()) "" else "\n"
            val remaining = lines.size - index - 1
            val reserve = if (remaining > 0) 1 + ELLIPSIS.length else 0
            if (kept.length + prefix.length + line.length + reserve <= budget) {
                kept.append(prefix).append(line)
            } else {
                val ellipsisPrefix = if (kept.isEmpty()) "" else "\n"
                if (kept.isEmpty()) {
                    kept.append(truncate(line, budget))
                } else if (kept.length + ellipsisPrefix.length + ELLIPSIS.length <= budget) {
                    kept.append(ellipsisPrefix).append(ELLIPSIS)
                }
                break
            }
        }
        return kept.toString().takeIf { it.isNotEmpty() }
    }

    private fun truncate(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max - ELLIPSIS.length).trimEnd() + ELLIPSIS

    private const val BULLET = "• "
    private const val ELLIPSIS = "…"
}
