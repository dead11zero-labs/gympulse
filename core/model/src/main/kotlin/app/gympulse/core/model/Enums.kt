package app.gympulse.core.model

/** Muskelgruppen; die Keys entsprechen den Seed-Daten (free-exercise-db). */
enum class MuscleGroup(val key: String) {
    ABDOMINALS("abdominals"),
    ABDUCTORS("abductors"),
    ADDUCTORS("adductors"),
    BICEPS("biceps"),
    CALVES("calves"),
    CHEST("chest"),
    FOREARMS("forearms"),
    GLUTES("glutes"),
    HAMSTRINGS("hamstrings"),
    LATS("lats"),
    LOWER_BACK("lower_back"),
    MIDDLE_BACK("middle_back"),
    NECK("neck"),
    QUADRICEPS("quadriceps"),
    SHOULDERS("shoulders"),
    TRAPS("traps"),
    TRICEPS("triceps");

    companion object {
        fun fromKey(key: String): MuscleGroup? = entries.firstOrNull { it.key == key }
    }
}

enum class EquipmentType(val key: String) {
    BARBELL("barbell"),
    DUMBBELL("dumbbell"),
    CABLE("cable"),
    MACHINE("machine"),
    KETTLEBELL("kettlebell"),
    BAND("band"),
    MEDICINE_BALL("medicine_ball"),
    EXERCISE_BALL("exercise_ball"),
    FOAM_ROLL("foam_roll"),
    EZ_BAR("ez_bar"),
    BODYWEIGHT("bodyweight"),
    OTHER("other");

    companion object {
        fun fromKey(key: String): EquipmentType = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

enum class ExerciseCategory(val key: String) {
    STRENGTH("strength"),
    STRETCHING("stretching"),
    PLYOMETRICS("plyometrics"),
    POWERLIFTING("powerlifting"),
    OLYMPIC_WEIGHTLIFTING("olympic_weightlifting"),
    STRONGMAN("strongman"),
    CARDIO("cardio");

    companion object {
        fun fromKey(key: String): ExerciseCategory = entries.firstOrNull { it.key == key } ?: STRENGTH
    }
}

/** Was pro Satz erfasst wird. */
/** Cardio erfasst immer nur die Dauer, egal was für die Übung gespeichert ist. */
fun ExerciseCategory.resolveTracking(requested: TrackingType): TrackingType =
    if (this == ExerciseCategory.CARDIO) TrackingType.DURATION else requested

enum class TrackingType { WEIGHT_REPS, REPS, DURATION, DISTANCE }

enum class SetType { WARMUP, WORKING, DROP, FAILURE }

enum class SessionStatus { ACTIVE, COMPLETED, DISCARDED }

enum class ScheduleOverrideType { MOVED, SKIPPED, ADDED }

enum class MeasurementType { WEIGHT, BODY_FAT, CHEST, WAIST, HIPS, UPPER_ARM, THIGH, CALF, NECK }

enum class MeasurementSource { MANUAL, HEALTH_CONNECT }

enum class PhotoPose { FRONT, SIDE, BACK }

enum class PersonalRecordType { MAX_WEIGHT, EST_1RM, MAX_VOLUME }
