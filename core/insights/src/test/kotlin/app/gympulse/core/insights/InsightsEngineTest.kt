package app.gympulse.core.insights

import app.gympulse.core.insights.Fixtures.START
import app.gympulse.core.insights.Fixtures.bench
import app.gympulse.core.insights.Fixtures.session
import app.gympulse.core.insights.Fixtures.set
import app.gympulse.core.insights.Fixtures.sets
import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MuscleGroup
import app.gympulse.core.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ende-zu-Ende-Tests mit Fixture-Historien über acht Wochen. */
class InsightsEngineTest {

    private val engine = InsightsEngine()

    /** Bankdrücken: jede Woche +1 Wdh., nach 12 Wdh. +2,5 kg – klassische Double Progression. */
    private val benchHistory = ExerciseHistory(
        profile = bench(targetSets = 3),
        sessions = listOf(
            8 to 80.0, 9 to 80.0, 10 to 80.0, 11 to 80.0, 12 to 80.0,
            8 to 82.5, 9 to 82.5, 10 to 82.5,
        ).mapIndexed { week, (reps, kg) ->
            session(START.plusDays(week * 7L), listOf(set(40.0, 10, type = SetType.WARMUP)) + sets(3, kg, reps))
        },
    )

    /** Kniebeuge: seit Woche 1 bei 100 kg × 5, das e1RM steht seit acht Wochen. */
    private val squatProfile = ExerciseProfile(
        exerciseId = "squat",
        equipmentType = EquipmentType.BARBELL,
        primaryMuscles = listOf(MuscleGroup.QUADRICEPS),
        secondaryMuscles = listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
        targetRepRange = RepRange(5, 8),
    )
    private val squatHistory = ExerciseHistory(
        profile = squatProfile,
        sessions = List(8) { week ->
            val reps = if (week % 2 == 0) 5 else 4
            session(START.plusDays(week * 7L), sets(3, 100.0, reps).let { if (week == 0) sets(3, 100.0, 6) else it })
        },
    )

    /** Rudern: wenig Volumen, damit Druck/Zug unausgewogen ist. */
    private val rowHistory = ExerciseHistory(
        profile = ExerciseProfile(
            exerciseId = "row",
            equipmentType = EquipmentType.CABLE,
            primaryMuscles = listOf(MuscleGroup.MIDDLE_BACK),
            secondaryMuscles = listOf(MuscleGroup.LATS, MuscleGroup.BICEPS),
            targetRepRange = RepRange(10, 15),
        ),
        sessions = List(8) { week -> session(START.plusDays(week * 7L + 2), sets(2, 50.0, 12)) },
    )

    private val asOf = START.plusDays(7L * 7 + 6) // Sonntag der achten Woche

    @Test
    fun fullAnalysis_producesExpectedFindingsInPriorityOrder() {
        val insights = engine.analyze(listOf(benchHistory, squatHistory, rowHistory), asOf)

        val bench = insights.exercise("bench")!!
        assertEquals(TrendStatus.PROGRESSING, bench.trend.status)
        assertNull(bench.deload)
        assertEquals(ProgressionAction.INCREASE_REPS, bench.progression!!.action)
        assertEquals(11, bench.progression!!.targetReps)
        assertEquals(OneRepMax.epley(82.5, 10.0), bench.latestE1rmKg!!, 1e-9)

        val squat = insights.exercise("squat")!!
        assertEquals(TrendStatus.PLATEAU, squat.trend.status)
        assertEquals(DeloadReason.REPEATED_PLATEAU, squat.deload)
        assertEquals(ProgressionAction.DELOAD, squat.progression!!.action)
        assertEquals(90.0, squat.progression!!.suggestedWeightKg, 0.0)

        val codes = insights.findings.map { it.code }
        assertEquals(codes.sortedBy { it.ordinal }, codes)
        assertEquals(InsightCode.DELOAD, codes.first())
        assertTrue(InsightCode.PLATEAU in codes)
        assertTrue(InsightCode.IMBALANCE in codes)
        assertTrue(InsightCode.LOW_VOLUME in codes)
        assertTrue(InsightCode.INCREASE_REPS in codes)
        assertTrue(InsightCode.PROGRESS in codes)

        val imbalance = insights.findings.filterIsInstance<Insight.Imbalance>().single()
        assertEquals(BalancePair.PUSH_PULL, imbalance.imbalance.pair)
        assertEquals(BalanceSide.A, imbalance.imbalance.dominantSide)

        val lowMuscles = insights.findings.filterIsInstance<Insight.Volume>()
            .filter { it.code == InsightCode.LOW_VOLUME }
            .map { it.volume.muscle }
        assertTrue(MuscleGroup.QUADRICEPS in lowMuscles) // 3 Sätze pro Woche
        assertTrue(MuscleGroup.MIDDLE_BACK in lowMuscles) // 2 Sätze pro Woche
    }

