package app.gympulse.core.insights.narration

import app.gympulse.core.insights.ExerciseInsight
import app.gympulse.core.insights.Insight
import app.gympulse.core.insights.InsightCode
import app.gympulse.core.insights.ProgressionAction
import app.gympulse.core.insights.MuscleVolume
import app.gympulse.core.insights.ProgressionSuggestion
import app.gympulse.core.insights.TrainingInsights
import app.gympulse.core.insights.VolumeStatus

/**
 * Grenzen der Zusammenfassung.
 *
 * @property maxTokens Obergrenze (geschätzt, siehe [TokenEstimator]) für [InsightSummary.text]. Der Plan
 *   verlangt unter 1.000 Token für den gesamten Prompt; die Prompt-Regeln brauchen rund 100–150 davon.
 * @property maxExercises Anzahl der Top-Übungen.
 * @property maxMuscles Anzahl der Muskelgruppen in der Volumen-Tabelle (auffällige zuerst).
 * @property maxFindings Anzahl der Befunde.
 * @property maxVolumeFindings davon höchstens so viele Volumen-Warnungen, damit sie Übungs-Empfehlungen nicht verdrängen.
 */
data class SummaryConfig(
    val maxTokens: Int = 800,
    val maxExercises: Int = 5,
    val maxMuscles: Int = 8,
    val maxFindings: Int = 10,
    val maxVolumeFindings: Int = 3,
) {
    init {
        require(maxTokens > 0 && maxExercises >= 0 && maxMuscles >= 0 && maxFindings >= 0) { "invalid summary config" }
    }
}

/**
 * Baut aus [TrainingInsights] eine kompakte Zusammenfassung für den Coach. Es kommen ausschließlich
 * Schlussfolgerungen aus Schicht 1 hinein (e1RM, Trend, nächster Schritt, Wochenvolumen, Befunde);
 * einzelne Sätze, Notizen, Fotos, Health-Daten und IDs gelangen nie in den Text. Die Ausgabe ist
 * deterministisch: gleiche Eingabe ergibt denselben Text.
 */
class InsightSummaryBuilder(private val config: SummaryConfig = SummaryConfig()) {

    /**
     * @param exerciseNames Anzeigenamen je Übungs-ID; Übungen ohne Namen werden ausgelassen.
     */
    fun build(
        insights: TrainingInsights,
        exerciseNames: Map<String, String>,
        language: AppLanguage,
    ): InsightSummary {
        val names = exerciseNames.mapValues { sanitizeName(it.value) }.filterValues { it.isNotEmpty() }

        val exercises = selectExercises(insights, names, language)
        val muscles = selectMuscles(insights, language)
        var volumeFindings = 0
        val findings = insights.findings
            .filter { it !is Insight.Volume || ++volumeFindings <= config.maxVolumeFindings }
            .mapNotNull { toFinding(it, names, language) }
            .take(config.maxFindings)

        var ex = exercises
        var mu = muscles
        var fi = findings
        fun render() = renderText(language, insights, ex.map { it.second }, mu, fi)
        var text = render()
        // Über dem Budget: erst Volumen, dann Übungen, dann Befunde von hinten kürzen (Priorität bleibt vorn).
        while (TokenEstimator.estimate(text) > config.maxTokens && (mu.isNotEmpty() || ex.isNotEmpty() || fi.isNotEmpty())) {
            when {
                mu.isNotEmpty() -> mu = mu.dropLast(1)
                ex.isNotEmpty() -> ex = ex.dropLast(1)
                else -> fi = fi.dropLast(1)
            }
            text = render()
        }

        return InsightSummary(
            language = language,
            asOf = insights.asOf,
            weeks = insights.volume.weeks,
            exercises = ex.map { it.second },
            muscles = mu,
            findings = fi,
            text = text,
        )
    }

    private fun selectExercises(
        insights: TrainingInsights,
        names: Map<String, String>,
        language: AppLanguage,
    ): List<Pair<String, SummaryExercise>> {
        val priority = insights.findings
            .flatMap { f ->
                when (f) {
                    is Insight.Trend -> listOf(f.exerciseId to f.code.ordinal)
                    is Insight.Progression -> listOf(f.exerciseId to f.code.ordinal)
                    else -> emptyList()
                }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.min() }

        return insights.exercises
            .filter { it.latestE1rmKg != null && names.containsKey(it.exerciseId) }
            .sortedWith(
                compareBy<ExerciseInsight> { priority[it.exerciseId] ?: Int.MAX_VALUE }
                    .thenByDescending { it.latestE1rmKg }
                    .thenBy { names.getValue(it.exerciseId) }
                    .thenBy { it.exerciseId },
            )
            .take(config.maxExercises)
            .map { e ->
                e.exerciseId to SummaryExercise(
                    name = names.getValue(e.exerciseId),
                    e1rmKg = e.latestE1rmKg,
                    trend = SummaryLabels.trend(e.trend.status, language),
                    nextStep = e.progression?.let { nextStep(it, language) },
                )
            }
    }

