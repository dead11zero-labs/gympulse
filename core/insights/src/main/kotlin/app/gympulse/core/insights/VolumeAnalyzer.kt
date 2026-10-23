package app.gympulse.core.insights

import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MuscleGroup
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

enum class VolumeStatus { LOW, OPTIMAL, HIGH }

/** Harte Sätze einer Muskelgruppe; Primärmuskel zählt 1, Sekundärmuskel 0,5 (konfigurierbar). */
data class MuscleVolume(
    val muscle: MuscleGroup,
    val totalSets: Double,
    val setsPerWeek: Double,
    val target: VolumeTarget,
    val status: VolumeStatus,
)

/** Gegenspieler-Paare für die Prüfung auf Ungleichgewicht. */
enum class BalancePair(val sideA: Set<MuscleGroup>, val sideB: Set<MuscleGroup>) {
    PUSH_PULL(
        sideA = setOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
        sideB = setOf(MuscleGroup.LATS, MuscleGroup.MIDDLE_BACK, MuscleGroup.BICEPS),
    ),
    QUADS_HAMSTRINGS(
        sideA = setOf(MuscleGroup.QUADRICEPS),
        sideB = setOf(MuscleGroup.HAMSTRINGS),
    ),
}

enum class BalanceSide { A, B }

/**
 * @property ratio stärkere / schwächere Seite; null, wenn die schwächere Seite gar nicht trainiert wurde.
 */
data class MuscleImbalance(
    val pair: BalancePair,
    val sideASetsPerWeek: Double,
    val sideBSetsPerWeek: Double,
    val dominantSide: BalanceSide,
    val ratio: Double?,
)

/**
 * @property weeks Anzahl Wochen, über die gemittelt wurde (kürzer als das Fenster, wenn die
 *   Trainingshistorie jünger ist).
 */
data class VolumeReport(
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val weeks: Int,
    val muscles: List<MuscleVolume>,
    val imbalances: List<MuscleImbalance>,
) {
    val hasData: Boolean get() = muscles.isNotEmpty()

    fun volumeOf(muscle: MuscleGroup): MuscleVolume? = muscles.firstOrNull { it.muscle == muscle }
}

/** Volumen pro Muskelgruppe im Zeitraum [VolumeConfig.windowWeeks] bis zum Stichtag (inklusive). */
object VolumeAnalyzer {

    fun analyze(histories: List<ExerciseHistory>, asOf: LocalDate, config: VolumeConfig = VolumeConfig()): VolumeReport {
        val from = asOf.minusDays(config.windowWeeks * 7L - 1)
        val earliest = histories.asSequence()
            .flatMap { it.sessions.asSequence() }
            .map { it.date }
            .filter { !it.isAfter(asOf) }
            .minOrNull()

        val sets = mutableMapOf<MuscleGroup, Double>()
        var anyHardSet = false
        for (history in histories) {
            val profile = history.profile
            if (profile.category in EXCLUDED_CATEGORIES) continue
            for (session in history.sessions) {
                if (session.date.isBefore(from) || session.date.isAfter(asOf)) continue
                val hardSets = session.sets.count { it.isHardSet(config) }
                if (hardSets == 0) continue
                anyHardSet = true
                // Ein Muskel, der doppelt gepflegt ist, zählt nur mit seinem höheren Gewicht.
                val weights = mutableMapOf<MuscleGroup, Double>()
                profile.secondaryMuscles.forEach { weights[it] = config.secondaryWeight }
                profile.primaryMuscles.forEach { weights[it] = config.primaryWeight }
                weights.forEach { (muscle, weight) -> sets.merge(muscle, hardSets * weight, Double::plus) }
            }
        }

        val weeks = if (earliest == null) {
            config.windowWeeks
        } else {
            val days = ChronoUnit.DAYS.between(maxOf(earliest, from), asOf) + 1
            ceil(days / 7.0).toInt().coerceIn(1, config.windowWeeks)
        }

        if (!anyHardSet) return VolumeReport(from, asOf, weeks, emptyList(), emptyList())

        val muscles = MuscleGroup.entries
            .filter { (sets[it] ?: 0.0) > 0.0 || it in config.trackedMuscles }
            .map { muscle ->
                val total = sets[muscle] ?: 0.0
                val perWeek = total / weeks
                val target = config.targetFor(muscle)
                val status = when {
                    perWeek < target.minSetsPerWeek -> VolumeStatus.LOW
                    perWeek > target.maxSetsPerWeek -> VolumeStatus.HIGH
                    else -> VolumeStatus.OPTIMAL
                }
                MuscleVolume(muscle, total, perWeek, target, status)
            }

        val perWeek = sets.mapValues { it.value / weeks }
        val imbalances = BalancePair.entries.mapNotNull { pair -> imbalanceOf(pair, perWeek, config) }
        return VolumeReport(from, asOf, weeks, muscles, imbalances)
    }

    private fun imbalanceOf(pair: BalancePair, perWeek: Map<MuscleGroup, Double>, config: VolumeConfig): MuscleImbalance? {
        val a = pair.sideA.sumOf { perWeek[it] ?: 0.0 }
        val b = pair.sideB.sumOf { perWeek[it] ?: 0.0 }
        val stronger = maxOf(a, b)
        val weaker = minOf(a, b)
        if (stronger < config.minSetsForBalance) return null
        val ratio = if (weaker > 0.0) stronger / weaker else null
        if (ratio != null && ratio < config.imbalanceRatio) return null
        return MuscleImbalance(
            pair = pair,
            sideASetsPerWeek = a,
            sideBSetsPerWeek = b,
            dominantSide = if (a >= b) BalanceSide.A else BalanceSide.B,
            ratio = ratio,
        )
    }

    private fun PerformedSet.isHardSet(config: VolumeConfig): Boolean {
        if (!isWorkSet) return false
        val threshold = config.minHardSetRpe ?: return true
        return rpe == null || rpe >= threshold
    }

    private val EXCLUDED_CATEGORIES = setOf(ExerciseCategory.STRETCHING, ExerciseCategory.CARDIO)
}
