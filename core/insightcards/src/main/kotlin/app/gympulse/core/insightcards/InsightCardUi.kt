package app.gympulse.core.insightcards

import androidx.annotation.StringRes
import app.gympulse.core.data.ui.labelRes
import app.gympulse.core.insights.BalancePair
import app.gympulse.core.insights.BalanceSide
import app.gympulse.core.insights.Insight
import app.gympulse.core.insights.InsightCode
import app.gympulse.core.insights.ProgressionAction
import app.gympulse.core.insights.ProgressionReason
import app.gympulse.core.insights.TrainingInsights
import app.gympulse.core.insights.TrendStatus
import java.text.NumberFormat
import java.util.Locale

/** Anzeigetext einer Karte; die Compose-Schicht löst ihn über `stringResource` auf. */
sealed interface InsightText {
    /** Text aus strings.xml. [args] sind Strings, Zahlen oder verschachtelte [InsightText]. */
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : InsightText

    /** Bereits fertiger Text, z. B. ein Übungsname. */
    data class Plain(val value: String) : InsightText
}

/** Tonalität einer Karte; bestimmt Farbpaar und Icon (nie allein die Farbe, der Titel trägt die Aussage). */
enum class InsightTone {
    /** Fortschritt oder Steigerungsvorschlag. */
    POSITIVE,

    /** Hinweis ohne Wertung, z. B. Gewicht halten. */
    NEUTRAL,

    /** Plateau, Rückgang, Deload, Volumen oder Ungleichgewicht: etwas verdient Aufmerksamkeit. */
    ATTENTION,
}

/**
 * Eine Insight-Karte, fertig für die Anzeige.
 *
 * @property id stabiler Schlüssel (für Listen), eindeutig je Befund.
 * @property subject Übung, Muskelgruppe oder Gegenspielerpaar, auf das sich der Befund bezieht.
 */
data class InsightCardUi(
    val id: String,
    val code: InsightCode,
    val tone: InsightTone,
    val title: InsightText.Res,
    val subject: InsightText?,
    val message: InsightText.Res,
)

enum class InsightsEmptyReason {
    /** Noch zu wenige Sessions für Trends und Vorschläge. */
    NOT_ENOUGH_DATA,

    /** Es gibt Daten, aber nichts Auffälliges. */
    NOTHING_NOTABLE,
}

/** Zustand eines Insight-Bereichs: entweder Karten oder ein Grund für den Leerzustand. */
data class InsightsUi(
    val cards: List<InsightCardUi> = emptyList(),
    val emptyReason: InsightsEmptyReason? = null,
) {
    val isEmpty: Boolean get() = cards.isEmpty()

    companion object {
        /** Zustand vor dem ersten Ergebnis: nichts anzeigen. */
        val Loading = InsightsUi()
    }
}

/** Befunde, die auf dem Dashboard keinen Platz bekommen: sie sind keine Handlungsempfehlung. */
private val DASHBOARD_HIDDEN_CODES = setOf(InsightCode.HOLD, InsightCode.PROGRESS)

const val DASHBOARD_MAX_CARDS = 3

/**
 * Wichtigste Befunde fürs Dashboard: nach Priorität sortiert, höchstens ein Befund je Übung,
 * Muskelgruppe oder Gegenspielerpaar, begrenzt auf [maxCards]. Ohne ausreichende Daten (keine Übung
 * mit auswertbarem Trend) gibt es nur den Leerzustand, damit neue Nutzer keine Volumen-Warnungen
 * nach einer einzigen Session sehen.
 *
 * @param exerciseNames Übungsname je ID; unbekannte IDs erscheinen als "Übung".
 */
