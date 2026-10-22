package app.gympulse.core.insights

import app.gympulse.core.model.SetType

enum class OneRepMaxFormula { EPLEY, BRZYCKI }

/**
 * Geschätztes Einwiederholungsmaximum (e1RM) in kg.
 *
 * Mit RPE werden die Wiederholungen im Tank (RIR = 10 − RPE) zu den geschafften Wiederholungen
 * addiert, weil ein Satz mit 5 Wdh. bei RPE 8 einem Satz mit 7 Wdh. bis zum Versagen entspricht.
 */
object OneRepMax {
    /** Oberhalb dieser (effektiven) Wiederholungszahl sind die Formeln zu ungenau. */
    const val MAX_REPS: Double = 20.0

    /** RPE-Werte darunter liefern keine verlässliche RIR-Schätzung und werden ignoriert. */
    const val MIN_RPE: Double = 6.0
    const val MAX_RPE: Double = 10.0

    /** Epley: Gewicht × (1 + Wdh. / 30); bei einer Wiederholung das Gewicht selbst. */
    fun epley(weightKg: Double, reps: Double): Double = when {
        reps <= 0.0 -> 0.0
        reps <= 1.0 -> weightKg
        else -> weightKg * (1 + reps / 30.0)
    }

    /** Brzycki: Gewicht × 36 / (37 − Wdh.); bei einer Wiederholung das Gewicht selbst. */
    fun brzycki(weightKg: Double, reps: Double): Double = when {
        reps <= 0.0 -> 0.0
        reps <= 1.0 -> weightKg
        reps >= 37.0 -> 0.0
        else -> weightKg * 36.0 / (37.0 - reps)
    }

    /**
     * e1RM für eine Last und Wiederholungszahl oder null, wenn keine sinnvolle Schätzung möglich ist
     * (keine Last, keine Wiederholungen oder mehr als [MAX_REPS] effektive Wiederholungen).
     */
    fun estimate(
        loadKg: Double,
        reps: Int,
        rpe: Double? = null,
        formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
    ): Double? {
        if (loadKg <= 0.0 || reps <= 0) return null
        val repsInReserve = rpe?.takeIf { it in MIN_RPE..MAX_RPE }?.let { MAX_RPE - it } ?: 0.0
        val effectiveReps = reps + repsInReserve
        if (effectiveReps > MAX_REPS) return null
        return when (formula) {
            OneRepMaxFormula.EPLEY -> epley(loadKg, effectiveReps)
            OneRepMaxFormula.BRZYCKI -> brzycki(loadKg, effectiveReps)
        }
    }
}

/** Zählt ein Satz als Arbeitssatz? Aufwärmsätze, offene Sätze und Sätze ohne Wdh. nicht. */
internal val PerformedSet.isWorkSet: Boolean
    get() = isCompleted && setType != SetType.WARMUP && (reps ?: 0) > 0

/**
 * Tatsächlich bewegte Last in kg: Körpergewicht × Faktor + Zusatzgewicht. Null, wenn sie sich
 * nicht bestimmen lässt (Körpergewichtsübung ohne bekanntes Körpergewicht, Satz ohne Gewicht).
 */
internal fun effectiveLoadKg(set: PerformedSet, bodyweightKg: Double?, bodyweightFactor: Double?): Double? {
    val extra = set.weightKg ?: 0.0
    val load = if (bodyweightFactor != null && bodyweightFactor > 0.0) {
        val bodyweight = bodyweightKg?.takeIf { it > 0.0 } ?: return null
        bodyweight * bodyweightFactor + extra
    } else {
        extra
    }
    return load.takeIf { it > 0.0 }
}

/** Höchstes e1RM aller Arbeitssätze einer Session oder null, wenn keiner auswertbar ist. */
fun sessionEstimatedOneRepMax(
    session: ExerciseSession,
    profile: ExerciseProfile,
    formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
    useRpe: Boolean = true,
): Double? = session.sets
    .asSequence()
    .filter { it.isWorkSet }
    .mapNotNull { set ->
        val load = effectiveLoadKg(set, session.bodyweightKg, profile.bodyweightFactor) ?: return@mapNotNull null
        OneRepMax.estimate(load, set.reps ?: 0, if (useRpe) set.rpe else null, formula)
    }
    .maxOrNull()

/** Rundet auf zwei Nachkommastellen, um Gleitkomma-Reste in Gewichtsangaben zu vermeiden. */
internal fun roundKg(value: Double): Double = Math.round(value * 100.0) / 100.0
