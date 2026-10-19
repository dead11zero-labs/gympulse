package app.gympulse.core.model

/** Vorlage für ein Gerät (z. B. Beinpresse) mit den üblichen Einstellmöglichkeiten. */
data class EquipmentTemplate(
    val key: String,
    val equipmentType: EquipmentType,
    val name: String,
    val muscleGroups: List<MuscleGroup>,
    val settingKeys: List<EquipmentTemplateSetting>,
)

data class EquipmentTemplateSetting(
    val key: String,
    val name: String,
    /** Platzhalter für das Eingabefeld, z. B. "z. B. 5". */
    val valueHint: String,
)
