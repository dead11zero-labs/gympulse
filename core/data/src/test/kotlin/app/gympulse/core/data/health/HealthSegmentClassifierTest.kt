package app.gympulse.core.data.health

import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthSegmentClassifierTest {

    private fun classify(
        slug: String?,
        name: String = slug ?: "",
        category: ExerciseCategory = ExerciseCategory.STRENGTH,
        equipment: EquipmentType = EquipmentType.BARBELL,
        muscles: List<MuscleGroup> = emptyList(),
    ) = HealthSegmentClassifier.classify(slug, name, category, equipment, muscles)

    @Test
    fun seedSlugsMapToSpecificSegmentTypes() {
        val expected = mapOf(
            "barbell_bench_press_medium_grip" to HealthSegmentType.BENCH_PRESS,
            "dumbbell_bench_press" to HealthSegmentType.BENCH_PRESS,
            "barbell_squat" to HealthSegmentType.SQUAT,
            "hack_squat" to HealthSegmentType.SQUAT,
            "romanian_deadlift" to HealthSegmentType.DEADLIFT,
            "rack_pulls" to HealthSegmentType.DEADLIFT,
            "wide_grip_lat_pulldown" to HealthSegmentType.LAT_PULL_DOWN,
            "45_degree_leg_press" to HealthSegmentType.LEG_PRESS,
            "dumbbell_lunges" to HealthSegmentType.LUNGE,
            "split_squat_with_dumbbells" to HealthSegmentType.LUNGE,
            "pullups" to HealthSegmentType.PULL_UP,
            "chin_up" to HealthSegmentType.PULL_UP,
            "seated_leg_curl" to HealthSegmentType.LEG_CURL,
            "leg_extensions" to HealthSegmentType.LEG_EXTENSION,
            "barbell_hip_thrust" to HealthSegmentType.HIP_THRUST,
            "hanging_leg_raise" to HealthSegmentType.LEG_RAISE,
            "cable_crunch" to HealthSegmentType.CRUNCH,
            "plank" to HealthSegmentType.PLANK,
            "russian_twist" to HealthSegmentType.UPPER_TWIST,
            "one_arm_kettlebell_swings" to HealthSegmentType.KETTLEBELL_SWING,
            "back_extension_machine" to HealthSegmentType.BACK_EXTENSION,
        )
        expected.forEach { (slug, type) -> assertEquals(slug, type, classify(slug)) }
    }

    @Test
    fun equipmentRefinesPressesRaisesAndRows() {
        assertEquals(
            HealthSegmentType.BARBELL_SHOULDER_PRESS,
            classify("standing_military_press", equipment = EquipmentType.BARBELL),
        )
        assertEquals(
            HealthSegmentType.SHOULDER_PRESS,
            classify("seated_dumbbell_press", equipment = EquipmentType.DUMBBELL, muscles = listOf(MuscleGroup.SHOULDERS)),
        )
        assertEquals(
            HealthSegmentType.BENCH_PRESS,
            classify("leverage_chest_press", equipment = EquipmentType.MACHINE, muscles = listOf(MuscleGroup.CHEST)),
        )
        assertEquals(
            HealthSegmentType.DUMBBELL_LATERAL_RAISE,
            classify("side_lateral_raise", equipment = EquipmentType.DUMBBELL),
        )
        assertEquals(HealthSegmentType.LATERAL_RAISE, classify("cable_lateral_raise", equipment = EquipmentType.CABLE))
        assertEquals(
            HealthSegmentType.DUMBBELL_FRONT_RAISE,
            classify("front_dumbbell_raise", equipment = EquipmentType.DUMBBELL),
        )
        assertEquals(
            HealthSegmentType.DUMBBELL_ROW,
            classify("one_arm_dumbbell_row", equipment = EquipmentType.DUMBBELL),
        )
        // Für Langhantel- und Kabelrudern hat Health Connect keinen eigenen Typ.
        assertEquals(HealthSegmentType.WEIGHTLIFTING, classify("bent_over_barbell_row"))
        assertEquals(HealthSegmentType.WEIGHTLIFTING, classify("seated_cable_rows", equipment = EquipmentType.CABLE))
        // "throw" enthält "row", ist aber kein Rudern.
        assertEquals(
            HealthSegmentType.WEIGHTLIFTING,
            classify("medicine_ball_scoop_throw", equipment = EquipmentType.DUMBBELL),
        )
    }

    @Test
    fun musclesDecideCurlsAndTricepsExtensions() {
        assertEquals(
            HealthSegmentType.ARM_CURL,
            classify("hammer_curls", equipment = EquipmentType.DUMBBELL, muscles = listOf(MuscleGroup.BICEPS)),
        )
        assertEquals(
            HealthSegmentType.LEG_CURL,
            classify("ball_leg_curl", equipment = EquipmentType.EXERCISE_BALL, muscles = listOf(MuscleGroup.HAMSTRINGS)),
        )
        assertEquals(
            HealthSegmentType.DOUBLE_ARM_TRICEPS_EXTENSION,
            classify("triceps_pushdown", equipment = EquipmentType.CABLE, muscles = listOf(MuscleGroup.TRICEPS)),
        )
        assertEquals(
            HealthSegmentType.SINGLE_ARM_TRICEPS_EXTENSION,
            classify(
                "one_arm_dumbbell_triceps_extension",
                equipment = EquipmentType.DUMBBELL,
                muscles = listOf(MuscleGroup.TRICEPS),
            ),
        )
        assertEquals(
            HealthSegmentType.DUMBBELL_TRICEPS_EXTENSION_TWO_ARM,
            classify("tate_press", equipment = EquipmentType.DUMBBELL, muscles = listOf(MuscleGroup.TRICEPS)),
        )
    }

    @Test
    fun customExercisesWithoutSlugUseGermanOrEnglishNames() {
        assertEquals(HealthSegmentType.BENCH_PRESS, classify(slug = null, name = "Bankdrücken (Maschine)"))
        assertEquals(HealthSegmentType.LAT_PULL_DOWN, classify(slug = null, name = "Latzug eng"))
        assertEquals(HealthSegmentType.LEG_PRESS, classify(slug = null, name = "Beinpresse 45°"))
        assertEquals(HealthSegmentType.SQUAT, classify(slug = null, name = "Kniebeuge"))
        assertEquals(HealthSegmentType.DEADLIFT, classify(slug = null, name = "Kreuzheben rumänisch"))
        assertEquals(HealthSegmentType.PULL_UP, classify(slug = null, name = "Klimmzüge"))
        assertEquals(HealthSegmentType.SHOULDER_PRESS, classify(slug = null, name = "Schulterpresse", equipment = EquipmentType.MACHINE))
        assertEquals(HealthSegmentType.LEG_EXTENSION, classify(slug = null, name = "Beinstrecker"))
        assertEquals(HealthSegmentType.LEG_CURL, classify(slug = null, name = "Beinbeuger sitzend"))
        assertEquals(HealthSegmentType.ARM_CURL, classify(slug = null, name = "Bizepscurls"))
        assertEquals(HealthSegmentType.BENCH_PRESS, classify(slug = null, name = "Chest Press", equipment = EquipmentType.MACHINE))
    }

    @Test
    fun cardioAndStretchingGetTheirOwnTypes() {
        assertEquals(
            HealthSegmentType.RUNNING_TREADMILL,
            classify("running_treadmill", category = ExerciseCategory.CARDIO, equipment = EquipmentType.MACHINE),
        )
        assertEquals(
            HealthSegmentType.BIKING_STATIONARY,
            classify("bicycling_stationary", category = ExerciseCategory.CARDIO, equipment = EquipmentType.MACHINE),
        )
        assertEquals(
            HealthSegmentType.ROWING_MACHINE,
            classify("rowing_stationary", category = ExerciseCategory.CARDIO, equipment = EquipmentType.MACHINE),
        )
        assertEquals(
            HealthSegmentType.ELLIPTICAL,
            classify(slug = null, name = "Crosstrainer", category = ExerciseCategory.CARDIO, equipment = EquipmentType.MACHINE),
        )
        assertEquals(
            HealthSegmentType.STRETCHING,
            classify("chest_stretch_on_stability_ball", category = ExerciseCategory.STRETCHING, equipment = EquipmentType.OTHER),
        )
    }

    @Test
    fun unmatchedExercisesFallBackByEquipment() {
        // Health Connect kennt weder Liegestütze noch Dips.
        assertEquals(
            HealthSegmentType.UNKNOWN,
            classify("pushups", equipment = EquipmentType.BODYWEIGHT, muscles = listOf(MuscleGroup.CHEST)),
        )
        assertEquals(HealthSegmentType.UNKNOWN, classify("parallel_bar_dip", equipment = EquipmentType.BODYWEIGHT))
        assertEquals(HealthSegmentType.WEIGHTLIFTING, classify("power_clean"))
        assertEquals(
            HealthSegmentType.WEIGHTLIFTING,
            classify("butterfly", equipment = EquipmentType.MACHINE, muscles = listOf(MuscleGroup.CHEST)),
        )
        assertEquals(HealthSegmentType.UNKNOWN, classify(slug = null, name = "Mein Spezialtraining", equipment = EquipmentType.OTHER))
    }

    @Test
    fun normalizeMarksWordBoundaries() {
        assertEquals("_bankdrücken_maschine_", HealthSegmentClassifier.normalize(" Bankdrücken (Maschine) "))
        assertEquals("_3_4_sit_up_", HealthSegmentClassifier.normalize("3_4_sit_up"))
    }
}
