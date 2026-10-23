package app.gympulse.core.ai

import app.gympulse.core.insights.narration.InsightNarrator
import app.gympulse.core.insights.narration.InsightSummary
import app.gympulse.core.insights.narration.NarrationChunk
import app.gympulse.core.insights.narration.NarrationSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GatedInsightNarratorTest {

    private var enabled = true
    private var unavailable: UnavailableReason? = null
    private var workoutActive = false
    private var probeCalls = 0
    private var llmBuilt = 0
    private var probeFailure: Exception? = null

    private class StaticNarrator(private vararg val parts: String) : InsightNarrator {
        override fun narrate(summary: InsightSummary): Flow<String> = flowOf(*parts)
    }

    private fun gated(
        llm: (onFallback: (Throwable?) -> Unit) -> InsightNarrator = { _ -> StaticNarrator("• ai") },
    ) = GatedInsightNarrator(
        isCoachEnabled = { enabled },
        workoutProbe = {
            probeCalls++
            probeFailure?.let { throw it }
            workoutActive
        },
        precheck = { unavailable },
        llmFactory = { onFallback ->
            llmBuilt++
            llm(onFallback)
        },
        template = FakeTemplate,
        decisionDispatcher = UnconfinedTestDispatcher(),
    )

    private suspend fun GatedInsightNarrator.run() = narrateWithSource(summary()).toList()

    private val template = listOf(NarrationChunk(FakeTemplate.TEXT, NarrationSource.TEMPLATE))

    @Test
    fun `uses the model when enabled, model ready and no workout`() = runTest {
        val chunks = gated { _ -> StaticNarrator("• a", "b") }.run()
        assertEquals(listOf(NarrationChunk("• a", NarrationSource.AI), NarrationChunk("b", NarrationSource.AI)), chunks)
    }

    @Test
    fun `uses the template and builds no model narrator while the feature is off`() = runTest {
        enabled = false
        assertEquals(template, gated().run())
        assertEquals(0, llmBuilt)
        assertEquals(0, probeCalls)
    }

    @Test
    fun `uses the template when the model is missing or the device cannot run it`() = runTest {
        for (reason in UnavailableReason.entries) {
            unavailable = reason
            assertEquals("$reason", template, gated().run())
        }
        assertEquals(0, llmBuilt)
    }

    @Test
    fun `never loads the model while a workout is active`() = runTest {
        workoutActive = true
        assertEquals(template, gated().run())
        assertEquals(0, llmBuilt)
    }

    @Test
    fun `a failing workout probe counts as active`() = runTest {
        probeFailure = IllegalStateException("db closed")
        assertEquals(template, gated().run())
        assertEquals(0, llmBuilt)
    }

    @Test
    fun `an empty summary does not wake the model`() = runTest {
        val empty = summary().copy(exercises = emptyList(), findings = emptyList())
        val chunks = gated().narrateWithSource(empty).toList()
        assertEquals(template, chunks)
        assertEquals(0, llmBuilt)
    }

    @Test
    fun `marks fallback text of the model narrator as template`() = runTest {
        val chunks = gated { onFallback ->
            object : InsightNarrator {
                override fun narrate(summary: InsightSummary): Flow<String> = flow {
                    onFallback(IllegalStateException("load failed"))
                    emit("• fallback")
                }
            }
        }.run()
        assertEquals(listOf(NarrationChunk("• fallback", NarrationSource.TEMPLATE)), chunks)
    }

    @Test
    fun `narrate returns only the text`() = runTest {
        val text = gated { _ -> StaticNarrator("x", "y") }.narrate(summary()).toList()
        assertEquals(listOf("x", "y"), text)
        assertEquals(1, llmBuilt)
    }
}
