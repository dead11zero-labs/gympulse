package app.gympulse.core.insights.narration

import app.gympulse.core.insights.InsightCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Fallback ohne Modell: setzt aus den Befunden der Zusammenfassung Textbausteine zusammen, höchstens
 * [maxPoints] Stichpunkte in Prioritätsreihenfolge. Deterministisch und ohne Android-Abhängigkeit.
 *
 * @param textsFor liefert die Textbausteine zur Sprache der Zusammenfassung.
 */
class TemplateNarrator(
    private val maxPoints: Int = 5,
    private val textsFor: (AppLanguage) -> NarrationTexts = { BuiltInNarrationTexts(it) },
) : InsightNarrator {

    init {
        require(maxPoints >= 1) { "maxPoints must be >= 1" }
    }

    override fun narrate(summary: InsightSummary): Flow<String> = flow {
        compose(summary).forEachIndexed { i, line ->
            emit(if (i == 0) "• $line" else "\n• $line")
        }
    }

    /** Die einzelnen Stichpunkte (ohne Aufzählungszeichen); öffentlich für UI und Tests. */
    fun compose(summary: InsightSummary): List<String> {
        val texts = textsFor(summary.language)
        if (summary.isEmpty) return listOf(texts.text(NarrationKey.NO_DATA, emptyList()))
        val points = relevantFindings(summary.findings).take(maxPoints).map { f -> render(f, texts, summary.language) }
        return points.ifEmpty { listOf(texts.text(NarrationKey.NO_FINDINGS, emptyList())) }
    }

    /** Pro Übung nur der aussagekräftigste Befund: Vorschlag vor Trend-Hinweis, Deload vor Plateau. */
    private fun relevantFindings(findings: List<SummaryFinding>): List<SummaryFinding> {
        val progressionSubjects = findings.filter { it.code in PROGRESSION_CODES }.mapNotNull { it.subject }.toSet()
        val deloadSubjects = findings.filter { it.code == InsightCode.DELOAD }.mapNotNull { it.subject }.toSet()
        return findings.filter { f ->
            when (f.code) {
                InsightCode.PROGRESS -> f.subject !in progressionSubjects
                InsightCode.PLATEAU, InsightCode.DECLINE -> f.subject !in deloadSubjects
                else -> true
            }
        }
    }

    private fun render(f: SummaryFinding, texts: NarrationTexts, language: AppLanguage): String {
        fun n(v: Double?) = formatNumber(v ?: 0.0, language)
        val subject = f.subject.orEmpty()
        val (key, args) = when (f.code) {
            InsightCode.DELOAD -> NarrationKey.DELOAD to listOf(subject, n(f.weightKg))
            InsightCode.DECLINE -> NarrationKey.DECLINE to listOf(subject)
            InsightCode.PLATEAU -> NarrationKey.PLATEAU to listOf(subject)
            InsightCode.IMBALANCE -> NarrationKey.IMBALANCE to listOf(subject, f.other.orEmpty(), n(f.ratio))
            InsightCode.LOW_VOLUME -> NarrationKey.LOW_VOLUME to listOf(subject, n(f.setsPerWeek))
            InsightCode.HIGH_VOLUME -> NarrationKey.HIGH_VOLUME to listOf(subject, n(f.setsPerWeek))
            InsightCode.INCREASE_WEIGHT -> NarrationKey.INCREASE_WEIGHT to listOf(subject, n(f.weightKg), (f.reps ?: 0).toString())
            InsightCode.INCREASE_REPS -> NarrationKey.INCREASE_REPS to listOf(subject, (f.reps ?: 0).toString(), n(f.weightKg))
            InsightCode.HOLD -> NarrationKey.HOLD to listOf(subject, n(f.weightKg))
            InsightCode.PROGRESS -> NarrationKey.PROGRESS to listOf(subject)
        }
        return texts.text(key, args)
    }

    private companion object {
        val PROGRESSION_CODES = setOf(InsightCode.INCREASE_WEIGHT, InsightCode.INCREASE_REPS, InsightCode.HOLD)
    }
}
