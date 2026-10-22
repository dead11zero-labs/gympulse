package app.gympulse.core.insights

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class TrendStatus { PROGRESSING, PLATEAU, DECLINING, INSUFFICIENT_DATA }

/**
 * e1RM einer Session.
 *
 * @property topWeightKg höchstes eingetragenes Gewicht der Arbeitssätze (bei Körpergewichtsübungen
 *   das Zusatzgewicht, sonst 0); optional, ermöglicht Last- und Wiederholungsrekorde.
 * @property repsAtTopWeight meiste Wiederholungen mit [topWeightKg].
 */
data class E1rmPoint(
    val date: LocalDate,
    val e1rmKg: Double,
    val topWeightKg: Double? = null,
    val repsAtTopWeight: Int? = null,
)

/**
 * Ergebnis der Trend- und Plateau-Erkennung für eine Übung.
 *
 * Als Fortschritt zählt ein neuer e1RM-Bestwert, ein neues Höchstgewicht oder ein Wiederholungsrekord
 * bei einem schon bekannten Gewicht. Ohne die beiden letzten würde die Double Progression fälschlich
 * als Plateau gelten: Nach einer Gewichtssteigerung fällt das e1RM (Epley überschätzt hohe
 * Wiederholungszahlen) und braucht einige Sessions, bis es den alten Bestwert wieder erreicht.
 *
 * @property confidence 0..1; steigt mit der Datenmenge im Fenster und der Eindeutigkeit des Signals.
 * @property slopeKgPerWeek Steigung der Regression über das Fenster in kg e1RM pro Woche.
 * @property relativeSlopePerWeek Steigung relativ zum mittleren e1RM (0,01 = 1 % pro Woche).
 * @property bestDate Session, in der der e1RM-Bestwert zuerst erreicht wurde.
 * @property lastProgressDate letzte Session mit Fortschritt (siehe oben).
 * @property sessionsSinceProgress Sessions nach [lastProgressDate].
 * @property daysSinceProgress Tage von [lastProgressDate] bis zur letzten Session (nicht bis heute:
 *   Trainingspausen sind kein Plateau).
 * @property stalledPeriods volle Plateau-Phasen ohne Fortschritt (Sessions- und Tagesschwelle gemeinsam).
 */
data class TrendResult(
    val status: TrendStatus,
    val confidence: Double,
    val slopeKgPerWeek: Double,
    val relativeSlopePerWeek: Double,
    val latestE1rmKg: Double?,
    val bestE1rmKg: Double?,
    val bestDate: LocalDate?,
    val lastProgressDate: LocalDate?,
    val sessionsSinceProgress: Int,
    val daysSinceProgress: Long,
    val stalledPeriods: Int,
    val sampleSize: Int,
)

/**
 * Plateau-Erkennung nach Plan: kein Fortschritt seit [TrendConfig.plateauSessions] Sessions und
 * [TrendConfig.plateauDays] Tagen, und die Regressionssteigung über das gleitende Fenster ist nahe 0.
 * Beide Schwellen müssen erfüllt sein, damit drei Sessions in einer Woche noch kein Plateau ergeben.
 */
object TrendAnalyzer {

    fun analyze(points: List<E1rmPoint>, config: TrendConfig = TrendConfig()): TrendResult {
        val sorted = points.filter { it.e1rmKg > 0.0 }.sortedBy { it.date }
        val n = sorted.size
        if (n == 0) {
            return TrendResult(
                status = TrendStatus.INSUFFICIENT_DATA,
                confidence = 0.0,
                slopeKgPerWeek = 0.0,
                relativeSlopePerWeek = 0.0,
                latestE1rmKg = null,
                bestE1rmKg = null,
                bestDate = null,
                lastProgressDate = null,
                sessionsSinceProgress = 0,
                daysSinceProgress = 0,
                stalledPeriods = 0,
                sampleSize = 0,
            )
        }

        val (bestIndex, lastProgressIndex) = progressIndices(sorted)
        val best = sorted[bestIndex]
        val lastProgress = sorted[lastProgressIndex]
        val latest = sorted.last()
        val sessionsSinceProgress = n - 1 - lastProgressIndex
        val daysSinceProgress = ChronoUnit.DAYS.between(lastProgress.date, latest.date)

        val window = sorted.takeLast(config.windowSessions)
        val origin = window.first().date
        val regression = linearRegression(
            xs = DoubleArray(window.size) { ChronoUnit.DAYS.between(origin, window[it].date).toDouble() },
            ys = DoubleArray(window.size) { window[it].e1rmKg },
        )
        val meanE1rm = window.sumOf { it.e1rmKg } / window.size
        val slopePerWeek = regression.slope * 7.0
        val relativeSlope = slopePerWeek / meanE1rm

        val stalledPeriods = minOf(
            sessionsSinceProgress / config.plateauSessions,
            (daysSinceProgress / config.plateauDays).toInt(),
        )

        fun result(status: TrendStatus, confidence: Double, periods: Int) = TrendResult(
            status = status,
            confidence = confidence,
            slopeKgPerWeek = slopePerWeek,
            relativeSlopePerWeek = relativeSlope,
            latestE1rmKg = latest.e1rmKg,
            bestE1rmKg = best.e1rmKg,
            bestDate = best.date,
            lastProgressDate = lastProgress.date,
            sessionsSinceProgress = sessionsSinceProgress,
            daysSinceProgress = daysSinceProgress,
            stalledPeriods = periods,
            sampleSize = n,
        )

        if (n < config.minSessions) return result(TrendStatus.INSUFFICIENT_DATA, 0.0, stalledPeriods)

        val dataFactor = minOf(1.0, window.size.toDouble() / config.windowSessions)
        val (status, signal) = when {
            relativeSlope <= -config.declineSlopePerWeek && sessionsSinceProgress >= 1 ->
                TrendStatus.DECLINING to regression.r2
            relativeSlope >= config.flatSlopePerWeek ->
                TrendStatus.PROGRESSING to regression.r2
            stalledPeriods >= 1 -> {
                val flatness = 1.0 - minOf(1.0, abs(relativeSlope) / config.flatSlopePerWeek)
                TrendStatus.PLATEAU to flatness
            }
            // Flach, aber der letzte Fortschritt ist frisch: noch Fortschritt, nur mit wenig Sicherheit.
            else -> TrendStatus.PROGRESSING to 0.0
        }
        val confidence = (dataFactor * (0.5 + 0.5 * signal.coerceIn(0.0, 1.0))).coerceIn(0.0, 1.0)
        return result(status, confidence, if (status == TrendStatus.PROGRESSING) 0 else stalledPeriods)
    }

