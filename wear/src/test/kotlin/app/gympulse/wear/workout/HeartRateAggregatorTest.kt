package app.gympulse.wear.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateAggregatorTest {
    private fun HeartRateAggregator.addAll(vararg values: Int) = values.forEach { add(it) }

    @Test
    fun noSamples_noStats() {
        assertNull(HeartRateAggregator().stats())
    }

    @Test
    fun belowMinimumSamples_noStats() {
        val aggregator = HeartRateAggregator()
        repeat(HeartRateAggregator.MIN_SAMPLES - 1) { aggregator.add(120) }
        assertNull(aggregator.stats())
    }

    @Test
    fun averageAndMax_overAllSamples() {
        val aggregator = HeartRateAggregator()
        aggregator.addAll(100, 110, 120, 130, 140, 150, 160, 170, 100, 120)

        val stats = aggregator.stats()!!

        assertEquals(130, stats.averageBpm)
        assertEquals(170, stats.maxBpm)
        assertEquals(10, stats.sampleCount)
    }

    @Test
    fun average_isRounded() {
        val aggregator = HeartRateAggregator()
        // Summe 1 205 bei 10 Werten: 120,5 wird auf 121 gerundet.
        aggregator.addAll(120, 121, 120, 121, 120, 121, 120, 121, 120, 121)
        assertEquals(121, aggregator.stats()!!.averageBpm)
    }

    @Test
    fun implausibleValues_areIgnored() {
        val aggregator = HeartRateAggregator()
        assertFalse(aggregator.add(0))
        assertFalse(aggregator.add(29))
        assertFalse(aggregator.add(251))
        assertFalse(aggregator.add(-5))
        assertTrue(aggregator.add(30))
        assertTrue(aggregator.add(250))

        repeat(8) { aggregator.add(100) }
        val stats = aggregator.stats()!!

        assertEquals(10, stats.sampleCount)
        assertEquals(250, stats.maxBpm)
    }

    @Test
    fun reset_startsOver() {
        val aggregator = HeartRateAggregator()
        repeat(20) { aggregator.add(180) }
        aggregator.reset()
        assertNull(aggregator.stats())

        repeat(10) { aggregator.add(90) }
        val stats = aggregator.stats()!!
        assertEquals(90, stats.averageBpm)
        assertEquals(90, stats.maxBpm)
    }
}
