package app.gympulse.core.ai.debug

import app.gympulse.core.ai.FakeEngineFactory
import app.gympulse.core.ai.FakeModelLocator
import app.gympulse.core.ai.LlmBackend
import app.gympulse.core.insights.narration.AppLanguage
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationBenchmarkTest {

    @Test
    fun `measures chunks, rate and peak memory and closes the engine`() = runTest {
        var now = 0L
        var pss = 100_000L
        val factory = FakeEngineFactory(output = {
            repeat(11) {
                now += 100_000_000 // 100 ms pro Delta
                pss += 1_000
                emit("x")
            }
        })
        val benchmark = NarrationBenchmark(
            factory = factory,
            memory = { MemorySample(pss, 0, 0) },
            loadDispatcher = StandardTestDispatcher(testScheduler),
            nanoTime = { now },
        )

        val result = benchmark.run(FakeModelLocator.MODEL, LlmBackend.GPU, BenchmarkSamples.prompt(AppLanguage.DE))

        assertEquals(11, result.chunks)
        assertEquals(100L, result.timeToFirstTokenMs)
        assertEquals(1_000L, result.decodeMs)
        assertEquals(10.0, result.chunksPerSecond, 0.001)
        assertTrue(result.memoryPeak.totalPssKb > result.memoryLoaded.totalPssKb)
        assertTrue(factory.created.single().closed)
        assertTrue(result.promptTokensEstimate in 100..1000)
    }

    @Test
    fun `load failure is reported, not thrown`() = runTest {
        val factory = FakeEngineFactory(failing = mapOf(LlmBackend.GPU to IllegalStateException("no OpenCL")))
        val benchmark = NarrationBenchmark(factory, { MemorySample(0, 0, 0) }, StandardTestDispatcher(testScheduler))
        val result = benchmark.run(FakeModelLocator.MODEL, LlmBackend.GPU, BenchmarkSamples.prompt(AppLanguage.EN))
        assertNotNull(result.error)
        assertTrue(result.format().contains("no OpenCL"))
    }
}
