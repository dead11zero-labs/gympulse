package app.gympulse.core.insights

import app.gympulse.core.insights.Fixtures.START
import app.gympulse.core.insights.Fixtures.bench
import app.gympulse.core.insights.Fixtures.session
import app.gympulse.core.insights.Fixtures.set
import app.gympulse.core.model.SetType
import app.gympulse.core.model.estimatedOneRepMax
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OneRepMaxTest {

    @Test
    fun epley_matchesKnownValues() {
        assertEquals(133.33, OneRepMax.epley(100.0, 10.0), 0.01)
        assertEquals(116.67, OneRepMax.epley(100.0, 5.0), 0.01)
    }

    @Test
    fun epley_isConsistentWithCoreModel() {
        for (reps in 1..15) {
            assertEquals(estimatedOneRepMax(80.0, reps), OneRepMax.epley(80.0, reps.toDouble()), 1e-9)
        }
    }

    @Test
    fun brzycki_matchesKnownValues() {
        assertEquals(133.33, OneRepMax.brzycki(100.0, 10.0), 0.01)
        assertEquals(112.5, OneRepMax.brzycki(100.0, 5.0), 0.01)
    }

    @Test
    fun singleRep_isTheWeightItself() {
        assertEquals(140.0, OneRepMax.epley(140.0, 1.0), 0.0)
        assertEquals(140.0, OneRepMax.brzycki(140.0, 1.0), 0.0)
        assertEquals(140.0, OneRepMax.estimate(140.0, 1)!!, 0.0)
    }

    @Test
    fun estimate_usesRpeAsRepsInReserve() {
        // 5 Wdh. bei RPE 8 = 2 Wdh. im Tank, also wie 7 Wdh. bis zum Versagen
        assertEquals(OneRepMax.epley(100.0, 7.0), OneRepMax.estimate(100.0, 5, rpe = 8.0)!!, 1e-9)
        // RPE 10 ändert nichts
        assertEquals(OneRepMax.epley(100.0, 5.0), OneRepMax.estimate(100.0, 5, rpe = 10.0)!!, 1e-9)
        // halbe RPE-Stufen
        assertEquals(OneRepMax.epley(100.0, 6.5), OneRepMax.estimate(100.0, 5, rpe = 8.5)!!, 1e-9)
    }

    @Test
    fun estimate_ignoresImplausibleRpe() {
        assertEquals(OneRepMax.epley(100.0, 5.0), OneRepMax.estimate(100.0, 5, rpe = 4.0)!!, 1e-9)
        assertEquals(OneRepMax.epley(100.0, 5.0), OneRepMax.estimate(100.0, 5, rpe = 11.0)!!, 1e-9)
    }

    @Test
    fun estimate_returnsNullForUnusableInput() {
        assertNull(OneRepMax.estimate(0.0, 5))
        assertNull(OneRepMax.estimate(-10.0, 5))
        assertNull(OneRepMax.estimate(100.0, 0))
        assertNull(OneRepMax.estimate(50.0, 21))
        // 18 Wdh. + 3 RIR = 21 effektive Wdh.
        assertNull(OneRepMax.estimate(50.0, 18, rpe = 7.0))
    }

    @Test
    fun estimate_supportsBrzycki() {
        assertEquals(112.5, OneRepMax.estimate(100.0, 5, formula = OneRepMaxFormula.BRZYCKI)!!, 0.01)
    }

    @Test
    fun sessionE1rm_takesBestWorkSetAndSkipsWarmupsAndOpenSets() {
        val s = session(
            START,
            listOf(
                set(200.0, 5, type = SetType.WARMUP), // wäre am höchsten, ist aber Aufwärmen
                set(180.0, 5, completed = false), // nicht abgeschlossen
                set(100.0, 5),
                set(105.0, 3),
                set(90.0, 0),
            ),
        )
        assertEquals(OneRepMax.epley(100.0, 5.0), sessionEstimatedOneRepMax(s, bench())!!, 1e-9)
    }

    @Test
    fun sessionE1rm_countsDropAndFailureSets() {
        val s = session(START, listOf(set(100.0, 5), set(110.0, 4, type = SetType.FAILURE)))
        assertEquals(OneRepMax.epley(110.0, 4.0), sessionEstimatedOneRepMax(s, bench())!!, 1e-9)
    }

    @Test
    fun sessionE1rm_bodyweightExerciseUsesBodyweightShare() {
        val pullUp = ExerciseProfile(exerciseId = "pullup", bodyweightFactor = 1.0)
        val s = session(START, listOf(set(10.0, 5)), bodyweightKg = 80.0)
        assertEquals(OneRepMax.epley(90.0, 5.0), sessionEstimatedOneRepMax(s, pullUp)!!, 1e-9)

        val withoutExtra = session(START, listOf(set(null, 8)), bodyweightKg = 80.0)
        assertEquals(OneRepMax.epley(80.0, 8.0), sessionEstimatedOneRepMax(withoutExtra, pullUp)!!, 1e-9)
    }

    @Test
    fun sessionE1rm_bodyweightExerciseWithoutBodyweightIsNull() {
        val pullUp = ExerciseProfile(exerciseId = "pullup", bodyweightFactor = 1.0)
        assertNull(sessionEstimatedOneRepMax(session(START, listOf(set(10.0, 5))), pullUp))
    }

    @Test
    fun sessionE1rm_canIgnoreRpe() {
        val s = session(START, listOf(set(100.0, 5, rpe = 7.0)))
        assertEquals(OneRepMax.epley(100.0, 5.0), sessionEstimatedOneRepMax(s, bench(), useRpe = false)!!, 1e-9)
        assertEquals(OneRepMax.epley(100.0, 8.0), sessionEstimatedOneRepMax(s, bench(), useRpe = true)!!, 1e-9)
    }

    @Test
    fun sessionE1rm_nullWithoutWorkSets() {
        assertNull(sessionEstimatedOneRepMax(session(START, listOf(set(60.0, 10, type = SetType.WARMUP))), bench()))
        assertNull(sessionEstimatedOneRepMax(session(START, emptyList()), bench()))
    }
}
