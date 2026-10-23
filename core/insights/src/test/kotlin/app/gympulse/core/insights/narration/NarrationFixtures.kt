package app.gympulse.core.insights.narration

import app.gympulse.core.insights.ExerciseHistory
import app.gympulse.core.insights.ExerciseProfile
import app.gympulse.core.insights.Fixtures.START
import app.gympulse.core.insights.Fixtures.session
import app.gympulse.core.insights.Fixtures.set
import app.gympulse.core.insights.Fixtures.sets
import app.gympulse.core.insights.InsightsEngine
import app.gympulse.core.insights.RepRange
import app.gympulse.core.insights.TrainingInsights
import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.MuscleGroup
import app.gympulse.core.model.SetType

/** Acht-Wochen-Historien mit bewusst unverwechselbaren IDs und Warm-up-Gewichten (Rohdaten-Tests). */
internal object NarrationFixtures {
    const val BENCH_ID = "11111111-aaaa-4bbb-8ccc-benchbenchbe"
    const val SQUAT_ID = "22222222-aaaa-4bbb-8ccc-squatsquatsq"
    const val ROW_ID = "33333333-aaaa-4bbb-8ccc-rowrowrowrowr"
    const val WARMUP_KG = 41.5

    val names = mapOf(BENCH_ID to "Bench Press", SQUAT_ID to "Back Squat", ROW_ID to "Cable Row")

    private val bench = ExerciseHistory(
        profile = ExerciseProfile(
            exerciseId = BENCH_ID,
            equipmentType = EquipmentType.BARBELL,
            primaryMuscles = listOf(MuscleGroup.CHEST),
            secondaryMuscles = listOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
            targetRepRange = RepRange(8, 12),
            targetSets = 3,
        ),
        sessions = listOf(
            8 to 80.0, 9 to 80.0, 10 to 80.0, 11 to 80.0, 12 to 80.0,
            8 to 82.5, 9 to 82.5, 10 to 82.5,
        ).mapIndexed { week, (reps, kg) ->
            session(
                START.plusDays(week * 7L),
                listOf(set(WARMUP_KG, 10, type = SetType.WARMUP)) + sets(3, kg, reps),
                id = "session-secret-$week",
            )
        },
    )

    private val squat = ExerciseHistory(
        profile = ExerciseProfile(
            exerciseId = SQUAT_ID,
            equipmentType = EquipmentType.BARBELL,
            primaryMuscles = listOf(MuscleGroup.QUADRICEPS),
            secondaryMuscles = listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            targetRepRange = RepRange(5, 8),
        ),
        sessions = List(8) { week ->
            val reps = if (week % 2 == 0) 5 else 4
            session(START.plusDays(week * 7L), sets(3, 100.0, if (week == 0) 6 else reps), id = "session-secret-sq-$week")
        },
    )

    private val row = ExerciseHistory(
        profile = ExerciseProfile(
            exerciseId = ROW_ID,
            equipmentType = EquipmentType.CABLE,
            primaryMuscles = listOf(MuscleGroup.MIDDLE_BACK),
            secondaryMuscles = listOf(MuscleGroup.LATS, MuscleGroup.BICEPS),
            targetRepRange = RepRange(10, 15),
        ),
        sessions = List(8) { week -> session(START.plusDays(week * 7L + 2), sets(2, 50.0, 12), id = "session-secret-r-$week") },
    )

    val asOf = START.plusDays(7L * 7 + 6)

    val histories = listOf(bench, squat, row)

    fun insights(): TrainingInsights = InsightsEngine().analyze(histories, asOf)

    /** Viele Übungen, um das Token-Budget zu belasten. */
    fun manyExercisesInsights(count: Int): Pair<TrainingInsights, Map<String, String>> {
        val muscles = MuscleGroup.entries
        val list = List(count) { i ->
            ExerciseHistory(
                profile = ExerciseProfile(
                    exerciseId = "ex-$i",
                    equipmentType = EquipmentType.BARBELL,
                    primaryMuscles = listOf(muscles[i % muscles.size]),
                    secondaryMuscles = listOf(muscles[(i + 3) % muscles.size]),
                    targetRepRange = RepRange(8, 12),
                ),
                sessions = List(8) { week -> session(START.plusDays(week * 7L), sets(3, 60.0 + i, 8 + (week % 5)), id = "s-$i-$week") },
            )
        }
        val names = list.associate { it.profile.exerciseId to "Very Long Exercise Name Number ${it.profile.exerciseId}" }
        return InsightsEngine().analyze(list, asOf) to names
    }
}