    private fun selectMuscles(insights: TrainingInsights, language: AppLanguage): List<SummaryMuscle> =
        insights.volume.muscles
            .sortedWith(
                compareBy<MuscleVolume>({ it.status == VolumeStatus.OPTIMAL }, { -it.setsPerWeek }, { it.muscle.ordinal }),
            )
            .take(config.maxMuscles)
            .map {
                SummaryMuscle(
                    name = SummaryLabels.muscle(it.muscle, language),
                    setsPerWeek = it.setsPerWeek,
                    targetMin = it.target.minSetsPerWeek,
                    targetMax = it.target.maxSetsPerWeek,
                    status = SummaryLabels.volumeStatus(it.status, language),
                )
            }

    private fun toFinding(insight: Insight, names: Map<String, String>, language: AppLanguage): SummaryFinding? =
        when (insight) {
            is Insight.Trend -> names[insight.exerciseId]?.let { SummaryFinding(insight.code, subject = it) }
            is Insight.Progression -> names[insight.exerciseId]?.let {
                SummaryFinding(
                    code = insight.code,
                    subject = it,
                    weightKg = insight.suggestion.suggestedWeightKg,
                    reps = insight.suggestion.targetReps,
                )
            }
            is Insight.Volume -> SummaryFinding(
                code = insight.code,
                subject = SummaryLabels.muscle(insight.volume.muscle, language),
                setsPerWeek = insight.volume.setsPerWeek,
            )
            is Insight.Imbalance -> {
                val pair = insight.imbalance.pair
                val strong = insight.imbalance.dominantSide
                SummaryFinding(
                    code = InsightCode.IMBALANCE,
                    subject = SummaryLabels.side(pair, strong, language),
                    other = SummaryLabels.side(pair, SummaryLabels.other(strong), language),
                    ratio = insight.imbalance.ratio,
                )
            }
        }

    private fun nextStep(s: ProgressionSuggestion, language: AppLanguage): String {
        val kg = formatNumber(s.suggestedWeightKg, language)
        val reps = s.targetReps.toString()
        val label = SummaryLabels.action(s.action, language)
        return when (language) {
            AppLanguage.EN -> when (s.action) {
                ProgressionAction.INCREASE_REPS -> "$label to $reps at $kg kg"
                ProgressionAction.HOLD -> "$label at $kg kg"
                else -> "$label to $kg kg, $reps reps"
            }
            AppLanguage.DE -> when (s.action) {
                ProgressionAction.INCREASE_REPS -> "$label auf $reps bei $kg kg"
                ProgressionAction.HOLD -> "$label bei $kg kg"
                else -> "$label auf $kg kg, $reps Wdh."
            }
        }
    }

    private fun renderText(
        language: AppLanguage,
        insights: TrainingInsights,
        exercises: List<SummaryExercise>,
        muscles: List<SummaryMuscle>,
        findings: List<SummaryFinding>,
    ): String {
        val de = language == AppLanguage.DE
        fun n(v: Double) = formatNumber(v, language)
        val lines = mutableListOf<String>()
        lines += if (de) {
            "Stand ${insights.asOf}, Zeitraum ${insights.volume.weeks} Wochen."
        } else {
            "As of ${insights.asOf}, period ${insights.volume.weeks} weeks."
        }
        if (exercises.isNotEmpty()) {
            lines += if (de) "Übungen (e1RM, Trend, nächster Schritt):" else "Exercises (e1RM, trend, next step):"
            exercises.forEach { e ->
                val next = e.nextStep?.let { "; $it" }.orEmpty()
                lines += "- ${e.name}: ${n(e.e1rmKg ?: 0.0)} kg; ${e.trend}$next"
            }
        }
        if (muscles.isNotEmpty()) {
            lines += if (de) "Volumen (harte Sätze pro Woche, Richtwert):" else "Volume (hard sets per week, target):"
            muscles.forEach { m ->
                lines += "- ${m.name}: ${n(m.setsPerWeek)} (${n(m.targetMin)}-${n(m.targetMax)}), ${m.status}"
            }
        }
        if (findings.isNotEmpty()) {
            lines += if (de) "Befunde:" else "Findings:"
            findings.forEach { lines += "- ${renderFinding(it, language)}" }
        }
        return lines.joinToString("\n")
    }

    private fun renderFinding(f: SummaryFinding, language: AppLanguage): String {
        val parts = mutableListOf<String>()
        f.subject?.let { parts += it }
        f.other?.let { parts += (if (language == AppLanguage.DE) "gegen $it" else "vs $it") }
        f.weightKg?.let { parts += "${formatNumber(it, language)} kg" }
        f.reps?.let { parts += (if (language == AppLanguage.DE) "$it Wdh." else "$it reps") }
        f.setsPerWeek?.let { parts += (if (language == AppLanguage.DE) "${formatNumber(it, language)} Sätze/Woche" else "${formatNumber(it, language)} sets/week") }
        f.ratio?.let { parts += (if (language == AppLanguage.DE) "Verhältnis ${formatNumber(it, language)}" else "ratio ${formatNumber(it, language)}") }
        return "${f.code.name}: ${parts.joinToString(", ")}"
    }
}
