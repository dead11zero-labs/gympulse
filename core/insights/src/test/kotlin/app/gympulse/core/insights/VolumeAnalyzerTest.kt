package app.gympulse.core.insights

import app.gympulse.core.insights.Fixtures.START
import app.gympulse.core.insights.Fixtures.bench
import app.gympulse.core.insights.Fixtures.session
import app.gympulse.core.insights.Fixtures.set
import app.gympulse.core.insights.Fixtures.sets
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MuscleGroup
import app.gympulse.core.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VolumeAnalyzerTest {

    /** Stichtag: Sonntag der vierten Woche ab [START], das Fenster umfasst also genau vier Wochen. */
    private val asOf: LocalDate = START.plusDays(27)

    private val row = ExerciseProfile(
        exerciseId = "row",
        primaryMuscles = listOf(MuscleGroup.MIDDLE_BACK),
        secondaryMuscles = listOf(MuscleGroup.LATS, MuscleGroup.BICEPS),
    )

    private val squat = ExerciseProfile(
        exerciseId = "squat",
        primaryMuscles = listOf(MuscleGroup.QUADRICEPS),
        secondaryMuscles = listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
    )

    /** Pro Woche eine Session mit [setsPerSession] harten Sätzen, über [weeks] Wochen ab [START]. */
    private fun weekly(profile: ExerciseProfile, setsPerSession: Int, weeks: Int = 4, start: LocalDate = START) =
        ExerciseHistory(profile, List(weeks) { w -> session(start.plusDays(w * 7L), sets(setsPerSession, 50.0, 10)) })

    @Test
    fun primaryCountsOneSecondaryHalf() {
        val report = VolumeAnalyzer.analyze(listOf(weekly(bench(), 12)), asOf)
        assertEquals(4, report.weeks)
        assertEquals(12.0, report.volumeOf(MuscleGroup.CHEST)!!.setsPerWeek, 1e-9)
        assertEquals(48.0, report.volumeOf(MuscleGroup.CHEST)!!.totalSets, 1e-9)
        assertEquals(6.0, report.volumeOf(MuscleGroup.SHOULDERS)!!.setsPerWeek, 1e-9)
        assertEquals(6.0, report.volumeOf(MuscleGroup.TRICEPS)!!.setsPerWeek, 1e-9)
    }

    @Test
    fun customWeights() {
        val config = VolumeConfig(secondaryWeight = 0.25)
        val report = VolumeAnalyzer.analyze(listOf(weekly(bench(), 12)), asOf, config)
        assertEquals(3.0, report.volumeOf(MuscleGroup.TRICEPS)!!.setsPerWeek, 1e-9)
    }

    @Test
    fun muscleListedAsPrimaryAndSecondaryCountsOnce() {
        val odd = bench().copy(secondaryMuscles = listOf(MuscleGroup.CHEST))
        val report = VolumeAnalyzer.analyze(listOf(weekly(odd, 10)), asOf)
        assertEquals(10.0, report.volumeOf(MuscleGroup.CHEST)!!.setsPerWeek, 1e-9)
    }

    @Test
    fun onlyHardSetsCount() {
        val history = ExerciseHistory(
            bench(),
            listOf(
                session(
                    START,
                    listOf(
                        set(40.0, 10, type = SetType.WARMUP),
                        set(80.0, 10, completed = false),
                        set(80.0, 0),
                        set(80.0, 10, rpe = 5.0), // zu leicht
                        set(80.0, 10, rpe = 6.0),
                        set(80.0, 10),
                        set(60.0, 8, type = SetType.DROP),
                        set(80.0, 6, type = SetType.FAILURE),
                    ),
                ),
            ),
        )
        val report = VolumeAnalyzer.analyze(listOf(history), START.plusDays(6), VolumeConfig(windowWeeks = 1))
        assertEquals(4.0, report.volumeOf(MuscleGroup.CHEST)!!.totalSets, 1e-9)

        val noRpeFilter = VolumeAnalyzer.analyze(listOf(history), START.plusDays(6), VolumeConfig(windowWeeks = 1, minHardSetRpe = null))
        assertEquals(5.0, noRpeFilter.volumeOf(MuscleGroup.CHEST)!!.totalSets, 1e-9)
    }

    @Test
    fun sessionsOutsideWindowAreIgnored() {
        val history = ExerciseHistory(
            bench(),
            listOf(
                session(START.minusDays(1), sets(10, 50.0, 10)), // vor dem Fenster
                session(START, sets(10, 50.0, 10)),
                session(asOf, sets(10, 50.0, 10)),
                session(asOf.plusDays(1), sets(10, 50.0, 10)), // nach dem Stichtag
            ),
        )
        val report = VolumeAnalyzer.analyze(listOf(history), asOf)
        assertEquals(START, report.fromDate)
        assertEquals(asOf, report.toDate)
        assertEquals(20.0, report.volumeOf(MuscleGroup.CHEST)!!.totalSets, 1e-9)
        assertEquals(5.0, report.volumeOf(MuscleGroup.CHEST)!!.setsPerWeek, 1e-9)
    }

    @Test
    fun youngHistory_averagesOverActualWeeks() {
        // Erst seit zehn Tagen dabei: zwei Wochen statt vier
        val start = asOf.minusDays(9)
        val history = ExerciseHistory(bench(), listOf(session(start, sets(10, 50.0, 10)), session(asOf, sets(10, 50.0, 10))))
        val report = VolumeAnalyzer.analyze(listOf(history), asOf)
        assertEquals(2, report.weeks)
        assertEquals(10.0, report.volumeOf(MuscleGroup.CHEST)!!.setsPerWeek, 1e-9)
    }

    @Test
    fun olderHistoryBeforeWindow_usesFullWindow() {
        val history = ExerciseHistory(
            bench(),
            listOf(session(START.minusDays(60), sets(10, 50.0, 10)), session(asOf, sets(12, 50.0, 10))),
        )
        val report = VolumeAnalyzer.analyze(listOf(history), asOf)
        assertEquals(4, report.weeks)
        assertEquals(3.0, report.volumeOf(MuscleGroup.CHEST)!!.setsPerWeek, 1e-9)
    }

    @Test
    fun statusAgainstTargets() {
        val report = VolumeAnalyzer.analyze(
            listOf(weekly(bench(), 24), weekly(row, 12), weekly(squat, 6)),
            asOf,
        )
        assertEquals(VolumeStatus.HIGH, report.volumeOf(MuscleGroup.CHEST)!!.status) // 24
        assertEquals(VolumeStatus.OPTIMAL, report.volumeOf(MuscleGroup.SHOULDERS)!!.status) // 12
        assertEquals(VolumeStatus.OPTIMAL, report.volumeOf(MuscleGroup.MIDDLE_BACK)!!.status) // 12
        assertEquals(VolumeStatus.LOW, report.volumeOf(MuscleGroup.QUADRICEPS)!!.status) // 6
        assertEquals(VolumeStatus.LOW, report.volumeOf(MuscleGroup.BICEPS)!!.status) // 6
    }

    @Test
    fun boundariesAreInclusive() {
        val low = VolumeAnalyzer.analyze(listOf(weekly(bench(), 10)), asOf)
        assertEquals(VolumeStatus.OPTIMAL, low.volumeOf(MuscleGroup.CHEST)!!.status)
        val high = VolumeAnalyzer.analyze(listOf(weekly(bench(), 20)), asOf)
        assertEquals(VolumeStatus.OPTIMAL, high.volumeOf(MuscleGroup.CHEST)!!.status)
    }

    @Test
    fun perMuscleTargetOverride() {
        val config = VolumeConfig(targets = mapOf(MuscleGroup.TRICEPS to VolumeTarget(4.0, 8.0)))
        val report = VolumeAnalyzer.analyze(listOf(weekly(bench(), 12)), asOf, config)
        assertEquals(VolumeStatus.OPTIMAL, report.volumeOf(MuscleGroup.TRICEPS)!!.status)
        assertEquals(VolumeTarget(4.0, 8.0), report.volumeOf(MuscleGroup.TRICEPS)!!.target)
    }

    @Test
    fun trackedMusclesWithoutSetsAreLow_untrackedAreOmitted() {
        val report = VolumeAnalyzer.analyze(listOf(weekly(bench(), 12)), asOf)
        val quads = report.volumeOf(MuscleGroup.QUADRICEPS)!!
        assertEquals(0.0, quads.setsPerWeek, 0.0)
        assertEquals(VolumeStatus.LOW, quads.status)
        assertNull(report.volumeOf(MuscleGroup.NECK))
        assertNull(report.volumeOf(MuscleGroup.CALVES))
        // Reihenfolge folgt der Enum-Reihenfolge
        assertEquals(report.muscles.sortedBy { it.muscle.ordinal }, report.muscles)
    }

    @Test
    fun noData_emptyReport() {
        val report = VolumeAnalyzer.analyze(emptyList(), asOf)
        assertFalse(report.hasData)
        assertTrue(report.imbalances.isEmpty())

        val onlyWarmups = ExerciseHistory(bench(), listOf(session(asOf, listOf(set(40.0, 10, type = SetType.WARMUP)))))
        assertFalse(VolumeAnalyzer.analyze(listOf(onlyWarmups), asOf).hasData)
    }

    @Test
    fun stretchingAndCardioAreExcluded() {
        val stretch = ExerciseProfile("stretch", category = ExerciseCategory.STRETCHING, primaryMuscles = listOf(MuscleGroup.HAMSTRINGS))
        val cardio = ExerciseProfile("bike", category = ExerciseCategory.CARDIO, primaryMuscles = listOf(MuscleGroup.QUADRICEPS))
        val report = VolumeAnalyzer.analyze(listOf(weekly(stretch, 5), weekly(cardio, 5), weekly(bench(), 10)), asOf)
        assertEquals(0.0, report.volumeOf(MuscleGroup.HAMSTRINGS)!!.setsPerWeek, 0.0)
        assertEquals(0.0, report.volumeOf(MuscleGroup.QUADRICEPS)!!.setsPerWeek, 0.0)
    }

    @Test
    fun pushPullImbalance() {
        // Druck: Brust 12 + Schulter 6 + Trizeps 6 = 24; Zug: Rücken 4 + Lat 2 + Bizeps 2 = 8
        val report = VolumeAnalyzer.analyze(listOf(weekly(bench(), 12), weekly(row, 4)), asOf)
        val imbalance = report.imbalances.single { it.pair == BalancePair.PUSH_PULL }
        assertEquals(24.0, imbalance.sideASetsPerWeek, 1e-9)
        assertEquals(8.0, imbalance.sideBSetsPerWeek, 1e-9)
        assertEquals(BalanceSide.A, imbalance.dominantSide)
        assertEquals(3.0, imbalance.ratio!!, 1e-9)
    }

    @Test
    fun balancedPushPull_noImbalance() {
        val report = VolumeAnalyzer.analyze(listOf(weekly(bench(), 10), weekly(row, 10)), asOf)
        assertTrue(report.imbalances.none { it.pair == BalancePair.PUSH_PULL })
    }

    @Test
    fun ratioJustBelowThreshold_noImbalance() {
        // 14 vs 10 = 1,4 < 1,5
        val report = VolumeAnalyzer.analyze(listOf(weekly(bench(), 7), weekly(row, 5)), asOf)
        assertTrue(report.imbalances.none { it.pair == BalancePair.PUSH_PULL })
    }

    @Test
    fun missingSide_imbalanceWithoutRatio() {
        val report = VolumeAnalyzer.analyze(listOf(weekly(squat, 8)), asOf)
        // Quads 8, Beinbeuger 4 → 2:1
        val legs = report.imbalances.single { it.pair == BalancePair.QUADS_HAMSTRINGS }
        assertEquals(2.0, legs.ratio!!, 1e-9)

        val pushOnly = VolumeAnalyzer.analyze(listOf(weekly(bench(), 10)), asOf)
        val push = pushOnly.imbalances.single { it.pair == BalancePair.PUSH_PULL }
        assertNull(push.ratio)
        assertEquals(BalanceSide.A, push.dominantSide)
    }

    @Test
    fun smallVolume_skipsBalanceCheck() {
        // Druck 4 Sätze pro Woche, Zug 0: unter der Mindestmenge, kein Befund
        val report = VolumeAnalyzer.analyze(listOf(weekly(bench(), 2)), asOf)
        assertTrue(report.imbalances.isEmpty())
    }
}
