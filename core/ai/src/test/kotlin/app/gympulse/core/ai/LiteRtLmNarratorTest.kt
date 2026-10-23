package app.gympulse.core.ai

import app.gympulse.core.insights.narration.AppLanguage
import app.gympulse.core.insights.narration.InsightPrompt
import app.gympulse.core.insights.narration.narrateToString
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LiteRtLmNarratorTest {

    private fun TestScope.manager(
        factory: LlmEngineFactory,
        locator: ModelLocator = FakeModelLocator(),
        memory: DeviceMemory = FakeDeviceMemory(),
        scope: CoroutineScope = backgroundScope,
    ) = LlmEngineManager(
        factory = factory,
        modelLocator = locator,
        deviceMemory = memory,
        scope = scope,
        loadDispatcher = StandardTestDispatcher(testScheduler),
    )

    private val fallbacks = mutableListOf<Throwable?>()

    private fun narrator(manager: LlmEngineManager) =
        LiteRtLmNarrator(manager, fallback = FakeTemplate, onFallback = { fallbacks += it })

    @Test
    fun `streams model deltas on GPU with the prompt of layer one`() = runTest {
        val factory = FakeEngineFactory()
        val parts = narrator(manager(factory)).narrate(summary()).toList()

        assertEquals(listOf("• Bench", "press: +2.5 kg"), parts)
        assertEquals(listOf(LlmBackend.GPU), factory.attempts)
        val expected = InsightPrompt.from(summary())
        assertEquals(listOf(expected.system to expected.user), factory.created.single().prompts)
        assertTrue(fallbacks.isEmpty())
    }

    @Test
    fun `uses the language of the summary in the system prompt`() = runTest {
        val factory = FakeEngineFactory()
        narrator(manager(factory)).narrateToString(summary(AppLanguage.EN))
        assertEquals(InsightPrompt.system(AppLanguage.EN), factory.created.single().prompts.single().first)
    }

    @Test
    fun `falls back to CPU when GPU fails to load`() = runTest {
        val factory = FakeEngineFactory(failing = mapOf(LlmBackend.GPU to IllegalStateException("no OpenCL")))
        val manager = manager(factory)
        val text = narrator(manager).narrateToString(summary())

        assertEquals("• Benchpress: +2.5 kg", text)
        assertEquals(listOf(LlmBackend.GPU, LlmBackend.CPU), factory.attempts)
        assertEquals(LlmBackend.CPU, manager.loadedBackend)
    }

    @Test
    fun `missing model uses the template narrator without loading`() = runTest {
        val factory = FakeEngineFactory()
        val text = narrator(manager(factory, locator = FakeModelLocator(model = null))).narrateToString(summary())

        assertEquals(FakeTemplate.TEXT, text)
        assertTrue(factory.attempts.isEmpty())
        assertEquals(UnavailableReason.MODEL_MISSING, (fallbacks.single() as ModelUnavailableException).reason)
    }

    @Test
    fun `too little RAM uses the template narrator`() = runTest {
        val factory = FakeEngineFactory()
        val memory = FakeDeviceMemory(totalBytes = 4L * 1024 * 1024 * 1024)
        val text = narrator(manager(factory, memory = memory)).narrateToString(summary())

        assertEquals(FakeTemplate.TEXT, text)
        assertTrue(factory.attempts.isEmpty())
        assertEquals(UnavailableReason.INSUFFICIENT_MEMORY, (fallbacks.single() as ModelUnavailableException).reason)
    }

    @Test
    fun `low memory signal uses the template narrator`() = runTest {
        val factory = FakeEngineFactory()
        val text = narrator(manager(factory, memory = FakeDeviceMemory(isLow = true))).narrateToString(summary())
        assertEquals(FakeTemplate.TEXT, text)
        assertTrue(factory.attempts.isEmpty())
    }

    @Test
    fun `corrupt model falls back and is not retried until the file changes`() = runTest {
        val corrupt = RuntimeException("LiteRtLmJniException: invalid flatbuffer")
        val factory = FakeEngineFactory(failing = mapOf(LlmBackend.GPU to corrupt, LlmBackend.CPU to corrupt))
        val locator = FakeModelLocator()
        val manager = manager(factory, locator = locator)
        val narrator = narrator(manager)

        assertEquals(FakeTemplate.TEXT, narrator.narrateToString(summary()))
        assertEquals(FakeTemplate.TEXT, narrator.narrateToString(summary()))
        assertEquals(listOf(LlmBackend.GPU, LlmBackend.CPU), factory.attempts)
        assertEquals(UnavailableReason.LOAD_FAILED, manager.precheck())
        val failure = fallbacks.first() as ModelUnavailableException
        assertEquals(UnavailableReason.LOAD_FAILED, failure.reason)
        assertEquals(corrupt, failure.cause)

        // Neue Datei (z. B. erneuter Download): wieder versuchen.
        locator.model = FakeModelLocator.MODEL.copy(lastModified = 2L)
        assertNull(manager.precheck())
        narrator.narrateToString(summary())
        assertEquals(4, factory.attempts.size)
    }

    @Test
    fun `native link errors count as load failure`() = runTest {
        val factory = FakeEngineFactory(
            failing = mapOf(
                LlmBackend.GPU to UnsatisfiedLinkError("liblitertlm_jni.so"),
                LlmBackend.CPU to OutOfMemoryError("native"),
            ),
        )
        assertEquals(FakeTemplate.TEXT, narrator(manager(factory)).narrateToString(summary()))
    }

    @Test
    fun `empty model answer uses the template narrator`() = runTest {
        val factory = FakeEngineFactory(output = { emit("") })
        assertEquals(FakeTemplate.TEXT, narrator(manager(factory)).narrateToString(summary()))
        assertEquals(listOf<Throwable?>(null), fallbacks)
    }

    @Test
    fun `timeout before the first token uses the template narrator`() = runTest {
        val factory = FakeEngineFactory(output = {
            delay(60.seconds)
            emit("too late")
        })
        assertEquals(FakeTemplate.TEXT, narrator(manager(factory)).narrateToString(summary()))
        assertEquals(1, fallbacks.size)
    }

    @Test
    fun `error after streamed text is reported instead of mixing in the template`() = runTest {
        val factory = FakeEngineFactory(output = {
            emit("• Bench")
            throw IllegalStateException("GPU lost")
        })
        val received = mutableListOf<String>()
        try {
            narrator(manager(factory)).narrate(summary()).collect { received += it }
            fail("expected NarrationInterruptedException")
        } catch (e: NarrationInterruptedException) {
            assertEquals("GPU lost", e.cause?.message)
        }
        assertEquals(listOf("• Bench"), received)
    }

    @Test
    fun `empty summary never loads the model`() = runTest {
        val factory = FakeEngineFactory()
        val empty = summary().copy(exercises = emptyList(), findings = emptyList())
        assertEquals(FakeTemplate.TEXT, narrator(manager(factory)).narrateToString(empty))
        assertTrue(factory.attempts.isEmpty())
    }

    @Test
    fun `engine is reused within the idle window and closed after it`() = runTest {
        val factory = FakeEngineFactory(output = { slowly("a", "b") })
        val manager = manager(factory)
        val narrator = narrator(manager)

        narrator.narrateToString(summary())
        advanceTimeBy(59.seconds)
        runCurrent()
        narrator.narrateToString(summary())
        assertEquals(1, factory.created.size)
        assertFalse(factory.created.single().closed)

        advanceTimeBy(61.seconds)
        runCurrent()
        assertTrue(factory.created.single().closed)
        assertNull(manager.loadedBackend)

        narrator.narrateToString(summary())
        assertEquals(2, factory.created.size)
    }

    @Test
    fun `releaseIfIdle closes the engine immediately`() = runTest {
        val factory = FakeEngineFactory()
        val manager = manager(factory)
        narrator(manager).narrateToString(summary())

        manager.releaseIfIdle()
        assertTrue(factory.created.single().closed)
        assertNull(manager.loadedBackend)
    }
}
