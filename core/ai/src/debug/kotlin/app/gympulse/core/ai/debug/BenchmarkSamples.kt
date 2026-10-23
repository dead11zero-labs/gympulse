package app.gympulse.core.ai.debug

import app.gympulse.core.insights.narration.AppLanguage
import app.gympulse.core.insights.narration.InsightPrompt

/**
 * Fester, realistischer Beispiel-Prompt im Format des `InsightSummaryBuilder` (keine Nutzerdaten),
 * damit Messungen zwischen Geräten, Backends und Modelldateien vergleichbar bleiben.
 */
object BenchmarkSamples {

    fun prompt(language: AppLanguage): InsightPrompt = InsightPrompt(
        system = InsightPrompt.system(language),
        user = when (language) {
            AppLanguage.DE -> DE
            AppLanguage.EN -> EN
        },
    )

    private val DE = """
        Stand 2026-10-20, Zeitraum 6 Wochen.
        Übungen (e1RM, Trend, nächster Schritt):
        - Bankdrücken: 108,5 kg, Plateau, Gewicht halten bei 82,5 kg
        - Kniebeuge: 131 kg, steigend, Gewicht erhöhen auf 107,5 kg, 5 Wdh.
        - Kreuzheben: 162 kg, steigend, Wiederholungen steigern auf 6 bei 140 kg
        - Rudern am Kabel: 78 kg, rückläufig, Deload auf 58,5 kg
        - Schulterdrücken: 61 kg, Plateau, Gewicht halten bei 45 kg
        Wochenvolumen (harte Sätze, Richtwert, Bewertung):
        - Brust: 14 (10–20), im Ziel
        - Quadrizeps: 12 (10–20), im Ziel
        - mittlerer Rücken: 6 (10–20), zu wenig
        - Latissimus: 5 (10–20), zu wenig
        - Schultern: 21,5 (10–20), zu viel
        Befunde:
        - Deload: Rudern am Kabel auf 58,5 kg
        - Plateau: Bankdrücken
        - Ungleichgewicht: Druck zu Zug 2,1
        - Zu wenig Volumen: mittlerer Rücken 6 Sätze pro Woche
        - Gewicht erhöhen: Kniebeuge auf 107,5 kg, 5 Wdh.
    """.trimIndent()

    private val EN = """
        As of 2026-10-20, period 6 weeks.
        Exercises (e1RM, trend, next step):
        - Bench Press: 108.5 kg, plateau, hold at 82.5 kg
        - Back Squat: 131 kg, progressing, increase to 107.5 kg, 5 reps
        - Deadlift: 162 kg, progressing, increase reps to 6 at 140 kg
        - Cable Row: 78 kg, declining, deload to 58.5 kg
        - Overhead Press: 61 kg, plateau, hold at 45 kg
        Weekly volume (hard sets, target, rating):
        - chest: 14 (10–20), on target
        - quadriceps: 12 (10–20), on target
        - middle back: 6 (10–20), too low
        - lats: 5 (10–20), too low
        - shoulders: 21.5 (10–20), too high
        Findings:
        - Deload: Cable Row to 58.5 kg
        - Plateau: Bench Press
        - Imbalance: push to pull 2.1
        - Low volume: middle back 6 sets per week
        - Increase weight: Back Squat to 107.5 kg, 5 reps
    """.trimIndent()
}
