package app.gympulse.core.data.ui

import app.gympulse.core.data.R
import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.MuscleGroup

/** Lokalisierter Anzeigename der Muskelgruppe (Ressource `muscle_<key>`). */
fun MuscleGroup.labelRes(): Int = when (this) {
    MuscleGroup.ABDOMINALS -> R.string.muscle_abdominals
    MuscleGroup.ABDUCTORS -> R.string.muscle_abductors
    MuscleGroup.ADDUCTORS -> R.string.muscle_adductors
    MuscleGroup.BICEPS -> R.string.muscle_biceps
    MuscleGroup.CALVES -> R.string.muscle_calves
    MuscleGroup.CHEST -> R.string.muscle_chest
    MuscleGroup.FOREARMS -> R.string.muscle_forearms
    MuscleGroup.GLUTES -> R.string.muscle_glutes
    MuscleGroup.HAMSTRINGS -> R.string.muscle_hamstrings
    MuscleGroup.LATS -> R.string.muscle_lats
    MuscleGroup.LOWER_BACK -> R.string.muscle_lower_back
    MuscleGroup.MIDDLE_BACK -> R.string.muscle_middle_back
    MuscleGroup.NECK -> R.string.muscle_neck
    MuscleGroup.QUADRICEPS -> R.string.muscle_quadriceps
    MuscleGroup.SHOULDERS -> R.string.muscle_shoulders
    MuscleGroup.TRAPS -> R.string.muscle_traps
    MuscleGroup.TRICEPS -> R.string.muscle_triceps
}

/** Lokalisierter Anzeigename des Gerätetyps (Ressource `equipment_<key>`). */
fun EquipmentType.labelRes(): Int = when (this) {
    EquipmentType.BARBELL -> R.string.equipment_barbell
    EquipmentType.DUMBBELL -> R.string.equipment_dumbbell
    EquipmentType.CABLE -> R.string.equipment_cable
    EquipmentType.MACHINE -> R.string.equipment_machine
    EquipmentType.KETTLEBELL -> R.string.equipment_kettlebell
    EquipmentType.BAND -> R.string.equipment_band
    EquipmentType.MEDICINE_BALL -> R.string.equipment_medicine_ball
    EquipmentType.EXERCISE_BALL -> R.string.equipment_exercise_ball
    EquipmentType.FOAM_ROLL -> R.string.equipment_foam_roll
    EquipmentType.EZ_BAR -> R.string.equipment_ez_bar
    EquipmentType.BODYWEIGHT -> R.string.equipment_bodyweight
    EquipmentType.OTHER -> R.string.equipment_other
}
