package app.gympulse.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeTest {
    @Test
    fun weightedSet_usesWeightTimesReps() {
        assertEquals(1000.0, setVolumeKg(weightKg = 100.0, reps = 10, bodyweightKg = 80.0, bodyweightFactor = null), 0.001)
    }

    @Test
    fun bodyweightSet_usesBodyweightShare() {
        // Klimmzug: 80 kg × 1,0 × 8 Wdh
        assertEquals(640.0, setVolumeKg(weightKg = null, reps = 8, bodyweightKg = 80.0, bodyweightFactor = 1.0), 0.001)
    }

    @Test
    fun weightedBodyweightSet_addsExtraWeight() {
        // Klimmzug mit 10 kg Zusatzgewicht
        assertEquals(450.0, setVolumeKg(weightKg = 10.0, reps = 5, bodyweightKg = 80.0, bodyweightFactor = 1.0), 0.001)
    }

    @Test
    fun missingRepsOrBodyweight_isZeroOrWeightOnly() {
        assertEquals(0.0, setVolumeKg(weightKg = 50.0, reps = null, bodyweightKg = 80.0, bodyweightFactor = null), 0.001)
        assertEquals(0.0, setVolumeKg(weightKg = null, reps = 10, bodyweightKg = null, bodyweightFactor = 0.65), 0.001)
    }

    @Test
    fun epley_oneRepIsWeight() {
        assertEquals(100.0, estimatedOneRepMax(100.0, 1), 0.001)
        assertEquals(133.333, estimatedOneRepMax(100.0, 10), 0.001)
    }
}