fun TrainingInsights.toDashboardInsights(
    exerciseNames: Map<String, String>,
    locale: Locale = Locale.getDefault(),
    maxCards: Int = DASHBOARD_MAX_CARDS,
): InsightsUi {
    val hasEnoughData = exercises.any { it.trend.status != TrendStatus.INSUFFICIENT_DATA }
    if (!hasEnoughData) return InsightsUi(emptyReason = InsightsEmptyReason.NOT_ENOUGH_DATA)
    val format = KgFormat(locale)
    val cards = findings
        .filter { it.code !in DASHBOARD_HIDDEN_CODES }
        .distinctBy { it.subjectKey() }
        .take(maxCards)
        .map { it.toCard(exerciseNames, format) }
    return if (cards.isEmpty()) InsightsUi(emptyReason = InsightsEmptyReason.NOTHING_NOTABLE) else InsightsUi(cards)
}

/**
 * Befunde einer Übung fürs Übungsdetail: Trend und Vorschlag, nach Priorität. Ohne Befund gibt es den
 * Leerzustand [InsightsEmptyReason.NOT_ENOUGH_DATA].
 */
fun TrainingInsights.toExerciseInsights(
    exerciseId: String,
    locale: Locale = Locale.getDefault(),
): InsightsUi {
    val format = KgFormat(locale)
    val cards = findingsFor(exerciseId).map { it.toCard(emptyMap(), format, includeSubject = false) }
    return if (cards.isEmpty()) InsightsUi(emptyReason = InsightsEmptyReason.NOT_ENOUGH_DATA) else InsightsUi(cards)
}

private fun Insight.subjectKey(): String = when (this) {
    is Insight.Trend -> "exercise:$exerciseId"
    is Insight.Progression -> "exercise:$exerciseId"
    is Insight.Volume -> "muscle:${volume.muscle.key}"
    is Insight.Imbalance -> "pair:${imbalance.pair.name}"
}

/** Kilogramm- und Zahlenformat in der Sprache der App: höchstens eine Nachkommastelle (62,5). */
internal class KgFormat(locale: Locale) {
    private val format = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }

    fun number(value: Double?): String = value?.let(format::format) ?: DASH

    private companion object {
        const val DASH = "–"
    }
}

