package app.gympulse.core.insights

import app.gympulse.core.model.ExerciseCategory
import java.time.LocalDate

/**
 * Einstiegspunkt der klassischen Auswertung: bündelt e1RM-Trend, Plateau/Deload, Double Progression
 * und Volumen zu einem [TrainingInsights]-Objekt. Zustandslos und deterministisch.
 */
class InsightsEngine(private val config: InsightsConfig = InsightsConfig()) {

    /**
     * Wertet alle Übungen bis einschließlich [asOf] aus; spätere Sessions werden ignoriert.
     */
    fun analyze(histories: List<ExerciseHistory>, asOf: LocalDate): TrainingInsights {
        val relevant = histories.map { history ->
            history.copy(sessions = history.sessions.filter { !it.date.isAfter(asOf) })
        }

        val exercises = relevant
            .filter { it.profile.category !in NON_STRENGTH_CATEGORIES }
            .mapNotNull { analyzeExercise(it) }
        val volume = VolumeAnalyzer.analyze(relevant, asOf, config.volume)

        val findings = buildList {
            for (exercise in exercises) {
                if (exercise.trend.status != TrendStatus.INSUFFICIENT_DATA) {
                    add(Insight.Trend(exercise.exerciseId, exercise.trend))
                }
                exercise.progression?.let { add(Insight.Progression(it)) }
            }
            volume.imbalances.forEach { add(Insight.Imbalance(it)) }
            volume.muscles
                .filter { it.status != VolumeStatus.OPTIMAL }
                .forEach { add(Insight.Volume(it)) }
        }.sortedBy { it.code.ordinal }

        return TrainingInsights(asOf, exercises, volume, findings)
    }

    /** Auswertung einer einzelnen Übung oder null, wenn es keine Arbeitssätze gibt. */
    fun analyzeExercise(history: ExerciseHistory): ExerciseInsight? {
        if (history.sessions.none { session -> session.sets.any { it.isWorkSet } }) return null
        val points = TrendAnalyzer.points(history, config)
        val trend = TrendAnalyzer.analyze(points, config.trend)
        val deload = DeloadAdvisor.evaluate(trend, config.progression)
        val progression = ProgressionAdvisor.suggest(history, deload, config.progression)
        return ExerciseInsight(
            exerciseId = history.profile.exerciseId,
            latestE1rmKg = trend.latestE1rmKg,
            bestE1rmKg = trend.bestE1rmKg,
            trend = trend,
            deload = deload,
            progression = progression,
        )
    }

    private companion object {
        val NON_STRENGTH_CATEGORIES = setOf(ExerciseCategory.STRETCHING, ExerciseCategory.CARDIO)
    }
}