    /**
     * Index des e1RM-Bestwerts (erstes Erreichen; ein gleich hoher Wert später ist kein Rekord) und
     * Index der letzten Session mit Fortschritt.
     */
    private fun progressIndices(sorted: List<E1rmPoint>): Pair<Int, Int> {
        var bestIndex = 0
        var lastProgressIndex = 0
        var maxTopWeight = sorted[0].topWeightKg
        val maxRepsByWeight = mutableMapOf<Long, Int>()
        sorted[0].weightKey()?.let { key -> sorted[0].repsAtTopWeight?.let { maxRepsByWeight[key] = it } }

        for (i in 1 until sorted.size) {
            val point = sorted[i]
            var progress = false
            if (point.e1rmKg > sorted[bestIndex].e1rmKg) {
                bestIndex = i
                progress = true
            }
            val weight = point.topWeightKg
            val reps = point.repsAtTopWeight
            val key = point.weightKey()
            if (weight != null && reps != null && key != null) {
                val previousMax = maxTopWeight
                val previousReps = maxRepsByWeight[key]
                if (previousMax != null && weight > previousMax + WEIGHT_EPSILON) progress = true
                if (previousReps != null && reps > previousReps) progress = true
                maxTopWeight = if (previousMax == null) weight else maxOf(previousMax, weight)
                maxRepsByWeight[key] = maxOf(previousReps ?: 0, reps)
            }
            if (progress) lastProgressIndex = i
        }
        return bestIndex to lastProgressIndex
    }

    private fun E1rmPoint.weightKey(): Long? = topWeightKg?.let { Math.round(it * 100.0) }

    /** e1RM-Verlauf einer Übung: eine Messung pro Session mit auswertbaren Arbeitssätzen. */
    fun points(history: ExerciseHistory, config: InsightsConfig = InsightsConfig()): List<E1rmPoint> =
        history.sessions
            .mapNotNull { session ->
                val e1rm = sessionEstimatedOneRepMax(session, history.profile, config.formula, config.useRpeForOneRepMax)
                    ?: return@mapNotNull null
                val workSets = session.sets.filter { it.isWorkSet }
                val topWeight = roundKg(workSets.maxOf { it.weightKg ?: 0.0 })
                val repsAtTop = workSets
                    .filter { abs((it.weightKg ?: 0.0) - topWeight) < WEIGHT_EPSILON }
                    .maxOf { it.reps ?: 0 }
                E1rmPoint(session.date, e1rm, topWeight, repsAtTop)
            }
            .sortedBy { it.date }

    private const val WEIGHT_EPSILON = 0.005
}

internal data class Regression(val slope: Double, val r2: Double)

/** Kleinste Quadrate; bei fehlender Streuung in x (alle am selben Tag) Steigung 0. */
internal fun linearRegression(xs: DoubleArray, ys: DoubleArray): Regression {
    require(xs.size == ys.size)
    val n = xs.size
    if (n < 2) return Regression(0.0, 0.0)
    val meanX = xs.average()
    val meanY = ys.average()
    var sxx = 0.0
    var sxy = 0.0
    var ssTot = 0.0
    for (i in 0 until n) {
        val dx = xs[i] - meanX
        val dy = ys[i] - meanY
        sxx += dx * dx
        sxy += dx * dy
        ssTot += dy * dy
    }
    if (sxx == 0.0) return Regression(0.0, 0.0)
    val slope = sxy / sxx
    var ssRes = 0.0
    for (i in 0 until n) {
        val residual = ys[i] - (meanY + slope * (xs[i] - meanX))
        ssRes += residual * residual
    }
    val r2 = if (ssTot == 0.0) 1.0 else (1.0 - ssRes / ssTot).coerceIn(0.0, 1.0)
    return Regression(slope, r2)
}
