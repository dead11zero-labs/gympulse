package app.gympulse.core.insights

import app.gympulse.core.model.SetType
import kotlin.math.abs
import kotlin.math.floor

enum class ProgressionAction { INCREASE_WEIGHT, INCREASE_REPS, HOLD, DELOAD }

enum class ProgressionReason {
    /** Alle Sätze am oberen Ende des Bereichs, RPE (falls erfasst) im Rahmen. */
    ALL_SETS_AT_TOP,

    /** Bereich noch nicht ausgeschöpft: Wiederholungen steigern. */
    REPS_BELOW_TOP,

    /** Mindestens ein Satz unter dem unteren Ende des Bereichs: Gewicht halten. */
    BELOW_MIN_REPS,

    /** Wiederholungen geschafft, aber zu schwer (RPE über dem Grenzwert): Gewicht halten. */
    RPE_TOO_HIGH,

    /** Weniger Arbeitssätze als geplant: Gewicht halten. */
    SETS_MISSING,

    /** Mehrere Plateau-Phasen in Folge. */
    REPEATED_PLATEAU,

    /** Anhaltender e1RM-Rückgang. */
    SUSTAINED_DECLINE,
}

/** Warum ein Deload empfohlen wird. */
enum class DeloadReason { REPEATED_PLATEAU, SUSTAINED_DECLINE }

/**
 * Vorschlag für die nächste Session einer Übung.
 *
 * @property currentWeightKg Arbeitsgewicht der letzten Session (bei Körpergewichtsübungen das Zusatzgewicht).
 * @property suggestedWeightKg vorgeschlagenes Gewicht, auf den Gerätesprung gerundet.
 * @property targetReps angestrebte Wiederholungen pro Satz.
 */
data class ProgressionSuggestion(
    val exerciseId: String,
    val action: ProgressionAction,
    val reason: ProgressionReason,
    val currentWeightKg: Double,
    val suggestedWeightKg: Double,
    val targetReps: Int,
    val incrementKg: Double,
)

/** Deload nach Plan: nach [ProgressionConfig.plateausBeforeDeload] Plateau-Phasen oder bei anhaltendem Rückgang. */
object DeloadAdvisor {
    fun evaluate(trend: TrendResult, config: ProgressionConfig = ProgressionConfig()): DeloadReason? = when {
        trend.status == TrendStatus.PLATEAU && trend.stalledPeriods >= config.plateausBeforeDeload ->
            DeloadReason.REPEATED_PLATEAU
        trend.status == TrendStatus.DECLINING && trend.stalledPeriods >= 1 ->
            DeloadReason.SUSTAINED_DECLINE
        else -> null
    }
}

/**
 * Double Progression: Werden alle Arbeitssätze am oberen Ende des Wiederholungsbereichs geschafft
 * (und liegt kein erfasster RPE über dem Grenzwert), steigt das Gewicht um den kleinsten Gerätesprung
 * und die Wiederholungen beginnen wieder unten im Bereich. Sonst werden die Wiederholungen gesteigert.
 */
object ProgressionAdvisor {

    /**
     * Vorschlag auf Basis der letzten Session mit Arbeitssätzen oder null, wenn es keine gibt oder
     * (ohne Deload) kein Wiederholungsbereich bekannt ist.
     */
    fun suggest(
        history: ExerciseHistory,
        deload: DeloadReason? = null,
        config: ProgressionConfig = ProgressionConfig(),
    ): ProgressionSuggestion? {
        val last = history.sessions
            .filter { session -> session.sets.any { it.isWorkSet } }
            .maxByOrNull { it.date }
            ?: return null
        return suggest(history.profile, last, deload, config)
    }

    fun suggest(
        profile: ExerciseProfile,
        lastSession: ExerciseSession,
        deload: DeloadReason? = null,
        config: ProgressionConfig = ProgressionConfig(),
    ): ProgressionSuggestion? {
        val workSets = lastSession.sets.filter { it.isWorkSet }
        // Drop-Sätze sind bewusst leichter und bremsen die Progression nicht.
        val evaluated = workSets.filter { it.setType != SetType.DROP }.ifEmpty { workSets }
        if (evaluated.isEmpty()) return null

        val currentWeight = roundKg(evaluated.maxOf { it.weightKg ?: 0.0 })
        val topSets = evaluated.filter { abs((it.weightKg ?: 0.0) - currentWeight) < WEIGHT_EPSILON }
        val increment = config.incrementFor(profile)
        val range = profile.targetRepRange ?: config.defaultRepRange

        fun result(action: ProgressionAction, reason: ProgressionReason, weight: Double, reps: Int) =
            ProgressionSuggestion(
                exerciseId = profile.exerciseId,
                action = action,
                reason = reason,
                currentWeightKg = currentWeight,
                suggestedWeightKg = weight,
                targetReps = reps,
                incrementKg = increment,
            )

        if (deload != null) {
            val reason = when (deload) {
                DeloadReason.REPEATED_PLATEAU -> ProgressionReason.REPEATED_PLATEAU
                DeloadReason.SUSTAINED_DECLINE -> ProgressionReason.SUSTAINED_DECLINE
            }
            val reps = range?.min ?: topSets.minOf { it.reps ?: 0 }
            return result(ProgressionAction.DELOAD, reason, deloadWeight(currentWeight, increment, config), reps)
        }

        range ?: return null
        val minReps = topSets.minOf { it.reps ?: 0 }
        val plannedSets = profile.targetSets

        return when {
            minReps < range.min ->
                result(ProgressionAction.HOLD, ProgressionReason.BELOW_MIN_REPS, currentWeight, range.min)
            plannedSets != null && topSets.size < plannedSets ->
                result(ProgressionAction.HOLD, ProgressionReason.SETS_MISSING, currentWeight, minOf(minReps, range.max))
            minReps >= range.max -> {
                val tooHard = topSets.any { set -> set.rpe?.let { it > config.maxRpeForIncrease } == true }
                if (tooHard) {
                    result(ProgressionAction.HOLD, ProgressionReason.RPE_TOO_HIGH, currentWeight, range.max)
                } else {
                    result(
                        ProgressionAction.INCREASE_WEIGHT,
                        ProgressionReason.ALL_SETS_AT_TOP,
                        roundKg(currentWeight + increment),
                        range.min,
                    )
                }
            }
            else ->
                result(ProgressionAction.INCREASE_REPS, ProgressionReason.REPS_BELOW_TOP, currentWeight, minOf(range.max, minReps + 1))
        }
    }

    /** Gewicht um [ProgressionConfig.deloadFraction] senken und auf den Gerätesprung abrunden. */
    internal fun deloadWeight(currentWeightKg: Double, incrementKg: Double, config: ProgressionConfig): Double {
        if (currentWeightKg <= 0.0) return 0.0
        val reduced = currentWeightKg * (1.0 - config.deloadFraction)
        val floored = floor(reduced / incrementKg + 1e-9) * incrementKg
        return roundKg(if (floored > 0.0) floored else reduced)
    }

    private const val WEIGHT_EPSILON = 0.005
}
