package app.gympulse.core.insights.narration

import app.gympulse.core.insights.InsightCode
import java.time.LocalDate

/** Eine Übung mit Schlussfolgerung (kein einzelner Satz, keine Session). */
data class SummaryExercise(
    val name: String,
    val e1rmKg: Double?,
    val trend: String,
    val nextStep: String?,
)

/** Wochenvolumen einer Muskelgruppe mit Bewertung. */
data class SummaryMuscle(
    val name: String,
    val setsPerWeek: Double,
    val targetMin: Double,
    val targetMax: Double,
    val status: String,
)

/**
 * Ein Befund aus Schicht 1. [subject] und [other] sind bereits lokalisierte Anzeigenamen
 * (Übung, Muskelgruppe oder Seite eines Ungleichgewichts), die Zahlen stammen unverändert aus
 * `TrainingInsights`.
 */
data class SummaryFinding(
    val code: InsightCode,
    val subject: String?,
    val other: String? = null,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val setsPerWeek: Double? = null,
    val ratio: Double? = null,
)

/**
 * Kompakte Zusammenfassung für den Coach: nur Schlussfolgerungen, keine Rohdaten. [text] ist die
 * Fassung für das Sprachmodell, die strukturierten Listen nutzt der `TemplateNarrator`.
 */
data class InsightSummary(
    val language: AppLanguage,
    val asOf: LocalDate,
    val weeks: Int,
    val exercises: List<SummaryExercise>,
    val muscles: List<SummaryMuscle>,
    val findings: List<SummaryFinding>,
    val text: String,
) {
    val estimatedTokens: Int get() = TokenEstimator.estimate(text)

    /** Ohne Befunde und ohne Übungen gibt es nichts zu erzählen. */
    val isEmpty: Boolean get() = exercises.isEmpty() && findings.isEmpty()
}
