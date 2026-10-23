package app.gympulse.feature.dashboard

import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MuscleGroup
import app.gympulse.core.model.SetType
import app.gympulse.core.model.WorkoutSession
import app.gympulse.core.model.setVolumeKg
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Wie viele Wochen die Analyse lädt: die gezeigten plus Kontext für Vergleich und "vernachlässigt". */
internal const val WEEKS_LOADED = 12

/** Wochen im Verlaufsdiagramm (und damit auswählbar). */
internal const val WEEKS_SHOWN = 8

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDARY_MUSCLE_WEIGHT = 0.5
private const val NEGLECT_LOOKBACK_WEEKS = 3
private const val NEGLECT_MIN_SETS = 6.0

/** Grobe Körperregion für den Ring "Oberkörper / Beine / Rumpf". */
enum class BodyRegion { UPPER, LEGS, CORE }

fun MuscleGroup.region(): BodyRegion = when (this) {
    MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.LATS, MuscleGroup.MIDDLE_BACK,
    MuscleGroup.TRAPS, MuscleGroup.BICEPS, MuscleGroup.TRICEPS, MuscleGroup.FOREARMS,
    MuscleGroup.NECK,
    -> BodyRegion.UPPER
    MuscleGroup.QUADRICEPS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES,
    MuscleGroup.ABDUCTORS, MuscleGroup.ADDUCTORS,
    -> BodyRegion.LEGS
    MuscleGroup.ABDOMINALS, MuscleGroup.LOWER_BACK -> BodyRegion.CORE
}

data class ExerciseVolume(
    val exerciseId: String,
    val name: String,
    val volumeKg: Double,
    val sets: Int,
)

/** Kennzahlen einer Woche, abgeleitet aus den abgeschlossenen Sessions mit Ende in dieser Woche. */
data class WeekStats(
    val weekStart: LocalDate,
    val sessions: Int,
    /** Erledigte Arbeitssätze (ohne Aufwärmen, ohne Kardio). */
    val strengthSets: Int,
    val volumeKg: Double,
    /** Sieben Werte in kg, beginnend mit [weekStart]. */
    val dailyKg: List<Double>,
    val dailySessions: List<Int>,
    /** Summe der Sessiondauern (Start bis Ende). */
    val sessionSeconds: Long,
    val cardioSeconds: Long,
    val cardioDistanceMeters: Double,
    /** Gewichtete Sätze je Muskelgruppe: Hauptmuskel 1, Nebenmuskel 0,5. */
    val muscleSets: Map<MuscleGroup, Double>,
    /** Übungen nach Volumen absteigend. */
    val topExercises: List<ExerciseVolume>,
) {
    val isEmpty: Boolean get() = sessions == 0

    val durationMinutes: Long get() = sessionSeconds / SECONDS_PER_MINUTE

    val cardioMinutes: Long get() = cardioSeconds / SECONDS_PER_MINUTE

    val averageVolumeKg: Double get() = if (sessions > 0) volumeKg / sessions else 0.0

    /**
     * Anteil Kardio an der Trainingszeit (0..1). Die Kraftzeit ist die Sessiondauer abzüglich der
     * erfassten Kardiozeit, also inklusive Pausen.
     */
    val cardioShare: Float
        get() {
            val total = maxOf(sessionSeconds, cardioSeconds)
            return if (total <= 0L) 0f else (cardioSeconds.toFloat() / total).coerceIn(0f, 1f)
        }

    /** Anteil je Körperregion an den gewichteten Sätzen; leer ohne Kraftsätze. */
    fun regionShares(): Map<BodyRegion, Float> {
        val total = muscleSets.values.sum()
        if (total <= 0.0) return emptyMap()
        return BodyRegion.entries.associateWith { region ->
            (muscleSets.filterKeys { it.region() == region }.values.sum() / total).toFloat()
        }
    }
}

/** Auffälligkeiten einer Woche; alle Felder sind null, wenn es nichts zu sagen gibt. */
data class WeekHighlights(
    val strongestDay: DayOfWeek? = null,
    val topMuscle: MuscleGroup? = null,
    /** Früher regelmäßig trainiert, in dieser Woche aber ohne Satz. */
    val neglectedMuscle: MuscleGroup? = null,
) {
    val isEmpty: Boolean get() = strongestDay == null && topMuscle == null && neglectedMuscle == null
}

private class ExerciseAccumulator {
    var volumeKg = 0.0
    var sets = 0
}

/**
 * Wertet die Sessions einer Woche aus. Volumen und Zählweise entsprechen der Dashboard-Kachel
 * (nur erledigte Arbeitssätze mit Wiederholungen); Kardio-Sätze zählen über Dauer und Distanz.
 *
 * @param sessions abgeschlossene Sessions, deren Ende in der Woche ab [weekStart] liegt.
 */
