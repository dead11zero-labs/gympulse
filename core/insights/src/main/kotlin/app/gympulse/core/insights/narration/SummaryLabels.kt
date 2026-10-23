package app.gympulse.core.insights.narration

import app.gympulse.core.insights.BalancePair
import app.gympulse.core.insights.BalanceSide
import app.gympulse.core.insights.ProgressionAction
import app.gympulse.core.insights.TrendStatus
import app.gympulse.core.insights.VolumeStatus
import app.gympulse.core.model.MuscleGroup

/** Sprachabhängige Bezeichnungen für Zusammenfassung und Textbausteine (rein JVM, ohne Android). */
internal object SummaryLabels {

    fun muscle(muscle: MuscleGroup, language: AppLanguage): String = when (language) {
        AppLanguage.EN -> when (muscle) {
            MuscleGroup.ABDOMINALS -> "abs"
            MuscleGroup.ABDUCTORS -> "abductors"
            MuscleGroup.ADDUCTORS -> "adductors"
            MuscleGroup.BICEPS -> "biceps"
            MuscleGroup.CALVES -> "calves"
            MuscleGroup.CHEST -> "chest"
            MuscleGroup.FOREARMS -> "forearms"
            MuscleGroup.GLUTES -> "glutes"
            MuscleGroup.HAMSTRINGS -> "hamstrings"
            MuscleGroup.LATS -> "lats"
            MuscleGroup.LOWER_BACK -> "lower back"
            MuscleGroup.MIDDLE_BACK -> "middle back"
            MuscleGroup.NECK -> "neck"
            MuscleGroup.QUADRICEPS -> "quadriceps"
            MuscleGroup.SHOULDERS -> "shoulders"
            MuscleGroup.TRAPS -> "traps"
            MuscleGroup.TRICEPS -> "triceps"
        }
        AppLanguage.DE -> when (muscle) {
            MuscleGroup.ABDOMINALS -> "Bauch"
            MuscleGroup.ABDUCTORS -> "Abduktoren"
            MuscleGroup.ADDUCTORS -> "Adduktoren"
            MuscleGroup.BICEPS -> "Bizeps"
            MuscleGroup.CALVES -> "Waden"
            MuscleGroup.CHEST -> "Brust"
            MuscleGroup.FOREARMS -> "Unterarme"
            MuscleGroup.GLUTES -> "Gesäß"
            MuscleGroup.HAMSTRINGS -> "hintere Oberschenkel"
            MuscleGroup.LATS -> "Latissimus"
            MuscleGroup.LOWER_BACK -> "unterer Rücken"
            MuscleGroup.MIDDLE_BACK -> "mittlerer Rücken"
            MuscleGroup.NECK -> "Nacken"
            MuscleGroup.QUADRICEPS -> "Quadrizeps"
            MuscleGroup.SHOULDERS -> "Schultern"
            MuscleGroup.TRAPS -> "Trapez"
            MuscleGroup.TRICEPS -> "Trizeps"
        }
    }

    fun trend(status: TrendStatus, language: AppLanguage): String = when (language) {
        AppLanguage.EN -> when (status) {
            TrendStatus.PROGRESSING -> "progressing"
            TrendStatus.PLATEAU -> "plateau"
            TrendStatus.DECLINING -> "declining"
            TrendStatus.INSUFFICIENT_DATA -> "not enough data"
        }
        AppLanguage.DE -> when (status) {
            TrendStatus.PROGRESSING -> "Fortschritt"
            TrendStatus.PLATEAU -> "Plateau"
            TrendStatus.DECLINING -> "Rückgang"
            TrendStatus.INSUFFICIENT_DATA -> "zu wenig Daten"
        }
    }

    fun volumeStatus(status: VolumeStatus, language: AppLanguage): String = when (language) {
        AppLanguage.EN -> when (status) {
            VolumeStatus.LOW -> "too low"
            VolumeStatus.OPTIMAL -> "ok"
            VolumeStatus.HIGH -> "too high"
        }
        AppLanguage.DE -> when (status) {
            VolumeStatus.LOW -> "zu wenig"
            VolumeStatus.OPTIMAL -> "ok"
            VolumeStatus.HIGH -> "zu viel"
        }
    }

    fun action(action: ProgressionAction, language: AppLanguage): String = when (language) {
        AppLanguage.EN -> when (action) {
            ProgressionAction.INCREASE_WEIGHT -> "increase weight"
            ProgressionAction.INCREASE_REPS -> "increase reps"
            ProgressionAction.HOLD -> "keep weight"
            ProgressionAction.DELOAD -> "deload"
        }
        AppLanguage.DE -> when (action) {
            ProgressionAction.INCREASE_WEIGHT -> "Gewicht steigern"
            ProgressionAction.INCREASE_REPS -> "Wiederholungen steigern"
            ProgressionAction.HOLD -> "Gewicht halten"
            ProgressionAction.DELOAD -> "Deload"
        }
    }

    fun side(pair: BalancePair, side: BalanceSide, language: AppLanguage): String = when (pair) {
        BalancePair.PUSH_PULL -> when (language) {
            AppLanguage.EN -> if (side == BalanceSide.A) "pushing" else "pulling"
            AppLanguage.DE -> if (side == BalanceSide.A) "Drücken" else "Ziehen"
        }
        BalancePair.QUADS_HAMSTRINGS -> when (language) {
            AppLanguage.EN -> if (side == BalanceSide.A) "quadriceps" else "hamstrings"
            AppLanguage.DE -> if (side == BalanceSide.A) "Quadrizeps" else "hintere Oberschenkel"
        }
    }

    fun other(side: BalanceSide): BalanceSide = if (side == BalanceSide.A) BalanceSide.B else BalanceSide.A
}
