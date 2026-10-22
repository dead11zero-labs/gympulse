package app.gympulse.core.data.health

import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MuscleGroup
import java.util.Locale

/**
 * Ordnet einer Übung den passendsten [HealthSegmentType] zu.
 *
 * Grundlage ist der Slug der Seed-Daten (free-exercise-db, z. B. `barbell_bench_press_medium_grip`),
 * bei eigenen Übungen ohne Slug der Name (englische und deutsche Stichwörter). Die Regeln werden
 * der Reihe nach geprüft, die erste passende gewinnt; spezifische Stichwörter stehen deshalb vor
 * allgemeinen (`leg_press` vor `press`, `leg_curl` vor `curl`). Ohne Treffer entscheiden Kategorie,
 * Hauptmuskel und Equipment: Dehnen → [HealthSegmentType.STRETCHING], Übungen mit Zusatzgewicht →
 * [HealthSegmentType.WEIGHTLIFTING], sonst [HealthSegmentType.UNKNOWN].
 */
object HealthSegmentClassifier {

    fun classify(exercise: Exercise): HealthSegmentType = classify(
        slug = exercise.slug,
        name = exercise.name,
        category = exercise.category,
        equipment = exercise.equipmentType,
        primaryMuscles = exercise.primaryMuscles,
    )

    fun classify(
        slug: String?,
        name: String,
        category: ExerciseCategory,
        equipment: EquipmentType,
        primaryMuscles: Collection<MuscleGroup>,
    ): HealthSegmentType {
        val text = normalize(slug?.takeIf { it.isNotBlank() } ?: name)
        val input = Input(text, category, equipment, primaryMuscles.toSet())
        return RULES.firstNotNullOfOrNull { rule -> rule(input) } ?: fallback(input)
    }

    /** Kleinbuchstaben, Wortgrenzen als `_`, vorne und hinten ein `_`, damit `_row_` nur ganze Wörter trifft. */
    internal fun normalize(value: String): String =
        "_" + value.lowercase(Locale.ROOT).replace(NON_WORD, "_").trim('_') + "_"

    private class Input(
        val text: String,
        val category: ExerciseCategory,
        val equipment: EquipmentType,
        val primaryMuscles: Set<MuscleGroup>,
    ) {
        fun has(vararg keys: String): Boolean = keys.any { it in text }
    }

    private fun fallback(input: Input): HealthSegmentType = when {
        input.category == ExerciseCategory.STRETCHING -> HealthSegmentType.STRETCHING
        input.equipment in LOADED_EQUIPMENT -> HealthSegmentType.WEIGHTLIFTING
        else -> HealthSegmentType.UNKNOWN
    }

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    private val LOADED_EQUIPMENT = setOf(
        EquipmentType.BARBELL,
        EquipmentType.DUMBBELL,
        EquipmentType.CABLE,
        EquipmentType.MACHINE,
        EquipmentType.KETTLEBELL,
        EquipmentType.EZ_BAR,
        EquipmentType.BAND,
        EquipmentType.MEDICINE_BALL,
    )

