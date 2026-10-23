package app.gympulse.wear.workout

import app.gympulse.core.wearprotocol.HeartRateLimits
import kotlin.math.roundToInt

/** Ø/Max eines Workouts in Schlägen pro Minute; [sampleCount] Messwerte flossen ein. */
data class HeartRateStats(
    val averageBpm: Int,
    val maxBpm: Int,
    val sampleCount: Int,
)

/**
 * Sammelt Pulswerte eines Workouts und liefert daraus Ø und Max. Reines Kotlin ohne Android-Bezug.
 *
 * - Werte außerhalb von [HeartRateLimits] (Messfehler, 0 bei Hautkontakt-Verlust) werden verworfen.
 * - Der Durchschnitt ist das arithmetische Mittel aller gültigen Messwerte. Die Uhr liefert etwa im
 *   Sekundentakt, deshalb braucht es keine Zeitgewichtung.
 * - Unter [MIN_SAMPLES] Werten gibt es keine Zusammenfassung; ein paar Sekunden Messung sagen nichts.
 * - Thread-sicher, weil Messwerte und Workout-Ende auf verschiedenen Threads ankommen können.
 */
class HeartRateAggregator {
    private val lock = Any()
    private var sum = 0L
    private var max = 0
    private var count = 0

    /** @return true, wenn [bpm] gültig war und zählt */
    fun add(bpm: Int): Boolean {
        if (bpm !in HeartRateLimits.MIN_BPM..HeartRateLimits.MAX_BPM) return false
        synchronized(lock) {
            sum += bpm
            if (bpm > max) max = bpm
            count += 1
        }
        return true
    }

    fun reset() {
        synchronized(lock) {
            sum = 0
            max = 0
            count = 0
        }
    }

    /** Stand bis jetzt oder null, solange weniger als [MIN_SAMPLES] gültige Werte vorliegen. */
    fun stats(): HeartRateStats? = synchronized(lock) {
        if (count < MIN_SAMPLES) {
            null
        } else {
            HeartRateStats(
                averageBpm = (sum.toDouble() / count).roundToInt(),
                maxBpm = max,
                sampleCount = count,
            )
        }
    }

    companion object {
        const val MIN_SAMPLES = 10
    }
}
