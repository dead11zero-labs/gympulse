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
 * Verteilt die abgeschlossenen Sätze einer Session als [HealthWorkoutSegment]s auf ihre Dauer: ein
 * Abschnitt je Satz mit Wiederholungen, Gewicht, RPE und Satznummer (0-basiert, je Übung ab 0).
 *
 * 1. Haben alle Sätze Zeitstempel innerhalb der Session, endet jeder Abschnitt mit dem Abhaken seines
 *    Satzes und beginnt, wo der vorige endet (der erste beim Sessionstart); die Pause vor einem Satz
 *    zählt also zu ihm. Die Reihenfolge folgt den Zeitstempeln, Supersätze wechseln sich also ab.
 * 2. Sonst wird die Session wie bisher in Übungsfenster geteilt: Jedes endet mit dem letzten Satz der
 *    Übung und beginnt, wo das vorige endet; fehlen Zeitstempel, wird gleichmäßig aufgeteilt.
 *    Innerhalb eines Fensters gelten die Zeitstempel der Sätze, sofern sie hineinpassen, sonst wird
 *    das Fenster gleichmäßig auf die Sätze verteilt.
 *
 * Wäre ein Abschnitt bzw. Fenster kürzer als [MIN_SEGMENT] (z. B. weil Sätze erst am Ende gesammelt
 * abgehakt wurden), greift jeweils die gleichmäßige Aufteilung. Abschnitte überlappen nie.
 */
object HealthSegmentPlanner {

    val MIN_SEGMENT: Duration = Duration.ofSeconds(5)

    fun plan(start: Instant, end: Instant, exercises: List<TrainedExercise>): List<HealthWorkoutSegment> {
        val trained = exercises.filter { it.sets.isNotEmpty() }
        if (trained.isEmpty() || !end.isAfter(start)) return emptyList()
        return fromSetTimestamps(start, end, trained) ?: byExerciseWindows(start, end, trained)
    }

    /** Ein Satz mit seiner Übung und seiner Position (0-basiert) unter deren abgeschlossenen Sätzen. */
    private class PlannedSet(val exercise: TrainedExercise, val position: Int, val set: SetEntry)

    private class Window(val exercise: TrainedExercise, val start: Instant, val end: Instant)

    private fun TrainedExercise.plannedSets(): List<PlannedSet> =
        sets.mapIndexed { position, set -> PlannedSet(this, position, set) }

    private fun fromSetTimestamps(
        start: Instant,
        end: Instant,
        exercises: List<TrainedExercise>,
    ): List<HealthWorkoutSegment>? {
        val timed = exercises.flatMap { it.plannedSets() }.map { planned ->
            val done = planned.set.completedAt ?: return null
            if (done.isBefore(start) || done.isAfter(end)) return null
            planned to done
        }
        return chain(start, timed.sortedBy { it.second })
    }

    private fun byExerciseWindows(
        start: Instant,
        end: Instant,
        exercises: List<TrainedExercise>,
    ): List<HealthWorkoutSegment> {
        val windows = windowsFromTimestamps(start, end, exercises) ?: evenWindows(start, end, exercises)
        return windows.flatMap { setsInWindow(it) }
    }

    private fun windowsFromTimestamps(start: Instant, end: Instant, exercises: List<TrainedExercise>): List<Window>? {
        val lastDone = exercises.map { exercise ->
            exercise.sets.mapNotNull { it.completedAt }.maxOrNull() ?: return null
        }
        if (lastDone.any { it.isBefore(start) || it.isAfter(end) }) return null
        var cursor = start
        return exercises.indices.sortedBy { lastDone[it] }.map { index ->
            val windowEnd = lastDone[index]
            if (Duration.between(cursor, windowEnd) < MIN_SEGMENT) return null
            Window(exercises[index], cursor, windowEnd).also { cursor = windowEnd }
        }
    }

    private fun evenWindows(start: Instant, end: Instant, exercises: List<TrainedExercise>): List<Window> =
        exercises.mapIndexedNotNull { index, exercise ->
            slice(start, end, index, exercises.size)?.let { (sliceStart, sliceEnd) ->
                Window(exercise, sliceStart, sliceEnd)
            }
        }

    /** Sätze innerhalb eines Übungsfensters: nach Zeitstempeln, der letzte reicht bis zum Fensterende. */
    private fun setsInWindow(window: Window): List<HealthWorkoutSegment> {
        val sets = window.exercise.plannedSets()
        val timed = sets.mapNotNull { planned ->
            planned.set.completedAt
                ?.takeIf { !it.isBefore(window.start) && !it.isAfter(window.end) }
                ?.let { planned to it }
        }
        if (timed.size == sets.size) {
            val sorted = timed.sortedBy { it.second }
            val stretched = sorted.dropLast(1) + (sorted.last().first to window.end)
            chain(window.start, stretched)?.let { return it }
        }
        return sets.mapIndexedNotNull { index, planned ->
            slice(window.start, window.end, index, sets.size)?.let { (sliceStart, sliceEnd) ->
                segment(sliceStart, sliceEnd, planned)
            }
        }
    }

    /** Abschnitte von Zeitstempel zu Zeitstempel ab [from]; null, wenn einer kürzer als [MIN_SEGMENT] wäre. */
    private fun chain(from: Instant, timed: List<Pair<PlannedSet, Instant>>): List<HealthWorkoutSegment>? {
        var cursor = from
        return timed.map { (planned, until) ->
            if (Duration.between(cursor, until) < MIN_SEGMENT) return null
            segment(cursor, until, planned).also { cursor = until }
        }
    }

    /** [index]-tes von [count] gleich langen Stücken zwischen [start] und [end], null wenn leer. */
    private fun slice(start: Instant, end: Instant, index: Int, count: Int): Pair<Instant, Instant>? {
        val totalMillis = Duration.between(start, end).toMillis()
        val sliceStart = start.plusMillis(totalMillis * index / count)
        val sliceEnd = start.plusMillis(totalMillis * (index + 1) / count)
        return if (sliceStart.isBefore(sliceEnd)) sliceStart to sliceEnd else null
    }

    private fun segment(start: Instant, end: Instant, planned: PlannedSet) = HealthWorkoutSegment(
        startTime = start,
        endTime = end,
        type = planned.exercise.type,
        repetitions = (planned.set.reps ?: 0).coerceAtLeast(0),
        weightKg = planned.set.weightKg?.takeIf { it > 0.0 && it.isFinite() },
        setIndex = planned.position,
        rpe = planned.set.rpe?.takeIf { it in RPE_RANGE },
    )

    /** Borg-CR10-Skala, die Health Connect für die Anstrengung je Abschnitt erwartet. */
    private val RPE_RANGE = 0.0..10.0
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
