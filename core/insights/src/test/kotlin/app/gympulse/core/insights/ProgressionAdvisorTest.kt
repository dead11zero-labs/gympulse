package app.gympulse.core.insights

import app.gympulse.core.insights.Fixtures.START
import app.gympulse.core.insights.Fixtures.bench
import app.gympulse.core.insights.Fixtures.session
import app.gympulse.core.insights.Fixtures.set
import app.gympulse.core.insights.Fixtures.sets
import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionAdvisorTest {

    private fun suggest(
        sets: List<PerformedSet>,
        profile: ExerciseProfile = bench(),
        deload: DeloadReason? = null,
        config: ProgressionConfig = ProgressionConfig(),
    ) = ProgressionAdvisor.suggest(profile, session(START, sets), deload, config)

    @Test
    fun allSetsAtTop_increasesBarbellBy2point5AndResetsReps() {
        val result = suggest(sets(3, 80.0, 12))!!
        assertEquals(ProgressionAction.INCREASE_WEIGHT, result.action)
        assertEquals(ProgressionReason.ALL_SETS_AT_TOP, result.reason)
        assertEquals(80.0, result.currentWeightKg, 0.0)
        assertEquals(82.5, result.suggestedWeightKg, 0.0)
        assertEquals(8, result.targetReps)
        assertEquals(2.5, result.incrementKg, 0.0)
    }

    @Test
    fun moreRepsThanTop_alsoIncreasesWeight() {
        assertEquals(ProgressionAction.INCREASE_WEIGHT, suggest(listOf(set(80.0, 13), set(80.0, 12)))!!.action)
    }

    @Test
    fun incrementFollowsEquipmentType() {
        val dumbbell = bench().copy(equipmentType = EquipmentType.DUMBBELL)
        assertEquals(26.0, suggest(sets(3, 24.0, 12), dumbbell)!!.suggestedWeightKg, 0.0)

        val machine = bench().copy(equipmentType = EquipmentType.MACHINE)
        assertEquals(55.0, suggest(sets(3, 50.0, 12), machine)!!.suggestedWeightKg, 0.0)

        val unknown = bench().copy(equipmentType = EquipmentType.OTHER)
        assertEquals(52.5, suggest(sets(3, 50.0, 12), unknown)!!.suggestedWeightKg, 0.0)
    }

    @Test
    fun profileIncrementOverridesEquipmentDefault() {
        val result = suggest(sets(3, 80.0, 12), bench(incrementKg = 1.25))!!
        assertEquals(81.25, result.suggestedWeightKg, 0.0)
        assertEquals(1.25, result.incrementKg, 0.0)
    }

    @Test
    fun floatingPointNoiseIsRounded() {
        assertEquals(10.3, suggest(sets(3, 9.1, 12), bench(incrementKg = 1.2))!!.suggestedWeightKg, 0.0)
    }

    @Test
    fun rpeAboveLimit_holdsWeight() {
        val result = suggest(listOf(set(80.0, 12, rpe = 8.0), set(80.0, 12, rpe = 9.0)))!!
        assertEquals(ProgressionAction.HOLD, result.action)
        assertEquals(ProgressionReason.RPE_TOO_HIGH, result.reason)
        assertEquals(80.0, result.suggestedWeightKg, 0.0)
        assertEquals(12, result.targetReps)
    }

    @Test
    fun rpeAtLimitOrMissing_allowsIncrease() {
        assertEquals(
            ProgressionAction.INCREASE_WEIGHT,
            suggest(listOf(set(80.0, 12, rpe = 8.0), set(80.0, 12), set(80.0, 12, rpe = 7.5)))!!.action,
        )
    }

    @Test
    fun customRpeLimit() {
        val strict = ProgressionConfig(maxRpeForIncrease = 7.0)
        assertEquals(ProgressionAction.HOLD, suggest(sets(3, 80.0, 12, rpe = 7.5), config = strict)!!.action)
    }

    @Test
    fun repsBelowTop_increasesRepsOfWeakestSet() {
        val result = suggest(listOf(set(80.0, 11), set(80.0, 10), set(80.0, 9)))!!
        assertEquals(ProgressionAction.INCREASE_REPS, result.action)
        assertEquals(ProgressionReason.REPS_BELOW_TOP, result.reason)
        assertEquals(80.0, result.suggestedWeightKg, 0.0)
        assertEquals(10, result.targetReps)
    }

    @Test
    fun oneSetShortOfTop_targetsTop() {
        val result = suggest(listOf(set(80.0, 12), set(80.0, 12), set(80.0, 11)))!!
        assertEquals(ProgressionAction.INCREASE_REPS, result.action)
        assertEquals(12, result.targetReps)
    }

    @Test
    fun belowRangeMinimum_holds() {
        val result = suggest(listOf(set(80.0, 9), set(80.0, 7)))!!
        assertEquals(ProgressionAction.HOLD, result.action)
        assertEquals(ProgressionReason.BELOW_MIN_REPS, result.reason)
        assertEquals(8, result.targetReps)
        assertEquals(80.0, result.suggestedWeightKg, 0.0)
    }

    @Test
    fun fewerSetsThanPlanned_holds() {
        val result = suggest(sets(2, 80.0, 12) + set(80.0, 12, completed = false), bench(targetSets = 3))!!
        assertEquals(ProgressionAction.HOLD, result.action)
        assertEquals(ProgressionReason.SETS_MISSING, result.reason)
        assertEquals(12, result.targetReps)
    }

    @Test
    fun plannedSetsCompleted_increases() {
        assertEquals(ProgressionAction.INCREASE_WEIGHT, suggest(sets(3, 80.0, 12), bench(targetSets = 3))!!.action)
    }

    @Test
    fun backOffAndDropSetsDoNotBlockProgression() {
        val result = suggest(
            sets(3, 100.0, 12) + set(80.0, 8) + set(60.0, 6, type = SetType.DROP),
        )!!
        assertEquals(ProgressionAction.INCREASE_WEIGHT, result.action)
        assertEquals(100.0, result.currentWeightKg, 0.0)
        assertEquals(102.5, result.suggestedWeightKg, 0.0)
    }

    @Test
    fun warmupsAndOpenSetsAreIgnored() {
        val result = suggest(
            listOf(
                set(40.0, 5, type = SetType.WARMUP),
                set(120.0, 2, completed = false),
            ) + sets(3, 80.0, 12),
        )!!
        assertEquals(ProgressionAction.INCREASE_WEIGHT, result.action)
        assertEquals(80.0, result.currentWeightKg, 0.0)
    }

    @Test
    fun withoutRepRange_noSuggestion() {
        assertNull(suggest(sets(3, 80.0, 12), bench(repRange = null)))
    }

    @Test
    fun defaultRepRangeFromConfig() {
        val config = ProgressionConfig(defaultRepRange = RepRange(5, 5))
        val result = suggest(sets(5, 100.0, 5), bench(repRange = null), config = config)!!
        assertEquals(ProgressionAction.INCREASE_WEIGHT, result.action)
        assertEquals(5, result.targetReps)
    }

    @Test
    fun noWorkSets_noSuggestion() {
        assertNull(suggest(listOf(set(40.0, 10, type = SetType.WARMUP))))
        assertNull(suggest(emptyList()))
    }

    @Test
    fun bodyweightExerciseAtTop_addsExtraWeight() {
        val pullUp = ExerciseProfile(
            exerciseId = "pullup",
            equipmentType = EquipmentType.BODYWEIGHT,
            bodyweightFactor = 1.0,
            targetRepRange = RepRange(6, 10),
        )
        val result = suggest(List(3) { set(null, 10) }, pullUp)!!
        assertEquals(ProgressionAction.INCREASE_WEIGHT, result.action)
        assertEquals(0.0, result.currentWeightKg, 0.0)
        assertEquals(2.5, result.suggestedWeightKg, 0.0)
        assertEquals(6, result.targetReps)
    }

    @Test
    fun deload_reducesByTenPercentRoundedDownToIncrement() {
        val result = suggest(sets(3, 100.0, 10), deload = DeloadReason.REPEATED_PLATEAU)!!
        assertEquals(ProgressionAction.DELOAD, result.action)
        assertEquals(ProgressionReason.REPEATED_PLATEAU, result.reason)
        assertEquals(90.0, result.suggestedWeightKg, 0.0)
        assertEquals(8, result.targetReps)

        // 62,5 × 0,9 = 56,25 → abgerundet auf 2,5er-Schritte = 55
        assertEquals(55.0, suggest(sets(3, 62.5, 10), deload = DeloadReason.SUSTAINED_DECLINE)!!.suggestedWeightKg, 0.0)
        assertEquals(
            ProgressionReason.SUSTAINED_DECLINE,
            suggest(sets(3, 62.5, 10), deload = DeloadReason.SUSTAINED_DECLINE)!!.reason,
        )
    }

    @Test
    fun deload_worksWithoutRepRange() {
        val result = suggest(listOf(set(100.0, 6), set(100.0, 5)), bench(repRange = null), DeloadReason.REPEATED_PLATEAU)!!
        assertEquals(ProgressionAction.DELOAD, result.action)
        assertEquals(5, result.targetReps)
    }

    @Test
    fun deloadWeight_edgeCases() {
        val config = ProgressionConfig()
        assertEquals(0.0, ProgressionAdvisor.deloadWeight(0.0, 2.5, config), 0.0)
        // Kleiner als ein Schritt: nicht auf 0 abrunden, sondern −10 %
        assertEquals(1.8, ProgressionAdvisor.deloadWeight(2.0, 2.5, config), 1e-9)
        // Exakt auf dem Raster bleibt erhalten
        assertEquals(45.0, ProgressionAdvisor.deloadWeight(50.0, 2.5, config), 0.0)
        assertEquals(72.0, ProgressionAdvisor.deloadWeight(80.0, 4.0, config), 0.0)
        assertEquals(80.0, ProgressionAdvisor.deloadWeight(100.0, 2.5, ProgressionConfig(deloadFraction = 0.2)), 0.0)
    }

    @Test
    fun suggestFromHistory_usesLatestSessionWithWorkSets() {
        val history = ExerciseHistory(
            profile = bench(),
            sessions = listOf(
                session(START.plusDays(7), sets(3, 82.5, 9)),
                session(START, sets(3, 80.0, 12)),
                session(START.plusDays(10), listOf(set(40.0, 10, type = SetType.WARMUP))),
            ),
        )
        val result = ProgressionAdvisor.suggest(history)!!
        assertEquals(ProgressionAction.INCREASE_REPS, result.action)
        assertEquals(82.5, result.currentWeightKg, 0.0)
        assertEquals(10, result.targetReps)
    }

    @Test
    fun suggestFromHistory_emptyHistory() {
        assertNull(ProgressionAdvisor.suggest(ExerciseHistory(bench(), emptyList())))
    }
}
