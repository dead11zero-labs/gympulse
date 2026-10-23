package app.gympulse.core.insights

import app.gympulse.core.insights.Fixtures.points
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeloadAdvisorTest {

    @Test
    fun singlePlateauPhase_noDeload() {
        val trend = TrendAnalyzer.analyze(points(100.0, 102.5, 105.0, 105.0, 104.0, 105.0, 104.5, 105.0))
        assertEquals(TrendStatus.PLATEAU, trend.status)
        assertEquals(1, trend.stalledPeriods)
        assertNull(DeloadAdvisor.evaluate(trend))
    }

    @Test
    fun secondPlateauPhase_deload() {
        // Rekord in Woche 1, danach sechs Sessions über sechs Wochen ohne Rekord
        val trend = TrendAnalyzer.analyze(points(100.0, 99.0, 100.0, 99.5, 99.0, 100.0, 99.5))
        assertEquals(TrendStatus.PLATEAU, trend.status)
        assertEquals(2, trend.stalledPeriods)
        assertEquals(DeloadReason.REPEATED_PLATEAU, DeloadAdvisor.evaluate(trend))
    }

    @Test
    fun plateausBeforeDeloadIsConfigurable() {
        val trend = TrendAnalyzer.analyze(points(100.0, 102.5, 105.0, 105.0, 104.0, 105.0, 104.5, 105.0))
        assertEquals(DeloadReason.REPEATED_PLATEAU, DeloadAdvisor.evaluate(trend, ProgressionConfig(plateausBeforeDeload = 1)))
    }

    @Test
    fun sustainedDecline_deload() {
        val trend = TrendAnalyzer.analyze(points(100.0, 102.0, 104.0, 101.0, 99.0, 97.0, 95.0))
        assertEquals(DeloadReason.SUSTAINED_DECLINE, DeloadAdvisor.evaluate(trend))
    }

    @Test
    fun shortDecline_noDeload() {
        // Rückgang erst seit zwei Sessions: noch keine volle Phase
        val trend = TrendAnalyzer.analyze(points(100.0, 102.0, 104.0, 106.0, 108.0, 100.0, 92.0))
        assertEquals(TrendStatus.DECLINING, trend.status)
        assertNull(DeloadAdvisor.evaluate(trend))
    }

    @Test
    fun progress_noDeload() {
        assertNull(DeloadAdvisor.evaluate(TrendAnalyzer.analyze(points(100.0, 102.5, 105.0, 107.5, 110.0))))
    }

    @Test
    fun insufficientData_noDeload() {
        assertNull(DeloadAdvisor.evaluate(TrendAnalyzer.analyze(points(100.0, 90.0))))
    }
}