    @Test
    fun findingsForExercise() {
        val insights = engine.analyze(listOf(benchHistory, squatHistory), asOf)
        val squatCodes = insights.findingsFor("squat").map { it.code }
        assertEquals(listOf(InsightCode.DELOAD, InsightCode.PLATEAU), squatCodes)
        val benchCodes = insights.findingsFor("bench").map { it.code }
        assertEquals(listOf(InsightCode.INCREASE_REPS, InsightCode.PROGRESS), benchCodes)
        assertTrue(insights.findingsFor("unknown").isEmpty())
    }

    @Test
    fun sessionsAfterAsOfAreIgnored() {
        // Stichtag nach Woche 5: Bankdrücken 3 × 12 geschafft → Gewicht erhöhen
        val insights = engine.analyze(listOf(benchHistory), START.plusDays(4 * 7L + 1))
        val progression = insights.exercise("bench")!!.progression!!
        assertEquals(ProgressionAction.INCREASE_WEIGHT, progression.action)
        assertEquals(82.5, progression.suggestedWeightKg, 0.0)
        assertEquals(5, insights.exercise("bench")!!.trend.sampleSize)
    }

    @Test
    fun newUser_withFewSessions_getsNoTrendButProgression() {
        val history = ExerciseHistory(bench(), listOf(session(START, sets(3, 60.0, 12))))
        val insights = engine.analyze(listOf(history), START)
        val exercise = insights.exercise("bench")!!
        assertEquals(TrendStatus.INSUFFICIENT_DATA, exercise.trend.status)
        assertEquals(ProgressionAction.INCREASE_WEIGHT, exercise.progression!!.action)
        assertTrue(insights.findings.none { it is Insight.Trend })
    }

    @Test
    fun exercisesWithoutWorkSetsAreSkipped() {
        val warmupOnly = ExerciseHistory(bench(), listOf(session(START, listOf(set(40.0, 10, type = SetType.WARMUP)))))
        val insights = engine.analyze(listOf(warmupOnly), START)
        assertTrue(insights.exercises.isEmpty())
        assertTrue(insights.findings.isEmpty())
        assertTrue(!insights.volume.hasData)
    }

    @Test
    fun stretchingHasNoExerciseInsight() {
        val stretch = ExerciseHistory(
            ExerciseProfile("stretch", category = ExerciseCategory.STRETCHING, targetRepRange = RepRange(10, 10)),
            listOf(session(START, sets(2, 5.0, 10))),
        )
        assertNull(engine.analyze(listOf(stretch), START).exercise("stretch"))
    }

    @Test
    fun emptyInput() {
        val insights = engine.analyze(emptyList(), START)
        assertEquals(START, insights.asOf)
        assertTrue(insights.exercises.isEmpty())
        assertTrue(insights.findings.isEmpty())
    }

    @Test
    fun sessionOrderDoesNotMatter() {
        val input = listOf(benchHistory, squatHistory, rowHistory)
        val shuffled = input.map { it.copy(sessions = it.sessions.reversed()) }
        assertEquals(engine.analyze(input, asOf), engine.analyze(shuffled, asOf))
    }

    @Test
    fun customConfigIsApplied() {
        val lenient = InsightsEngine(
            InsightsConfig(progression = ProgressionConfig(plateausBeforeDeload = 5)),
        )
        val squat = lenient.analyze(listOf(squatHistory), asOf).exercise("squat")!!
        assertEquals(TrendStatus.PLATEAU, squat.trend.status)
        assertNull(squat.deload)
        assertNotNull(squat.progression)
        assertTrue(squat.progression!!.action != ProgressionAction.DELOAD)
    }
}