    private val RULES: List<(Input) -> HealthSegmentType?> = listOf(
        // Dehnen und Faszienrolle
        { i ->
            HealthSegmentType.STRETCHING.takeIf {
                i.category == ExerciseCategory.STRETCHING || i.has("stretch", "_smr_", "dehnen", "dehnung")
            }
        },
        // Ausdauergeräte und -formen
        { i -> HealthSegmentType.RUNNING_TREADMILL.takeIf { i.has("treadmill", "laufband") } },
        { i ->
            HealthSegmentType.BIKING_STATIONARY.takeIf {
                i.has("stationary_bike", "bicycling_stationary", "recumbent_bike", "ergometer", "heimtrainer", "spinning")
            }
        },
        { i -> HealthSegmentType.ELLIPTICAL.takeIf { i.has("elliptical", "crosstrainer", "cross_trainer") } },
        { i ->
            HealthSegmentType.ROWING_MACHINE.takeIf {
                i.has("rowing_stationary", "rowing_machine", "rudergerät", "rudergeraet")
            }
        },
        { i ->
            HealthSegmentType.STAIR_CLIMBING_MACHINE.takeIf {
                i.has("stairmaster", "step_mill", "stair_climb", "stepper", "treppen")
            }
        },
        { i -> HealthSegmentType.RUNNING.takeIf { i.has("jogging", "_running_", "_laufen_", "_joggen_") } },
        { i -> HealthSegmentType.WALKING.takeIf { i.has("_walking_", "_gehen_", "spazieren") } },
        { i -> HealthSegmentType.BIKING.takeIf { i.has("bicycling", "_cycling_", "_biking_", "radfahren") } },
        { i -> HealthSegmentType.JUMP_ROPE.takeIf { i.has("rope_jumping", "jump_rope", "seilspringen") } },
        { i -> HealthSegmentType.JUMPING_JACK.takeIf { i.has("jumping_jack", "hampelmann") } },
        { i -> HealthSegmentType.BURPEE.takeIf { i.has("burpee") } },
        { i -> HealthSegmentType.MOUNTAIN_CLIMBER.takeIf { i.has("mountain_climber", "bergsteiger") } },
        { i -> HealthSegmentType.BALL_SLAM.takeIf { i.has("ball_slam", "_slam") } },
        { i ->
            HealthSegmentType.KETTLEBELL_SWING.takeIf {
                i.has("kettlebell_swing") || (i.has("swing") && i.equipment == EquipmentType.KETTLEBELL)
            }
        },
        // Grundübungen
        { i -> HealthSegmentType.DEADLIFT.takeIf { i.has("deadlift", "rack_pull", "kreuzheben") } },
        { i -> HealthSegmentType.LEG_PRESS.takeIf { i.has("leg_press", "beinpresse") } },
        { i -> HealthSegmentType.LUNGE.takeIf { i.has("lunge", "split_squat", "ausfallschritt") } },
        { i -> HealthSegmentType.SQUAT.takeIf { i.has("squat", "kniebeuge") } },
        { i ->
            HealthSegmentType.HIP_THRUST.takeIf {
                i.has("hip_thrust", "glute_bridge", "hip_lift", "butt_lift", "hüftheben", "hueftheben")
            }
        },
        { i -> HealthSegmentType.LAT_PULL_DOWN.takeIf { i.has("pulldown", "pull_down", "latzug", "latziehen") } },
        { i ->
            HealthSegmentType.PULL_UP.takeIf {
                i.has("pull_up", "pullup", "chin_up", "chinup", "_chin_", "_chins_", "muscle_up", "klimmz")
            }
        },
        { i ->
            HealthSegmentType.BENCH_PRESS.takeIf {
                i.has("bench_press", "floor_press", "chest_press", "bankdrücken", "bankdruecken", "brustpresse")
            }
        },
        { i ->
            if (i.has("shoulder_press", "military_press", "overhead_press", "push_press", "arnold", "schulterdrücken", "schulterdruecken", "schulterpresse")) {
                shoulderPress(i)
            } else {
                null
            }
        },
        // Trizepsdrücken vor der allgemeinen Drück-Regel
        { i -> tricepsExtension(i) },
        { i ->
            when {
                !i.has("press") || i.has("push_up", "pushup") -> null
                MuscleGroup.CHEST in i.primaryMuscles -> HealthSegmentType.BENCH_PRESS
                MuscleGroup.SHOULDERS in i.primaryMuscles -> shoulderPress(i)
                else -> null
            }
        },
        // Isolationsübungen
        { i ->
            HealthSegmentType.LEG_CURL.takeIf {
                i.has("leg_curl", "hamstring_curl", "beinbeuger") ||
                    (i.has("curl") && MuscleGroup.HAMSTRINGS in i.primaryMuscles)
            }
        },
        { i -> HealthSegmentType.LEG_EXTENSION.takeIf { i.has("leg_extension", "beinstrecker") } },
        { i ->
            HealthSegmentType.ARM_CURL.takeIf {
                i.has("bizeps", "bicep") ||
                    (i.has("curl") && i.primaryMuscles.any { it == MuscleGroup.BICEPS || it == MuscleGroup.FOREARMS })
            }
        },
        { i ->
            HealthSegmentType.BACK_EXTENSION.takeIf {
                i.has("back_extension", "hyperextension", "rückenstrecker", "rueckenstrecker")
            }
        },
        { i ->
            HealthSegmentType.DUMBBELL_ROW.takeIf {
                i.has("_row_", "_rows_", "rudern") && !i.has("upright") &&
                    (i.equipment == EquipmentType.DUMBBELL || i.equipment == EquipmentType.KETTLEBELL)
            }
        },
        { i ->
            if (i.has("lateral_raise", "side_lateral", "deltoid_raise", "seitheben")) {
                if (i.equipment == EquipmentType.DUMBBELL) {
                    HealthSegmentType.DUMBBELL_LATERAL_RAISE
                } else {
                    HealthSegmentType.LATERAL_RAISE
                }
            } else {
                null
            }
        },
        { i ->
            if (i.has("front_raise", "frontheben") || (i.has("front") && i.has("raise"))) {
                if (i.equipment == EquipmentType.DUMBBELL) {
                    HealthSegmentType.DUMBBELL_FRONT_RAISE
                } else {
                    HealthSegmentType.FRONT_RAISE
                }
            } else {
                null
            }
        },
        { i -> HealthSegmentType.LEG_RAISE.takeIf { i.has("leg_raise", "knee_raise", "leg_lift", "beinheben", "knieheben") } },
        { i -> HealthSegmentType.CRUNCH.takeIf { i.has("crunch") } },
        { i ->
            when {
                !i.has("sit_up", "situp") -> null
                i.has("bench", "decline") -> HealthSegmentType.BENCH_SIT_UP
                else -> HealthSegmentType.SIT_UP
            }
        },
        { i -> HealthSegmentType.PLANK.takeIf { i.has("plank", "unterarmstütz", "unterarmstuetz") } },
        { i -> HealthSegmentType.UPPER_TWIST.takeIf { i.has("twist") && !i.has("fly") } },
        { i -> HealthSegmentType.HULA_HOOP.takeIf { i.has("hula") } },
    )

    private fun shoulderPress(i: Input): HealthSegmentType =
        if (i.equipment == EquipmentType.BARBELL) {
            HealthSegmentType.BARBELL_SHOULDER_PRESS
        } else {
            HealthSegmentType.SHOULDER_PRESS
        }

    private fun tricepsExtension(i: Input): HealthSegmentType? {
        val triceps = MuscleGroup.TRICEPS in i.primaryMuscles || i.has("trizeps", "tricep")
        val extension = i.has(
            "extension", "skull", "pushdown", "push_down", "kickback", "french", "tate_press",
            "triceps_press", "tricep_press", "jm_press", "trizepsdrücken", "trizepsdruecken",
        )
        if (!triceps || !extension) return null
        return when {
            i.has("one_arm", "single_arm", "einarmig") -> HealthSegmentType.SINGLE_ARM_TRICEPS_EXTENSION
            i.equipment == EquipmentType.DUMBBELL -> HealthSegmentType.DUMBBELL_TRICEPS_EXTENSION_TWO_ARM
            else -> HealthSegmentType.DOUBLE_ARM_TRICEPS_EXTENSION
        }
    }
}