internal fun buildWeekStats(
    weekStart: LocalDate,
    sessions: List<WorkoutSession>,
    exercises: Map<String, Exercise>,
    zone: ZoneId,
): WeekStats {
    val dailyKg = DoubleArray(DAYS_PER_WEEK)
    val dailySessions = IntArray(DAYS_PER_WEEK)
    val muscleSets = mutableMapOf<MuscleGroup, Double>()
    val perExercise = mutableMapOf<String, ExerciseAccumulator>()
    var sessionCount = 0
    var strengthSets = 0
    var sessionSeconds = 0L
    var cardioSeconds = 0L
    var cardioMeters = 0.0

    for (session in sessions) {
        val endedAt = session.endedAt ?: continue
        sessionCount++
        val day = ChronoUnit.DAYS.between(weekStart, endedAt.atZone(zone).toLocalDate())
            .toInt().coerceIn(0, DAYS_PER_WEEK - 1)
        dailySessions[day]++
        sessionSeconds += Duration.between(session.startedAt, endedAt).seconds.coerceAtLeast(0L)

        for (item in session.exercises) {
            val exercise = exercises[item.exerciseId]
            val isCardio = exercise?.category == ExerciseCategory.CARDIO
            for (set in item.sets) {
                if (!set.isCompleted) continue
                if (isCardio) {
                    val seconds = set.durationSeconds ?: 0
                    val meters = set.distanceMeters ?: 0.0
                    cardioSeconds += seconds.coerceAtLeast(0)
                    cardioMeters += meters.coerceAtLeast(0.0)
                    continue
                }
                if (set.setType == SetType.WARMUP || (set.reps ?: 0) <= 0) continue
                val kg = setVolumeKg(set.weightKg, set.reps, session.bodyweightKg, exercise?.bodyweightFactor)
                strengthSets++
                dailyKg[day] += kg
                if (exercise != null) {
                    exercise.primaryMuscles.forEach { muscleSets.merge(it, 1.0, Double::plus) }
                    exercise.secondaryMuscles.forEach { muscleSets.merge(it, SECONDARY_MUSCLE_WEIGHT, Double::plus) }
                    val acc = perExercise.getOrPut(exercise.id) { ExerciseAccumulator() }
                    acc.volumeKg += kg
                    acc.sets++
                }
            }
        }
    }

    return WeekStats(
        weekStart = weekStart,
        sessions = sessionCount,
        strengthSets = strengthSets,
        volumeKg = dailyKg.sum(),
        dailyKg = dailyKg.toList(),
        dailySessions = dailySessions.toList(),
        sessionSeconds = sessionSeconds,
        cardioSeconds = cardioSeconds,
        cardioDistanceMeters = cardioMeters,
        muscleSets = muscleSets,
        topExercises = perExercise
            .map { (id, acc) -> ExerciseVolume(id, exercises.getValue(id).name, acc.volumeKg, acc.sets) }
            .sortedByDescending { it.volumeKg },
    )
}

/**
 * Baut [WEEKS_LOADED] Wochen, die letzte beginnt bei [currentWeekStart]; Wochen ohne Training
 * sind leer. Sessions werden nach dem Ende der Woche zugeordnet.
 */
internal fun buildWeeks(
    sessions: List<WorkoutSession>,
    exercises: Map<String, Exercise>,
    currentWeekStart: LocalDate,
    weekStartsOn: DayOfWeek,
    zone: ZoneId,
): List<WeekStats> {
    val byWeek = sessions
        .filter { it.endedAt != null }
        .groupBy { weekStartOf(it.endedAt!!.atZone(zone).toLocalDate(), weekStartsOn) }
    return (WEEKS_LOADED - 1 downTo 0).map { back ->
        val weekStart = currentWeekStart.minusWeeks(back.toLong())
        buildWeekStats(weekStart, byWeek[weekStart].orEmpty(), exercises, zone)
    }
}

/** Stärkster Tag, meisttrainierter Muskel und Muskel, der gegenüber den Vorwochen fehlt. */
internal fun weekHighlights(selected: WeekStats, earlier: List<WeekStats>): WeekHighlights {
    if (selected.isEmpty) return WeekHighlights()
    val peak = selected.dailyKg.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0.0 }
    val recent = earlier.takeLast(NEGLECT_LOOKBACK_WEEKS)
    val neglected = recent
        .flatMap { it.muscleSets.entries }
        .groupingBy { it.key }
        .fold(0.0) { sum, entry -> sum + entry.value }
        .filter { (muscle, sum) -> sum >= NEGLECT_MIN_SETS && (selected.muscleSets[muscle] ?: 0.0) <= 0.0 }
        .maxByOrNull { it.value }
        ?.key
    return WeekHighlights(
        strongestDay = peak?.let { selected.weekStart.plusDays(it.index.toLong()).dayOfWeek },
        topMuscle = selected.muscleSets.maxByOrNull { it.value }?.key,
        neglectedMuscle = neglected,
    )
}

private const val DAYS_PER_WEEK = 7
