package app.gympulse.core.insightcards

import app.gympulse.core.insights.BalancePair
import app.gympulse.core.insights.BalanceSide
import app.gympulse.core.insights.ExerciseInsight
import app.gympulse.core.insights.Insight
import app.gympulse.core.insights.InsightCode
import app.gympulse.core.insights.MuscleImbalance
import app.gympulse.core.insights.MuscleVolume
import app.gympulse.core.insights.ProgressionAction
import app.gympulse.core.insights.ProgressionReason
import app.gympulse.core.insights.ProgressionSuggestion
import app.gympulse.core.insights.TrainingInsights
import app.gympulse.core.insights.TrendResult
import app.gympulse.core.insights.TrendStatus
import app.gympulse.core.insights.VolumeReport
import app.gympulse.core.insights.VolumeStatus
import app.gympulse.core.insights.VolumeTarget
import app.gympulse.core.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class InsightCardUiTest {
    private val asOf = LocalDate.of(2026, 10, 22)
    private val names = mapOf("bench" to "Bench Press", "squat" to "Squat", "row" to "Row")

    private fun trend(
        status: TrendStatus,
        latest: Double? = 100.0,
        best: Double? = 105.0,
        slope: Double = 0.0,
        days: Long = 28,
        sessions: Int = 4,
    ) = TrendResult(
        status = status, confidence = 0.8, slopeKgPerWeek = slope, relativeSlopePerWeek = 0.0,
        latestE1rmKg = latest, bestE1rmKg = best, bestDate = asOf.minusDays(days), lastProgressDate = asOf.minusDays(days),
        sessionsSinceProgress = sessions, daysSinceProgress = days, stalledPeriods = 1, sampleSize = 6,
    )

    private fun suggestion(
        id: String,
        action: ProgressionAction,
        reason: ProgressionReason,
        current: Double = 80.0,
        next: Double = 82.5,
        reps: Int = 8,
    ) = ProgressionSuggestion(
        exerciseId = id, action = action, reason = reason, currentWeightKg = current,
        suggestedWeightKg = next, targetReps = reps, incrementKg = 2.5,
    )

    private fun exerciseInsight(id: String, status: TrendStatus) = ExerciseInsight(
        exerciseId = id, latestE1rmKg = 100.0, bestE1rmKg = 105.0, trend = trend(status),
        deload = null, progression = null,
    )

    private fun volume(vararg findings: Insight) = TrainingInsights(
        asOf = asOf,
        exercises = listOf(exerciseInsight("bench", TrendStatus.PLATEAU)),
        volume = VolumeReport(asOf.minusDays(27), asOf, 4, emptyList(), emptyList()),
        findings = findings.sortedBy { it.code.ordinal },
    )

    private fun lowChest() = Insight.Volume(
        MuscleVolume(MuscleGroup.CHEST, 12.0, 3.0, VolumeTarget(10.0, 20.0), VolumeStatus.LOW),
    )

    @Test
    fun dashboardShowsEmptyStateWithoutAnyAnalyzableExercise() {
        val insights = TrainingInsights(
            asOf = asOf,
            exercises = listOf(exerciseInsight("bench", TrendStatus.INSUFFICIENT_DATA)),
            volume = VolumeReport(asOf.minusDays(27), asOf, 1, emptyList(), emptyList()),
            findings = listOf(lowChest()),
        )

        val ui = insights.toDashboardInsights(names, Locale.US)

        assertTrue(ui.cards.isEmpty())
        assertEquals(InsightsEmptyReason.NOT_ENOUGH_DATA, ui.emptyReason)
    }

    @Test
    fun dashboardShowsEmptyStateWithoutHistoryAtAll() {
        val insights = TrainingInsights(
            asOf = asOf,
            exercises = emptyList(),
            volume = VolumeReport(asOf.minusDays(27), asOf, 4, emptyList(), emptyList()),
            findings = emptyList(),
        )

        assertEquals(InsightsEmptyReason.NOT_ENOUGH_DATA, insights.toDashboardInsights(names, Locale.US).emptyReason)
    }

    @Test
    fun dashboardSaysNothingNotableWhenOnlyProgressAndHoldRemain() {
        val insights = volume(
            Insight.Trend("bench", trend(TrendStatus.PROGRESSING, slope = 1.0)),
            Insight.Progression(suggestion("bench", ProgressionAction.HOLD, ProgressionReason.BELOW_MIN_REPS)),
        )

        val ui = insights.toDashboardInsights(names, Locale.US)

        assertTrue(ui.cards.isEmpty())
        assertEquals(InsightsEmptyReason.NOTHING_NOTABLE, ui.emptyReason)
    }

    @Test
    fun dashboardKeepsPriorityOrderOneCardPerSubjectAndMaxThree() {
        val insights = volume(
            Insight.Trend("bench", trend(TrendStatus.PLATEAU)),
            Insight.Progression(suggestion("bench", ProgressionAction.DELOAD, ProgressionReason.REPEATED_PLATEAU, 100.0, 90.0, 5)),
            Insight.Progression(suggestion("squat", ProgressionAction.INCREASE_WEIGHT, ProgressionReason.ALL_SETS_AT_TOP)),
            Insight.Progression(suggestion("row", ProgressionAction.INCREASE_REPS, ProgressionReason.REPS_BELOW_TOP)),
            Insight.Progression(suggestion("squat", ProgressionAction.HOLD, ProgressionReason.RPE_TOO_HIGH)),
            lowChest(),
        )

        val ui = insights.toDashboardInsights(names, Locale.US)

        // Bench: Deload statt Plateau (gleiche Übung, höhere Priorität); Squat: nur die Steigerung, nicht
        // zusätzlich "Gewicht halten"; Row (Wiederholungen) fällt hinter die drei wichtigsten.
        assertEquals(
            listOf(InsightCode.DELOAD, InsightCode.LOW_VOLUME, InsightCode.INCREASE_WEIGHT),
            ui.cards.map { it.code },
        )
        assertEquals(InsightText.Plain("Bench Press"), ui.cards.first().subject)
        assertNull(ui.emptyReason)
    }

    @Test
    fun cardIdsAreUnique() {
        val insights = volume(
            Insight.Trend("bench", trend(TrendStatus.PLATEAU)),
            Insight.Progression(suggestion("bench", ProgressionAction.HOLD, ProgressionReason.BELOW_MIN_REPS)),
            Insight.Trend("squat", trend(TrendStatus.PLATEAU)),
        )

        val ids = insights.toExerciseInsights("bench", Locale.US).cards.map { it.id } +
            insights.toExerciseInsights("squat", Locale.US).cards.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun plateauCardCarriesDaysSessionsAndFormattedE1rm() {
        val insights = volume(Insight.Trend("bench", trend(TrendStatus.PLATEAU, latest = 102.5, days = 25, sessions = 4)))

        val card = insights.toDashboardInsights(names, Locale.US).cards.single()

        assertEquals(InsightTone.ATTENTION, card.tone)
        assertEquals(R.string.insights_title_plateau, card.title.id)
        assertEquals(R.string.insights_message_plateau, card.message.id)
        assertEquals(listOf<Any>(25, 4, "102.5"), card.message.args)
    }

    @Test
    fun weightsUseTheLocaleAndAtMostOneDecimal() {
        val insights = volume(
            Insight.Progression(
                suggestion("bench", ProgressionAction.INCREASE_WEIGHT, ProgressionReason.ALL_SETS_AT_TOP, 80.0, 82.5, 6),
            ),
        )

        val english = insights.toDashboardInsights(names, Locale.US).cards.single()
        val german = insights.toDashboardInsights(names, Locale.GERMANY).cards.single()

        assertEquals(InsightTone.POSITIVE, english.tone)
        assertEquals(R.string.insights_message_increase_weight, english.message.id)
        assertEquals(listOf<Any>("82.5", "80", 6), english.message.args)
        assertEquals(listOf<Any>("82,5", "80", 6), german.message.args)
    }

    @Test
    fun deloadMessageDependsOnTheReason() {
        val plateau = volume(
            Insight.Progression(suggestion("bench", ProgressionAction.DELOAD, ProgressionReason.REPEATED_PLATEAU, 100.0, 90.0, 5)),
        ).toDashboardInsights(names, Locale.US).cards.single()
        val decline = volume(
            Insight.Progression(suggestion("bench", ProgressionAction.DELOAD, ProgressionReason.SUSTAINED_DECLINE, 100.0, 90.0, 5)),
        ).toDashboardInsights(names, Locale.US).cards.single()

        assertEquals(R.string.insights_message_deload_plateau, plateau.message.id)
        assertEquals(R.string.insights_message_deload_decline, decline.message.id)
        assertEquals(listOf<Any>("90", "100", 5), plateau.message.args)
    }

    @Test
    fun declineCardShowsLatestAndBestE1rm() {
        val card = volume(Insight.Trend("bench", trend(TrendStatus.DECLINING, latest = 92.0, best = 105.0)))
            .toDashboardInsights(names, Locale.US).cards.single()

        assertEquals(InsightCode.DECLINE, card.code)
        assertEquals(listOf<Any>("92", "105"), card.message.args)
    }

    @Test
    fun missingE1rmIsShownAsDashInsteadOfCrashing() {
        val card = volume(Insight.Trend("bench", trend(TrendStatus.PLATEAU, latest = null)))
            .toDashboardInsights(names, Locale.US).cards.single()

        assertEquals("–", card.message.args[2])
    }

    @Test
    fun holdMessageDependsOnTheReason() {
        fun hold(reason: ProgressionReason) = volume(
            Insight.Progression(suggestion("bench", ProgressionAction.HOLD, reason)),
        ).toExerciseInsights("bench", Locale.US).cards.single()

        assertEquals(R.string.insights_message_hold_rpe, hold(ProgressionReason.RPE_TOO_HIGH).message.id)
        assertEquals(R.string.insights_message_hold_sets_missing, hold(ProgressionReason.SETS_MISSING).message.id)
        assertEquals(R.string.insights_message_hold_reps, hold(ProgressionReason.BELOW_MIN_REPS).message.id)
        assertEquals(InsightTone.NEUTRAL, hold(ProgressionReason.BELOW_MIN_REPS).tone)
    }

    @Test
    fun unknownExerciseFallsBackToGenericName() {
        val card = volume(Insight.Trend("gone", trend(TrendStatus.PLATEAU)))
            .toDashboardInsights(names, Locale.US).cards.single()

        assertEquals(InsightText.Res(R.string.insights_unknown_exercise), card.subject)
    }

    @Test
    fun volumeCardNamesTheMuscleAndTheTargetRange() {
        val insights = volume(Insight.Trend("bench", trend(TrendStatus.PROGRESSING, slope = 1.0)), lowChest())

        val card = insights.toDashboardInsights(names, Locale.US).cards.single()

        assertEquals(InsightCode.LOW_VOLUME, card.code)
        assertEquals(R.string.insights_title_low_volume, card.title.id)
        assertEquals(InsightText.Res(app.gympulse.core.data.R.string.muscle_chest), card.subject)
        assertEquals(listOf<Any>("3", "10", "20"), card.message.args)
    }

    @Test
    fun imbalanceCardPicksTheMessageOfTheDominantSide() {
        fun imbalance(pair: BalancePair, side: BalanceSide) = volume(
            Insight.Imbalance(MuscleImbalance(pair, 12.0, 6.0, side, 2.0)),
        ).toDashboardInsights(names, Locale.US).cards.single()

        val push = imbalance(BalancePair.PUSH_PULL, BalanceSide.A)
        val pull = imbalance(BalancePair.PUSH_PULL, BalanceSide.B)
        val quads = imbalance(BalancePair.QUADS_HAMSTRINGS, BalanceSide.A)
        val hamstrings = imbalance(BalancePair.QUADS_HAMSTRINGS, BalanceSide.B)

        assertEquals(R.string.insights_message_push_dominant, push.message.id)
        assertEquals(R.string.insights_message_pull_dominant, pull.message.id)
        assertEquals(R.string.insights_message_quads_dominant, quads.message.id)
        assertEquals(R.string.insights_message_hamstrings_dominant, hamstrings.message.id)
        assertEquals(InsightText.Res(R.string.insights_subject_push_pull), push.subject)
        // Die dominante Seite steht zuerst in den Argumenten, unabhängig davon, ob sie A oder B ist.
        assertEquals(listOf<Any>("6", "12"), pull.message.args)
        assertEquals(listOf<Any>("12", "6"), push.message.args)
    }

    @Test
    fun exerciseDetailShowsTrendAndSuggestionWithoutSubject() {
        val insights = volume(
            Insight.Trend("bench", trend(TrendStatus.PROGRESSING, slope = 1.3)),
            Insight.Progression(suggestion("bench", ProgressionAction.INCREASE_WEIGHT, ProgressionReason.ALL_SETS_AT_TOP)),
            Insight.Trend("squat", trend(TrendStatus.PLATEAU)),
            lowChest(),
        )

        val ui = insights.toExerciseInsights("bench", Locale.US)

        assertEquals(listOf(InsightCode.INCREASE_WEIGHT, InsightCode.PROGRESS), ui.cards.map { it.code })
        assertTrue(ui.cards.all { it.subject == null })
        assertEquals(listOf<Any>("100", "1.3"), ui.cards.last().message.args)
    }

    @Test
    fun exerciseDetailShowsEmptyStateWithoutFindings() {
        val ui = volume(Insight.Trend("squat", trend(TrendStatus.PLATEAU))).toExerciseInsights("bench", Locale.US)

        assertTrue(ui.cards.isEmpty())
        assertEquals(InsightsEmptyReason.NOT_ENOUGH_DATA, ui.emptyReason)
    }

    @Test
    fun loadingStateHasNeitherCardsNorReason() {
        assertTrue(InsightsUi.Loading.isEmpty)
        assertNull(InsightsUi.Loading.emptyReason)
    }
}
