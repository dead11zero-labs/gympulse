package app.gympulse.core.model

/**
 * Volumen eines Satzes in kg: (Körpergewicht × Faktor + Zusatzgewicht) × Wiederholungen.
 * Bei Übungen ohne Körpergewichtsanteil ist [bodyweightFactor] null und es zählt nur das Gewicht.
 */
fun setVolumeKg(
    weightKg: Double?,
    reps: Int?,
    bodyweightKg: Double?,
    bodyweightFactor: Double?,
): Double {
    val repetitions = reps ?: return 0.0
    if (repetitions <= 0) return 0.0
    val bodyweightShare = if (bodyweightFactor != null && bodyweightKg != null) bodyweightKg * bodyweightFactor else 0.0
    val load = bodyweightShare + (weightKg ?: 0.0)
    return load * repetitions
}

/** Geschätztes 1RM nach Epley; für Bestleistungen und Verlaufsdiagramme. */
fun estimatedOneRepMax(weightKg: Double, reps: Int): Double = when {
    reps <= 0 -> 0.0
    reps == 1 -> weightKg
    else -> weightKg * (1 + reps / 30.0)
}
