package app.gympulse.core.insights

import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MuscleGroup
import app.gympulse.core.model.SetType
import java.time.LocalDate

/** Zielbereich der Wiederholungen pro Satz, z. B. 8–12. */
data class RepRange(val min: Int, val max: Int) {
    init {
        require(min >= 1) { "min must be >= 1, was $min" }
        require(max >= min) { "max ($max) must be >= min ($min)" }
    }
}

/**
 * Stammdaten einer Übung, soweit die Auswertung sie braucht.
 *
 * @property bodyweightFactor Anteil des Körpergewichts, der bewegt wird (z. B. 1,0 bei Klimmzügen),
 *   oder null bei reinen Gewichtsübungen.
 * @property targetRepRange Wiederholungsbereich aus dem Plan; Grundlage der Double Progression.
 * @property targetSets geplante Arbeitssätze aus dem Plan; null = keine Vorgabe.
 * @property weightIncrementKg kleinster Gewichtssprung am Gerät; null = Standard nach [equipmentType].
 */
data class ExerciseProfile(
    val exerciseId: String,
    val equipmentType: EquipmentType = EquipmentType.OTHER,
    val category: ExerciseCategory = ExerciseCategory.STRENGTH,
    val primaryMuscles: List<MuscleGroup> = emptyList(),
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val bodyweightFactor: Double? = null,
    val targetRepRange: RepRange? = null,
    val targetSets: Int? = null,
    val weightIncrementKg: Double? = null,
)

/** Ein erfasster Satz. Gewicht in kg; bei Körpergewichtsübungen das Zusatzgewicht. */
data class PerformedSet(
    val weightKg: Double?,
    val reps: Int?,
    val rpe: Double? = null,
    val setType: SetType = SetType.WORKING,
    val isCompleted: Boolean = true,
)

/** Alle Sätze einer Übung innerhalb einer Session. */
data class ExerciseSession(
    val sessionId: String,
    val date: LocalDate,
    val sets: List<PerformedSet>,
    /** Körpergewicht zum Zeitpunkt der Session; nötig für Körpergewichtsübungen. */
    val bodyweightKg: Double? = null,
)

/** Verlauf einer Übung über mehrere Sessions. Die Reihenfolge von [sessions] ist beliebig. */
data class ExerciseHistory(
    val profile: ExerciseProfile,
    val sessions: List<ExerciseSession>,
)