internal fun Insight.toCard(
    exerciseNames: Map<String, String>,
    format: KgFormat,
    includeSubject: Boolean = true,
): InsightCardUi {
    fun exerciseSubject(id: String): InsightText? = when {
        !includeSubject -> null
        else -> exerciseNames[id]?.takeIf { it.isNotBlank() }?.let { InsightText.Plain(it) }
            ?: InsightText.Res(R.string.insights_unknown_exercise)
    }

    return when (this) {
        is Insight.Trend -> {
            val t = trend
            when (code) {
                InsightCode.PLATEAU -> card(
                    key = "trend:$exerciseId", tone = InsightTone.ATTENTION,
                    title = R.string.insights_title_plateau, subject = exerciseSubject(exerciseId),
                    message = InsightText.Res(
                        R.string.insights_message_plateau,
                        listOf(t.daysSinceProgress.toInt(), t.sessionsSinceProgress, format.number(t.latestE1rmKg)),
                    ),
                )
                InsightCode.DECLINE -> card(
                    key = "trend:$exerciseId", tone = InsightTone.ATTENTION,
                    title = R.string.insights_title_decline, subject = exerciseSubject(exerciseId),
                    message = InsightText.Res(
                        R.string.insights_message_decline,
                        listOf(format.number(t.latestE1rmKg), format.number(t.bestE1rmKg)),
                    ),
                )
                else -> card(
                    key = "trend:$exerciseId", tone = InsightTone.POSITIVE,
                    title = R.string.insights_title_progress, subject = exerciseSubject(exerciseId),
                    message = InsightText.Res(
                        R.string.insights_message_progress,
                        listOf(format.number(t.latestE1rmKg), format.number(t.slopeKgPerWeek)),
                    ),
                )
            }
        }
        is Insight.Progression -> {
            val s = suggestion
            val current = format.number(s.currentWeightKg)
            val next = format.number(s.suggestedWeightKg)
            val subject = exerciseSubject(s.exerciseId)
            when (s.action) {
                ProgressionAction.DELOAD -> card(
                    key = "progression:${s.exerciseId}", tone = InsightTone.ATTENTION,
                    title = R.string.insights_title_deload, subject = subject,
                    message = InsightText.Res(
                        if (s.reason == ProgressionReason.SUSTAINED_DECLINE) {
                            R.string.insights_message_deload_decline
                        } else {
                            R.string.insights_message_deload_plateau
                        },
                        listOf(next, current, s.targetReps),
                    ),
                )
                ProgressionAction.INCREASE_WEIGHT -> card(
                    key = "progression:${s.exerciseId}", tone = InsightTone.POSITIVE,
                    title = R.string.insights_title_increase_weight, subject = subject,
                    message = InsightText.Res(R.string.insights_message_increase_weight, listOf(next, current, s.targetReps)),
                )
                ProgressionAction.INCREASE_REPS -> card(
                    key = "progression:${s.exerciseId}", tone = InsightTone.POSITIVE,
                    title = R.string.insights_title_increase_reps, subject = subject,
                    message = InsightText.Res(R.string.insights_message_increase_reps, listOf(current, s.targetReps)),
                )
                ProgressionAction.HOLD -> card(
                    key = "progression:${s.exerciseId}", tone = InsightTone.NEUTRAL,
                    title = R.string.insights_title_hold, subject = subject,
                    message = InsightText.Res(
                        when (s.reason) {
                            ProgressionReason.SETS_MISSING -> R.string.insights_message_hold_sets_missing
                            ProgressionReason.RPE_TOO_HIGH -> R.string.insights_message_hold_rpe
                            else -> R.string.insights_message_hold_reps
                        },
                        listOf(current, s.targetReps),
                    ),
                )
            }
        }
        is Insight.Volume -> {
            val v = volume
            val high = code == InsightCode.HIGH_VOLUME
            card(
                key = "volume:${v.muscle.key}", tone = InsightTone.ATTENTION,
                title = if (high) R.string.insights_title_high_volume else R.string.insights_title_low_volume,
                subject = InsightText.Res(v.muscle.labelRes()),
                message = InsightText.Res(
                    if (high) R.string.insights_message_high_volume else R.string.insights_message_low_volume,
                    listOf(
                        format.number(v.setsPerWeek),
                        format.number(v.target.minSetsPerWeek),
                        format.number(v.target.maxSetsPerWeek),
                    ),
                ),
            )
        }
        is Insight.Imbalance -> {
            val i = imbalance
            val aDominant = i.dominantSide == BalanceSide.A
            val dominant = format.number(if (aDominant) i.sideASetsPerWeek else i.sideBSetsPerWeek)
            val weaker = format.number(if (aDominant) i.sideBSetsPerWeek else i.sideASetsPerWeek)
            val (subjectRes, messageRes) = when (i.pair) {
                BalancePair.PUSH_PULL ->
                    R.string.insights_subject_push_pull to
                        if (aDominant) R.string.insights_message_push_dominant else R.string.insights_message_pull_dominant
                BalancePair.QUADS_HAMSTRINGS ->
                    R.string.insights_subject_quads_hamstrings to
                        if (aDominant) R.string.insights_message_quads_dominant else R.string.insights_message_hamstrings_dominant
            }
            card(
                key = "imbalance:${i.pair.name}", tone = InsightTone.ATTENTION,
                title = R.string.insights_title_imbalance, subject = InsightText.Res(subjectRes),
                message = InsightText.Res(messageRes, listOf(dominant, weaker)),
            )
        }
    }
}

private fun Insight.card(
    key: String,
    tone: InsightTone,
    @StringRes title: Int,
    subject: InsightText?,
    message: InsightText.Res,
) = InsightCardUi(
    id = "$key:${code.name}",
    code = code,
    tone = tone,
    title = InsightText.Res(title),
    subject = subject,
    message = message,
)
