package app.gympulse.core.insights

import java.time.LocalDate

/**
 * Stabile Codes der Befunde. Die UI übersetzt sie über strings.xml; die Namen dürfen sich daher
 * nicht ändern. Die Reihenfolge ist zugleich die Priorität (wichtigster Befund zuerst).
 */
enum class InsightCode {
    DELOAD,
    DECLINE,
    PLATEAU,
    IMBALANCE,
    LOW_VOLUME,
    HIGH_VOLUME,
    INCREASE_WEIGHT,
    INCREASE_REPS,
    HOLD,
    PROGRESS,
}

/** Ein einzelner Befund mit den Zahlen, die die UI (oder später das LLM) dazu anzeigen darf. */
sealed interface Insight {
    val code: InsightCode

    /** Trend einer Übung: [InsightCode.PROGRESS], [InsightCode.PLATEAU] oder [InsightCode.DECLINE]. */
    data class Trend(val exerciseId: String, val trend: TrendResult) : Insight {
        override val code: InsightCode = when (trend.status) {
            TrendStatus.PLATEAU -> InsightCode.PLATEAU
            TrendStatus.DECLINING -> InsightCode.DECLINE
            else -> InsightCode.PROGRESS
        }
    }

    /** Vorschlag für die nächste Session einer Übung. */
    data class Progression(val suggestion: ProgressionSuggestion) : Insight {
        override val code: InsightCode = when (suggestion.action) {
            ProgressionAction.INCREASE_WEIGHT -> InsightCode.INCREASE_WEIGHT
            ProgressionAction.INCREASE_REPS -> InsightCode.INCREASE_REPS
            ProgressionAction.HOLD -> InsightCode.HOLD
            ProgressionAction.DELOAD -> InsightCode.DELOAD
        }
        val exerciseId: String get() = suggestion.exerciseId
    }

    /** Zu wenig oder zu viel Volumen für eine Muskelgruppe. */
    data class Volume(val volume: MuscleVolume) : Insight {
        override val code: InsightCode = when (volume.status) {
            VolumeStatus.HIGH -> InsightCode.HIGH_VOLUME
            else -> InsightCode.LOW_VOLUME
        }
    }

    /** Starkes Ungleichgewicht zwischen Gegenspielern. */
    data class Imbalance(val imbalance: MuscleImbalance) : Insight {
        override val code: InsightCode = InsightCode.IMBALANCE
    }
}

/** Auswertung einer Übung. */
data class ExerciseInsight(
    val exerciseId: String,
    val latestE1rmKg: Double?,
    val bestE1rmKg: Double?,
    val trend: TrendResult,
    val deload: DeloadReason?,
    val progression: ProgressionSuggestion?,
)

/** Gesamtergebnis der klassischen Auswertung, Eingabe für Insight-Karten und später den KI-Coach. */
data class TrainingInsights(
    val asOf: LocalDate,
    val exercises: List<ExerciseInsight>,
    val volume: VolumeReport,
    /** Alle Befunde, nach Priorität ([InsightCode]-Reihenfolge) sortiert. */
    val findings: List<Insight>,
) {
    fun exercise(exerciseId: String): ExerciseInsight? = exercises.firstOrNull { it.exerciseId == exerciseId }

    fun findingsFor(exerciseId: String): List<Insight> = findings.filter {
        when (it) {
            is Insight.Trend -> it.exerciseId == exerciseId
            is Insight.Progression -> it.exerciseId == exerciseId
            else -> false
        }
    }
}
