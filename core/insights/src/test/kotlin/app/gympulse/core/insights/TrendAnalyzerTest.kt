package app.gympulse.core.insights

import app.gympulse.core.insights.Fixtures.START
import app.gympulse.core.insights.Fixtures.bench
import app.gympulse.core.insights.Fixtures.points
import app.gympulse.core.insights.Fixtures.session
import app.gympulse.core.insights.Fixtures.set
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendAnalyzerTest {

    @Test
    fun noData_isInsufficient() {
        val result = TrendAnalyzer.analyze(emptyList())
        assertEquals(TrendStatus.INSUFFICIENT_DATA, result.status)
        assertEquals(0.0, result.confidence, 0.0)
        assertNull(result.bestE1rmKg)
        assertEquals(0, result.sampleSize)
    }

    @Test
    fun fewerThanMinSessions_isInsufficientButReportsBest() {
        val result = TrendAnalyzer.analyze(points(100.0, 105.0, 110.0))
        assertEquals(TrendStatus.INSUFFICIENT_DATA, result.status)
        assertEquals(110.0, result.bestE1rmKg!!, 0.0)
        assertEquals(110.0, result.latestE1rmKg!!, 0.0)
        assertEquals(3, result.sampleSize)
    }

    @Test
    fun steadyLinearProgress_isProgressingWithHighConfidence() {
        val result = TrendAnalyzer.analyze(points(100.0, 102.5, 105.0, 107.5, 110.0, 112.5))
        assertEquals(TrendStatus.PROGRESSING, result.status)
        assertEquals(2.5, result.slopeKgPerWeek, 1e-9)
        assertEquals(1.0, result.confidence, 1e-9) // volles Fenster, perfekte Gerade
        assertEquals(0, result.sessionsSinceProgress)
        assertEquals(0, result.stalledPeriods)
    }

    @Test
    fun noisyProgress_isProgressingWithLowerConfidence() {
        val clean = TrendAnalyzer.analyze(points(100.0, 102.5, 105.0, 107.5, 110.0, 112.5))
        val noisy = TrendAnalyzer.analyze(points(100.0, 104.0, 102.0, 108.0, 106.0, 112.0))
        assertEquals(TrendStatus.PROGRESSING, noisy.status)
        assertTrue(noisy.confidence < clean.confidence)
        assertTrue(noisy.confidence >= 0.5)
    }

    @Test
    fun flatAfterBest_isPlateau() {
        // Bestwert 105 in Woche 3, danach fünf Wochen ohne neuen Rekord bei flacher Kurve
        val result = TrendAnalyzer.analyze(points(100.0, 102.5, 105.0, 105.0, 104.0, 105.0, 104.5, 105.0))
        assertEquals(TrendStatus.PLATEAU, result.status)
        assertEquals(105.0, result.bestE1rmKg!!, 0.0)
        assertEquals(Fixtures.START.plusDays(14), result.bestDate)
        assertEquals(5, result.sessionsSinceProgress)
        assertEquals(35L, result.daysSinceProgress)
        assertEquals(1, result.stalledPeriods)
        assertTrue(kotlin.math.abs(result.relativeSlopePerWeek) < 0.005)
        assertTrue(result.confidence in 0.5..1.0)
    }

    @Test
    fun plateauNeedsBothSessionAndDayThreshold() {
        // Fünf Sessions ohne Rekord, aber alle innerhalb von zwei Wochen: noch kein Plateau
        val dense = TrendAnalyzer.analyze(points(100.0, 105.0, 104.5, 105.0, 104.0, 105.0, 104.5, everyDays = 2))
        assertEquals(TrendStatus.PROGRESSING, dense.status)
        assertEquals(0, dense.stalledPeriods)

        // Lange Pause, aber nur zwei Sessions ohne Rekord: ebenfalls kein Plateau
        val sparse = TrendAnalyzer.analyze(points(100.0, 102.0, 104.0, 105.0, 104.5, 105.0, everyDays = 21))
        assertEquals(2, sparse.sessionsSinceProgress) // Bestwert 105 an Index 3
        assertTrue(sparse.daysSinceProgress >= 21)
        assertEquals(TrendStatus.PROGRESSING, sparse.status)
        assertEquals(0, sparse.stalledPeriods)
    }

    @Test
    fun longPlateau_countsStalledPeriods() {
        // Rekord in Woche 1, danach neun Wochen um den Bestwert herum
        val result = TrendAnalyzer.analyze(
            points(100.0, 99.5, 100.0, 99.0, 100.0, 99.5, 100.0, 99.5, 99.0, 100.0),
        )
        assertEquals(TrendStatus.PLATEAU, result.status)
        assertEquals(9, result.sessionsSinceProgress)
        assertEquals(63L, result.daysSinceProgress)
        assertEquals(3, result.stalledPeriods)
    }

    @Test
    fun equalValueLaterIsNoNewBest() {
        val result = TrendAnalyzer.analyze(points(100.0, 98.0, 99.0, 100.0))
        assertEquals(Fixtures.START, result.bestDate)
        assertEquals(3, result.sessionsSinceProgress)
    }

    @Test
    fun fallingE1rm_isDeclining() {
        val result = TrendAnalyzer.analyze(points(100.0, 102.0, 104.0, 101.0, 99.0, 97.0, 95.0))
        assertEquals(TrendStatus.DECLINING, result.status)
        assertTrue(result.slopeKgPerWeek < 0.0)
        assertTrue(result.relativeSlopePerWeek <= -0.01)
        assertEquals(4, result.sessionsSinceProgress)
        assertEquals(1, result.stalledPeriods)
    }

    @Test
    fun slightDip_isNotDeclining() {
        // −0,25 kg pro Woche bei ~100 kg liegt unter der Rückgangsschwelle von 1 %
        val result = TrendAnalyzer.analyze(points(101.0, 100.75, 100.5, 100.25, 100.0, 99.75))
        assertTrue(result.status != TrendStatus.DECLINING)
        assertEquals(TrendStatus.PLATEAU, result.status)
    }

    @Test
    fun flatButFreshBest_isProgressingWithLowConfidence() {
        val result = TrendAnalyzer.analyze(points(105.0, 104.0, 105.0, 104.5, 104.0, 105.5))
        assertEquals(TrendStatus.PROGRESSING, result.status)
        assertEquals(0, result.sessionsSinceProgress)
        assertEquals(0.5, result.confidence, 1e-9)
    }

    @Test
    fun windowUsesOnlyRecentSessions() {
        // Früher steiler Anstieg, zuletzt sechs flache Sessions: Plateau, nicht Fortschritt
        val result = TrendAnalyzer.analyze(
            points(60.0, 70.0, 80.0, 90.0, 100.0, 100.0, 99.0, 100.0, 99.5, 100.0, 99.0),
        )
        assertEquals(TrendStatus.PLATEAU, result.status)
        assertEquals(11, result.sampleSize)
    }

    @Test
    fun confidenceGrowsWithMoreData() {
        val four = TrendAnalyzer.analyze(points(100.0, 102.5, 105.0, 107.5))
        val six = TrendAnalyzer.analyze(points(100.0, 102.5, 105.0, 107.5, 110.0, 112.5))
        assertTrue(four.confidence < six.confidence)
    }

    @Test
    fun unsortedInputAndSameDaySessionsAreHandled() {
        val day = START
        val result = TrendAnalyzer.analyze(
            listOf(
                E1rmPoint(day.plusDays(14), 105.0),
                E1rmPoint(day, 100.0),
                E1rmPoint(day, 101.0),
                E1rmPoint(day.plusDays(7), 103.0),
            ),
        )
        assertEquals(TrendStatus.PROGRESSING, result.status)
        assertEquals(105.0, result.latestE1rmKg!!, 0.0)
        assertTrue(result.slopeKgPerWeek > 0.0)
    }

    @Test
    fun allSessionsOnOneDay_doNotCrash() {
        val result = TrendAnalyzer.analyze(List(5) { E1rmPoint(START, 100.0 + it) })
        assertEquals(0.0, result.slopeKgPerWeek, 0.0)
        assertEquals(TrendStatus.PROGRESSING, result.status)
    }

    @Test
    fun points_skipSessionsWithoutUsableSetsAndSortByDate() {
        val history = ExerciseHistory(
            profile = bench(),
            sessions = listOf(
                session(START.plusDays(7), listOf(set(100.0, 1))),
                session(START, listOf(set(95.0, 1))),
                session(START.plusDays(3), listOf(set(null, 10))), // kein Gewicht
            ),
        )
        val result = TrendAnalyzer.points(history)
        assertEquals(listOf(E1rmPoint(START, 95.0, 95.0, 1), E1rmPoint(START.plusDays(7), 100.0, 100.0, 1)), result)
    }

    @Test
    fun points_recordTopWeightAndRepsAtTopWeight() {
        val history = ExerciseHistory(
            profile = bench(),
            sessions = listOf(session(START, listOf(set(100.0, 5), set(100.0, 6), set(80.0, 12)))),
        )
        val point = TrendAnalyzer.points(history).single()
        assertEquals(100.0, point.topWeightKg!!, 0.0)
        assertEquals(6, point.repsAtTopWeight)
    }

    private fun weightReps(vararg sessions: Pair<Double, Int>): List<E1rmPoint> =
        sessions.mapIndexed { i, (kg, reps) ->
            E1rmPoint(START.plusDays(i * 7L), OneRepMax.epley(kg, reps.toDouble()), kg, reps)
        }

    @Test
    fun doubleProgressionSawtooth_isNotAPlateau() {
        // e1RM fällt nach der Steigerung auf 82,5 kg unter den Bestwert von 80 × 12,
        // aber Höchstgewicht und Wiederholungsrekorde zeigen weiter Fortschritt.
        val result = TrendAnalyzer.analyze(
            weightReps(80.0 to 8, 80.0 to 9, 80.0 to 10, 80.0 to 11, 80.0 to 12, 82.5 to 8, 82.5 to 9, 82.5 to 10),
        )
        assertEquals(TrendStatus.PROGRESSING, result.status)
        assertEquals(OneRepMax.epley(80.0, 12.0), result.bestE1rmKg!!, 1e-9)
        assertEquals(START.plusDays(28), result.bestDate)
        assertEquals(START.plusDays(49), result.lastProgressDate)
        assertEquals(0, result.sessionsSinceProgress)
    }

    @Test
    fun withoutWeightInfo_sameSawtoothLooksLikePlateau() {
        val result = TrendAnalyzer.analyze(
            weightReps(80.0 to 8, 80.0 to 9, 80.0 to 10, 80.0 to 11, 80.0 to 12, 82.5 to 8, 82.5 to 9, 82.5 to 10)
                .map { it.copy(topWeightKg = null, repsAtTopWeight = null) },
        )
        assertEquals(TrendStatus.PLATEAU, result.status)
    }

    @Test
    fun repRecordAtKnownWeight_countsAsProgress() {
        val result = TrendAnalyzer.analyze(weightReps(100.0 to 6, 100.0 to 5, 100.0 to 5, 100.0 to 4, 100.0 to 5, 95.0 to 7))
        // 95 × 7 ist weder e1RM-Rekord noch neues Höchstgewicht, und 95 kg gab es vorher nicht
        assertEquals(START, result.lastProgressDate)
        val withRepPr = TrendAnalyzer.analyze(weightReps(100.0 to 5, 100.0 to 4, 100.0 to 4, 100.0 to 4, 100.0 to 5, 100.0 to 6))
        assertEquals(START.plusDays(35), withRepPr.lastProgressDate)
        assertEquals(0, withRepPr.sessionsSinceProgress)
    }

    @Test
    fun sameRepsAtKnownWeight_isNoProgress() {
        val result = TrendAnalyzer.analyze(weightReps(100.0 to 5, 100.0 to 5, 100.0 to 5, 100.0 to 5, 100.0 to 5))
        assertEquals(START, result.lastProgressDate)
        assertEquals(4, result.sessionsSinceProgress)
        assertEquals(TrendStatus.PLATEAU, result.status)
    }

    @Test
    fun linearRegression_basics() {
        val perfect = linearRegression(doubleArrayOf(0.0, 1.0, 2.0), doubleArrayOf(1.0, 3.0, 5.0))
        assertEquals(2.0, perfect.slope, 1e-12)
        assertEquals(1.0, perfect.r2, 1e-12)

        val constant = linearRegression(doubleArrayOf(0.0, 1.0, 2.0), doubleArrayOf(4.0, 4.0, 4.0))
        assertEquals(0.0, constant.slope, 1e-12)
        assertEquals(1.0, constant.r2, 1e-12)

        assertEquals(0.0, linearRegression(doubleArrayOf(1.0), doubleArrayOf(1.0)).slope, 0.0)
    }
}
