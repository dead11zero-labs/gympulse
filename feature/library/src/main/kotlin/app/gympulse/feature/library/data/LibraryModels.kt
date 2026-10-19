package app.gympulse.feature.library.data

import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MuscleGroup
import app.gympulse.core.model.TrackingType

/** Übung für die Liste, dazu ein vorbereiteter Suchtext (alle Sprachen, normalisiert). */
data class LibraryExercise(
    val exercise: Exercise,
    val searchKey: String,
)

/** Übung für die Detailseite inklusive Herkunft und Lizenz der Seed-Daten. */
data class ExerciseDetail(
    val exercise: Exercise,
    val source: String?,
    val license: String?,
    /** True, wenn die Anleitung nur auf Englisch vorliegt, die App aber in einer anderen Sprache läuft. */
    val instructionsInEnglishOnly: Boolean,
)

/** Eingabe zum Anlegen oder Ändern einer eigenen Übung. */
data class ExerciseDraft(
    val id: String?,
    val name: String,
    val category: ExerciseCategory,
    val equipmentType: EquipmentType,
    val trackingType: TrackingType,
    val bodyweightFactor: Double?,
    val primaryMuscles: Set<MuscleGroup>,
    val secondaryMuscles: Set<MuscleGroup>,
    val instructions: List<String>,
    val notes: String?,
    val defaultRestSeconds: Int?,
    val equipmentId: String?,
)

/** Eingabe zum Anlegen oder Ändern eines Geräts. Einstellungen ohne [SettingDraft.id] sind neu. */
data class EquipmentDraft(
    val id: String?,
    val name: String,
    val location: String?,
    val notes: String?,
    val templateKey: String?,
    val settings: List<SettingDraft>,
)

data class SettingDraft(
    val id: String?,
    val key: String,
    val value: String,
)
