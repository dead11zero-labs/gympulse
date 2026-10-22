package app.gympulse.core.insights.narration

/** Stabile Schlüssel der Textbausteine; Platzhalter sind `{0}`, `{1}` … in Argument-Reihenfolge. */
enum class NarrationKey {
    /** {0} Übung, {1} Gewicht in kg */
    DELOAD,

    /** {0} Übung */
    DECLINE,

    /** {0} Übung */
    PLATEAU,

    /** {0} stärkere Seite, {1} schwächere Seite, {2} Verhältnis */
    IMBALANCE,

    /** {0} Muskelgruppe, {1} Sätze pro Woche */
    LOW_VOLUME,

    /** {0} Muskelgruppe, {1} Sätze pro Woche */
    HIGH_VOLUME,

    /** {0} Übung, {1} Gewicht in kg, {2} Wiederholungen */
    INCREASE_WEIGHT,

    /** {0} Übung, {1} Wiederholungen, {2} Gewicht in kg */
    INCREASE_REPS,

    /** {0} Übung, {1} Gewicht in kg */
    HOLD,

    /** {0} Übung */
    PROGRESS,

    /** Keine Trainingsdaten. */
    NO_DATA,

    /** Daten vorhanden, aber nichts Auffälliges. */
    NO_FINDINGS,
}

/**
 * Quelle der Textbausteine. Die JVM-Fassung [BuiltInNarrationTexts] hält Tests ohne Android lauffähig;
 * eine Android-Implementierung kann dieselben Schlüssel auf `strings.xml`-Ressourcen abbilden.
 */
fun interface NarrationTexts {
    fun text(key: NarrationKey, args: List<String>): String
}

/** Eingebaute Textbausteine in Deutsch und Englisch. */
class BuiltInNarrationTexts(private val language: AppLanguage) : NarrationTexts {

    override fun text(key: NarrationKey, args: List<String>): String {
        var result = pattern(key)
        args.forEachIndexed { i, arg -> result = result.replace("{$i}", arg) }
        return result
    }

    private fun pattern(key: NarrationKey): String = when (language) {
        AppLanguage.EN -> when (key) {
            NarrationKey.DELOAD -> "{0}: take a deload week and drop to {1} kg."
            NarrationKey.DECLINE -> "{0}: your strength estimate is falling. Check recovery, sleep and nutrition."
            NarrationKey.PLATEAU -> "{0}: no new best for a while. Try a different rep range or more recovery."
            NarrationKey.IMBALANCE -> "Balance: {0} gets {2} times the volume of {1}. Add sets for {1}."
            NarrationKey.LOW_VOLUME -> "{0}: only {1} hard sets per week. Add a few sets."
            NarrationKey.HIGH_VOLUME -> "{0}: {1} hard sets per week is a lot. Consider trimming."
            NarrationKey.INCREASE_WEIGHT -> "{0}: increase to {1} kg and start at {2} reps."
            NarrationKey.INCREASE_REPS -> "{0}: aim for {1} reps at {2} kg."
            NarrationKey.HOLD -> "{0}: stay at {1} kg and keep the form clean."
            NarrationKey.PROGRESS -> "{0}: steady progress. Keep going."
            NarrationKey.NO_DATA -> "Not enough training data yet. Log a few workouts to get insights."
            NarrationKey.NO_FINDINGS -> "Nothing unusual right now. Keep training consistently."
        }
        AppLanguage.DE -> when (key) {
            NarrationKey.DELOAD -> "{0}: plane eine Deload-Woche und gehe auf {1} kg."
            NarrationKey.DECLINE -> "{0}: deine Kraftschätzung sinkt. Prüfe Erholung, Schlaf und Ernährung."
            NarrationKey.PLATEAU -> "{0}: seit einiger Zeit keine neue Bestleistung. Probiere einen anderen Wiederholungsbereich oder mehr Erholung."
            NarrationKey.IMBALANCE -> "Balance: {0} bekommt das {2}-Fache an Volumen von {1}. Ergänze Sätze für {1}."
            NarrationKey.LOW_VOLUME -> "{0}: nur {1} harte Sätze pro Woche. Ergänze ein paar Sätze."
            NarrationKey.HIGH_VOLUME -> "{0}: {1} harte Sätze pro Woche sind viel. Erwäge, etwas zu kürzen."
            NarrationKey.INCREASE_WEIGHT -> "{0}: steigere auf {1} kg und starte bei {2} Wiederholungen."
            NarrationKey.INCREASE_REPS -> "{0}: peile {1} Wiederholungen bei {2} kg an."
            NarrationKey.HOLD -> "{0}: bleibe bei {1} kg und halte die Technik sauber."
            NarrationKey.PROGRESS -> "{0}: stetiger Fortschritt. Weiter so."
            NarrationKey.NO_DATA -> "Noch zu wenig Trainingsdaten. Protokolliere ein paar Workouts, um Auswertungen zu erhalten."
            NarrationKey.NO_FINDINGS -> "Aktuell nichts Auffälliges. Trainiere weiter regelmäßig."
        }
    }
}
