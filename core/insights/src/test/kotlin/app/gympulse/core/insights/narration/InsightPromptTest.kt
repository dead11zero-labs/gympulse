package app.gympulse.core.insights.narration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightPromptTest {

    private val builder = InsightSummaryBuilder()

    @Test
    fun promptFollowsAppLanguage() {
        val insights = NarrationFixtures.insights()
        val en = InsightPrompt.from(builder.build(insights, NarrationFixtures.names, AppLanguage.EN))
        val de = InsightPrompt.from(builder.build(insights, NarrationFixtures.names, AppLanguage.DE))
        assertTrue(en.system.contains("Answer in English"))
        assertTrue(en.system.contains("at most 5"))
        assertTrue(en.system.contains("No medical"))
        assertTrue(de.system.contains("Antworte auf Deutsch"))
        assertTrue(de.system.contains("höchstens 5"))
        assertTrue(de.system.contains("Keine medizinischen"))
    }

    @Test
    fun fullTextContainsRulesAndSummary() {
        val summary = builder.build(NarrationFixtures.insights(), NarrationFixtures.names, AppLanguage.EN)
        val prompt = InsightPrompt.from(summary)
        assertEquals(summary.text, prompt.user)
        assertTrue(prompt.fullText.startsWith(prompt.system))
        assertTrue(prompt.fullText.endsWith(summary.text))
        assertTrue(prompt.estimatedTokens < 1000)
    }
}
