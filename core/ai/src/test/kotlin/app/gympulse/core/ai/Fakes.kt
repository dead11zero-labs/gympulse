package app.gympulse.core.ai

import app.gympulse.core.insights.narration.AppLanguage
import app.gympulse.core.insights.narration.InsightNarrator
import app.gympulse.core.insights.narration.InsightSummary
import app.gympulse.core.insights.narration.SummaryExercise
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

internal class FakeLlmEngine(
    override val backend: LlmBackend,
    private val output: suspend kotlinx.coroutines.flow.FlowCollector<String>.() -> Unit,
) : LlmEngine {
    var closed = false
        private set
    val prompts = mutableListOf<Pair<String, String>>()

    override fun generate(system: String, user: String): Flow<String> = flow {
        check(!closed) { "engine closed" }
        prompts += system to user
        output()
    }

    override fun close() {
        closed = true
    }
}

/** Liefert pro Backend eine Engine oder wirft die hinterlegte Exception. */
internal class FakeEngineFactory(
    private val failing: Map<LlmBackend, Throwable> = emptyMap(),
    private val output: suspend kotlinx.coroutines.flow.FlowCollector<String>.() -> Unit = {
        emit("• Bench")
        emit("press: +2.5 kg")
    },
) : LlmEngineFactory {
    val created = mutableListOf<FakeLlmEngine>()
    val attempts = mutableListOf<LlmBackend>()

    override fun create(model: ModelFile, backend: LlmBackend): LlmEngine {
        attempts += backend
        failing[backend]?.let { throw it }
        return FakeLlmEngine(backend, output).also { created += it }
    }
}

internal class FakeModelLocator(var model: ModelFile? = MODEL) : ModelLocator {
    override fun locate(): ModelFile? = model

    companion object {
        val MODEL = ModelFile("/data/models/gemma-4-E2B-it-gpu.litertlm", 2_010_000_000L, 1L)
    }
}

internal class FakeDeviceMemory(
    override var totalBytes: Long = 12L * 1024 * 1024 * 1024,
    override var availableBytes: Long = 6L * 1024 * 1024 * 1024,
    override var isLow: Boolean = false,
) : DeviceMemory

internal object FakeTemplate : InsightNarrator {
    const val TEXT = "• template"
    override fun narrate(summary: InsightSummary): Flow<String> = flowOf(TEXT)
}

internal fun summary(language: AppLanguage = AppLanguage.DE) = InsightSummary(
    language = language,
    asOf = LocalDate.of(2026, 10, 20),
    weeks = 4,
    exercises = listOf(SummaryExercise("Bankdrücken", 100.0, "steigend", "+2,5 kg")),
    muscles = emptyList(),
    findings = emptyList(),
    text = "Bankdrücken: e1RM 100 kg, steigend",
)

internal suspend fun kotlinx.coroutines.flow.FlowCollector<String>.slowly(vararg parts: String) {
    for (p in parts) {
        delay(10)
        emit(p)
    }
}
