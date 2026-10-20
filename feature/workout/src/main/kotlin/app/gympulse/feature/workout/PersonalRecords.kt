package app.gympulse.feature.workout

import app.gympulse.core.data.repository.HistorySet
import app.gympulse.core.model.SetType
import app.gympulse.core.model.estimatedOneRepMax
import app.gympulse.core.model.setVolumeKg

internal enum class RecordType { BEST_WEIGHT, ESTIMATED_ONE_REP_MAX, BEST_SET_VOLUME }

/** A new best of [type]: [valueKg] beats [previousKg] (0 if there was nothing to beat). */
internal data class PersonalRecord(
    val type: RecordType,
    val valueKg: Double,
    val previousKg: Double,
)

/**
 * Detects personal records of one session against all other completed sessions, per exercise.
 *
 * Rules:
 * - Only completed working sets with reps count; warm-up sets, open sets and soft-deleted sets
 *   (`deletedAt` set) are ignored, on both sides of the comparison.
 * - Best weight is the entered weight (for bodyweight exercises the additional load).
 * - The estimated 1RM (Epley) and the set volume use the effective load
 *   (bodyweight x factor + weight), like the volume calculation of the repository.
 * - A record must be strictly greater; a tie is no record.
 * - An exercise without any earlier eligible set has nothing to compare with: no records
 *   (the first session sets the baseline).
 */
internal class PersonalRecordDetector {

    /**
     * @param sessionId the session to check
     * @param sets sets of that session and of earlier sessions, any exercises
     * @return records per exercise id; exercises without records are left out
     */
    fun detect(sessionId: String, sets: List<HistorySet>): Map<String, List<PersonalRecord>> {
        val eligible = sets.filter { it.isEligible() }.groupBy { it.exerciseId }
        val result = linkedMapOf<String, List<PersonalRecord>>()
        for ((exerciseId, exerciseSets) in eligible) {
            val (current, earlier) = exerciseSets.partition { it.sessionId == sessionId }
            if (current.isEmpty() || earlier.isEmpty()) continue
            val records = RecordType.entries.mapNotNull { type ->
                val best = current.maxOf { metric(type, it) }
                val previous = earlier.maxOf { metric(type, it) }
                if (best > previous + EPSILON) PersonalRecord(type, best, previous) else null
            }
            if (records.isNotEmpty()) result[exerciseId] = records
        }
        return result
    }

    private fun HistorySet.isEligible(): Boolean =
        deletedAt == null && isCompleted && setType != SetType.WARMUP && (reps ?: 0) > 0

    /** 0 when the set has no value for [type], so it can never beat anything. */
    private fun metric(type: RecordType, set: HistorySet): Double {
        val reps = set.reps ?: 0
        return when (type) {
            RecordType.BEST_WEIGHT -> (set.weightKg ?: 0.0).coerceAtLeast(0.0)
            RecordType.ESTIMATED_ONE_REP_MAX -> {
                val load = setVolumeKg(set.weightKg, 1, set.bodyweightKg, set.bodyweightFactor)
                if (load > 0.0) estimatedOneRepMax(load, reps) else 0.0
            }
            RecordType.BEST_SET_VOLUME -> setVolumeKg(set.weightKg, reps, set.bodyweightKg, set.bodyweightFactor)
        }
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
