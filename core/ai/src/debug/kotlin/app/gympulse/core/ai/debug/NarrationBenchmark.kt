package app.gympulse.core.ai.debug

import app.gympulse.core.ai.LlmBackend
import app.gympulse.core.ai.LlmEngineFactory
import app.gympulse.core.ai.ModelFile
import app.gympulse.core.insights.narration.InsightPrompt
import app.gympulse.core.insights.narration.TokenEstimator
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Speicher des eigenen Prozesses in KB (PSS gesamt, nativer Heap, Grafik/GPU). */
data class MemorySample(val totalPssKb: Long, val nativeHeapKb: Long, val graphicsKb: Long)

fun interface MemoryProbe {
    fun sample(): MemorySample
}

/** Messwerte eines Durchlaufs auf einem Backend. Nur fester Beispiel-Prompt, keine Nutzerdaten. */
data class BenchmarkResult(
    val backend: LlmBackend,
    val loadMs: Long,
    val timeToFirstTokenMs: Long,
    val decodeMs: Long,
    /** Anzahl gestreamter Deltas; LiteRT-LM liefert in der Regel ein Token pro Delta. */
    val chunks: Int,
    /** Grobe Schätzung über [TokenEstimator], zum Gegenprüfen der Delta-Zählung. */
    val estimatedTokens: Int,
    val promptTokensEstimate: Int,
    val memoryBefore: MemorySample,
    val memoryLoaded: MemorySample,
    val memoryPeak: MemorySample,
    val output: String,
    val error: Throwable? = null,
) {
    /** Decode-Geschwindigkeit ab dem ersten Token. */
    val chunksPerSecond: Double
        get() = if (decodeMs <= 0 || chunks <= 1) 0.0 else (chunks - 1) * 1000.0 / decodeMs

    fun format(): String = buildString {
        appendLine("Backend: $backend")
        if (error != null) {
            appendLine("ERROR: ${error::class.simpleName}: ${error.message}")
            return@buildString
        }
        appendLine("Load: $loadMs ms")
        appendLine("TTFT: $timeToFirstTokenMs ms (prompt ≈ $promptTokensEstimate tokens)")
        appendLine("Decode: $chunks chunks in $decodeMs ms → %.1f tok/s".format(chunksPerSecond))
        appendLine("Output ≈ $estimatedTokens tokens (estimate)")
        appendLine("PSS before/loaded/peak: ${memoryBefore.totalPssKb / 1024} / ${memoryLoaded.totalPssKb / 1024} / ${memoryPeak.totalPssKb / 1024} MB")
        appendLine("Native heap peak: ${memoryPeak.nativeHeapKb / 1024} MB, graphics peak: ${memoryPeak.graphicsKb / 1024} MB")
        appendLine("---")
        append(output)
    }
}

/**
 * Debug-Messgerüst für das Zielgerät (S25 Ultra): lädt das Modell frisch auf einem Backend, misst
 * Ladezeit, Zeit bis zum ersten Token, Decode-Rate und Speicher und schließt die Engine wieder.
 */
class NarrationBenchmark(
    private val factory: LlmEngineFactory,
    private val memory: MemoryProbe,
    private val loadDispatcher: CoroutineDispatcher,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    suspend fun run(model: ModelFile, backend: LlmBackend, prompt: InsightPrompt): BenchmarkResult {
        val before = memory.sample()
        var loaded = before
        var peak = before
        fun track() {
            val s = memory.sample()
            if (s.totalPssKb > peak.totalPssKb) peak = s
        }
        val promptTokens = prompt.estimatedTokens
        val start = nanoTime()
        return try {
            val engine = withContext(loadDispatcher) { factory.create(model, backend) }
            val loadMs = (nanoTime() - start) / 1_000_000
            loaded = memory.sample()
            peak = loaded
            engine.use {
                val sendAt = nanoTime()
                var firstAt = 0L
                var chunks = 0
                val text = StringBuilder()
                it.generate(prompt.system, prompt.user).collect { delta ->
                    if (delta.isEmpty()) return@collect
                    if (chunks == 0) firstAt = nanoTime()
                    chunks++
                    text.append(delta)
                    if (chunks % SAMPLE_EVERY == 0) track()
                }
                val endAt = nanoTime()
                track()
                BenchmarkResult(
                    backend = backend,
                    loadMs = loadMs,
                    timeToFirstTokenMs = if (chunks == 0) 0 else (firstAt - sendAt) / 1_000_000,
                    decodeMs = if (chunks == 0) 0 else (endAt - firstAt) / 1_000_000,
                    chunks = chunks,
                    estimatedTokens = TokenEstimator.estimate(text.toString()),
                    promptTokensEstimate = promptTokens,
                    memoryBefore = before,
                    memoryLoaded = loaded,
                    memoryPeak = peak,
                    output = text.toString(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            BenchmarkResult(backend, 0, 0, 0, 0, 0, promptTokens, before, loaded, peak, "", error = e)
        }
    }

    private companion object {
        const val SAMPLE_EVERY = 16
    }
}
