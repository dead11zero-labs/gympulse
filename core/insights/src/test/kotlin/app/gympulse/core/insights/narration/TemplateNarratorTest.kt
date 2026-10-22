package app.gympulse.core.insights.narration

import app.gympulse.core.insights.InsightsEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateNarratorTest {

    private val builder = InsightSummaryBuilder()
    private val narrator = TemplateNarrator()
    private val insights = NarrationFixtures.insights()

    @Test
    fun englishTextHasAtMostFivePointsAndPrioritizesDeload() {
        val summary = builder.build(insights, NarrationFixtures.names, AppLanguage.EN)
        val text = narrator.compose(summary).joinToString("\n") { "• $it" }
        val lines = text.lines()
        assertTrue(lines.size in 1..5)
        assertTrue(lines.all { it.startsWith("• ") })
        assertTrue(lines.first().contains("Back Squat"))
        assertTrue(lines.first().contains("90 kg"))
    }

    @Test
    fun englishTextContainsExerciseAdviceWhenNotCapped() {
        val summary = builder.build(insights, NarrationFixtures.names, AppLanguage.EN)
        val text = TemplateNarrator(maxPoints = 10).compose(summary).joinToString("\n")
        assertTrue(text, text.contains("Bench Press: aim for 11 reps at 82.5 kg."))
    }

    @Test
    fun germanTextUsesGermanBlocks() {
        val summary = builder.build(insights, NarrationFixtures.names, AppLanguage.DE)
        val text = TemplateNarrator(maxPoints = 10).compose(summary).joinToString("\n") { "• $it" }
        assertTrue(text.contains("Deload"))
        assertTrue(text.contains("Bench Press: peile 11 Wiederholungen bei 82,5 kg an."))
        assertFalse(text.contains("{0}"))
        assertFalse(text.contains("{1}"))
    }

    @Test
    fun streamsOneChunkPerPointAndConcatenatesToFullText() = runTest {
        val summary = builder.build(insights, NarrationFixtures.names, AppLanguage.EN)
        val chunks = narrator.narrate(summary).toList()
        assertEquals(narrator.compose(summary).size, chunks.size)
        assertEquals(chunks.joinToString(""), narrator.narrateToString(summary))
    }

    @Test
    fun deterministic() = runTest {
        val a = narrator.narrateToString(builder.build(insights, NarrationFixtures.names, AppLanguage.EN))
        val b = TemplateNarrator().narrateToString(
            InsightSummaryBuilder().build(NarrationFixtures.insights(), NarrationFixtures.names, AppLanguage.EN),
        )
        assertEquals(a, b)
    }

    @Test
    fun noRawDataInOutput() = runTest {
        for (language in AppLanguage.entries) {
            val text = narrator.narrateToString(builder.build(insights, NarrationFixtures.names, language))
            assertFalse(text.contains(NarrationFixtures.BENCH_ID))
            assertFalse(text.contains("session-secret"))
            assertFalse(text.contains("41.5") || text.contains("41,5"))
        }
    }

    @Test
    fun emptySummaryGivesNoDataHint() = runTest {
        val empty = InsightsEngine().analyze(emptyList(), NarrationFixtures.asOf)
        val en = narrator.narrateToString(builder.build(empty, emptyMap(), AppLanguage.EN))
        val de = narrator.narrateToString(builder.build(empty, emptyMap(), AppLanguage.DE))
        assertTrue(en.contains("Not enough training data"))
        assertTrue(de.contains("Noch zu wenig Trainingsdaten"))
    }

    @Test
    fun customTextsAreUsed() = runTest {
        val custom = TemplateNarrator(maxPoints = 1, textsFor = { NarrationTexts { key, _ -> "T:${key.name}" } })
        val text = custom.narrateToString(builder.build(insights, NarrationFixtures.names, AppLanguage.EN))
        assertEquals("• T:DELOAD", text)
    }

    @Test
    fun everyKeyHasTextInBothLanguages() {
        for (language in AppLanguage.entries) {
            val texts = BuiltInNarrationTexts(language)
            for (key in NarrationKey.entries) {
                val result = texts.text(key, listOf("A", "B", "C"))
                assertTrue("$language $key", result.isNotBlank())
                assertFalse("$language $key", result.contains("{"))
            }
        }
    }

    @Test
    fun languageFromLocale() {
        assertEquals(AppLanguage.DE, AppLanguage.fromLocale(java.util.Locale.GERMANY))
        assertEquals(AppLanguage.EN, AppLanguage.fromLocale(java.util.Locale.FRANCE))
    }
}
