package app.gympulse.core.insights

import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.MuscleGroup

/** Alle Schwellwerte der Auswertung an einer Stelle; die Defaults folgen dem Plan "Lokale KI". */
data class InsightsConfig(
    val formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
    /** RPE fließt als Wiederholungen im Tank in das e1RM ein. */
    val useRpeForOneRepMax: Boolean = true,
    val trend: TrendConfig = TrendConfig(),
    val progression: ProgressionConfig = ProgressionConfig(),
    val volume: VolumeConfig = VolumeConfig(),
)

/**
 * @property windowSessions gleitendes Fenster (letzte Sessions) für die Regression.
 * @property minSessions so viele auswertbare Sessions braucht es mindestens für eine Aussage.
 * @property plateauSessions ohne neuen Bestwert seit mindestens so vielen Sessions …
 * @property plateauDays … und seit mindestens so vielen Tagen gilt die Übung als festgefahren.
 * @property flatSlopePerWeek relative Steigung pro Woche (0,005 = 0,5 %), die noch als flach gilt.
 * @property declineSlopePerWeek relative Steigung pro Woche, ab der (negativ) ein Rückgang vorliegt.
 */
data class TrendConfig(
    val windowSessions: Int = 6,
    val minSessions: Int = 4,
    val plateauSessions: Int = 3,
    val plateauDays: Int = 21,
    val flatSlopePerWeek: Double = 0.005,
    val declineSlopePerWeek: Double = 0.01,
) {
    init {
        require(windowSessions >= 2) { "windowSessions must be >= 2" }
        require(minSessions in 2..windowSessions) { "minSessions must be in 2..windowSessions" }
        require(plateauSessions >= 1 && plateauDays >= 1) { "plateau thresholds must be positive" }
        require(flatSlopePerWeek > 0.0 && declineSlopePerWeek > 0.0) { "slope thresholds must be positive" }
    }
}

/**
 * @property maxRpeForIncrease Gewicht wird nur gesteigert, wenn kein erfasster RPE darüber liegt.
 * @property deloadFraction Gewichtsreduktion beim Deload (0,10 = −10 %).
 * @property plateausBeforeDeload so viele aufeinanderfolgende Plateau-Phasen führen zum Deload.
 * @property defaultRepRange Wiederholungsbereich für Übungen ohne Vorgabe aus dem Plan; null = keine
 *   Double Progression ohne Vorgabe.
 * @property incrementsKg kleinster Gewichtssprung je Gerätetyp.
 */
data class ProgressionConfig(
    val maxRpeForIncrease: Double = 8.0,
    val deloadFraction: Double = 0.10,
    val plateausBeforeDeload: Int = 2,
    val defaultRepRange: RepRange? = null,
    val incrementsKg: Map<EquipmentType, Double> = DEFAULT_INCREMENTS_KG,
    val fallbackIncrementKg: Double = 2.5,
) {
    init {
        require(deloadFraction > 0.0 && deloadFraction < 1.0) { "deloadFraction must be in (0, 1)" }
        require(plateausBeforeDeload >= 1) { "plateausBeforeDeload must be >= 1" }
        require(fallbackIncrementKg > 0.0) { "fallbackIncrementKg must be positive" }
    }

    fun incrementFor(profile: ExerciseProfile): Double =
        profile.weightIncrementKg?.takeIf { it > 0.0 }
            ?: incrementsKg[profile.equipmentType]?.takeIf { it > 0.0 }
            ?: fallbackIncrementKg

    companion object {
        val DEFAULT_INCREMENTS_KG: Map<EquipmentType, Double> = mapOf(
            EquipmentType.BARBELL to 2.5,
            EquipmentType.EZ_BAR to 2.5,
            EquipmentType.DUMBBELL to 2.0,
            EquipmentType.KETTLEBELL to 4.0,
            EquipmentType.MACHINE to 5.0,
            EquipmentType.CABLE to 2.5,
            EquipmentType.BODYWEIGHT to 2.5,
        )
    }
}

/** Richtwert für harte Sätze pro Woche und Muskelgruppe. */
data class VolumeTarget(val minSetsPerWeek: Double = 10.0, val maxSetsPerWeek: Double = 20.0) {
    init {
        require(minSetsPerWeek >= 0.0 && maxSetsPerWeek >= minSetsPerWeek) { "invalid volume target" }
    }
}

/**
 * @property windowWeeks Betrachtungszeitraum bis einschließlich Stichtag.
 * @property trackedMuscles werden auch ohne Sätze bewertet (Warnung "zu wenig"), sobald es überhaupt
 *   Trainingsdaten im Zeitraum gibt. Andere Muskelgruppen erscheinen nur, wenn sie trainiert wurden.
 * @property minHardSetRpe Sätze mit erfasstem RPE darunter zählen nicht als harte Sätze; null = aus.
 * @property imbalanceRatio ab diesem Verhältnis (stärkere / schwächere Seite) gilt ein Paar als unausgewogen.
 * @property minSetsForBalance Ungleichgewicht erst prüfen, wenn die stärkere Seite so viele Sätze pro Woche hat.
 */
data class VolumeConfig(
    val windowWeeks: Int = 4,
    val defaultTarget: VolumeTarget = VolumeTarget(),
    val targets: Map<MuscleGroup, VolumeTarget> = emptyMap(),
    val trackedMuscles: Set<MuscleGroup> = DEFAULT_TRACKED_MUSCLES,
    val primaryWeight: Double = 1.0,
    val secondaryWeight: Double = 0.5,
    val minHardSetRpe: Double? = 6.0,
    val imbalanceRatio: Double = 1.5,
    val minSetsForBalance: Double = 6.0,
) {
    init {
        require(windowWeeks >= 1) { "windowWeeks must be >= 1" }
        require(imbalanceRatio > 1.0) { "imbalanceRatio must be > 1" }
    }

    fun targetFor(muscle: MuscleGroup): VolumeTarget = targets[muscle] ?: defaultTarget

    companion object {
        val DEFAULT_TRACKED_MUSCLES: Set<MuscleGroup> = setOf(
            MuscleGroup.CHEST,
            MuscleGroup.LATS,
            MuscleGroup.MIDDLE_BACK,
            MuscleGroup.SHOULDERS,
            MuscleGroup.QUADRICEPS,
            MuscleGroup.HAMSTRINGS,
            MuscleGroup.GLUTES,
        )
    }
}
