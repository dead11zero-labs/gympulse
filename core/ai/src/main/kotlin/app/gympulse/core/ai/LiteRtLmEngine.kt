package app.gympulse.core.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Sampling und Kontextlänge für den Coach: kurze, sachliche Antworten. */
data class LlmGenerationConfig(
    /** Prompt (Ziel < 1.000 Tokens) plus Antwort (max. 5 Stichpunkte). */
    val maxNumTokens: Int = 2048,
    val topK: Int = 40,
    val topP: Double = 0.95,
    val temperature: Double = 0.4,
)

/** Echte Engine über LiteRT-LM (`com.google.ai.edge.litertlm`). */
internal class LiteRtLmEngine(
    private val engine: Engine,
    override val backend: LlmBackend,
    private val config: LlmGenerationConfig,
) : LlmEngine {

    override fun generate(system: String, user: String): Flow<String> = flow {
        val conversation = engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(system),
                samplerConfig = SamplerConfig(topK = config.topK, topP = config.topP, temperature = config.temperature),
            ),
        )
        conversation.use {
            try {
                // Jede Message ist ein Delta der Antwort; toString() liefert ihren Text.
                it.sendMessageAsync(user).collect { message -> emit(message.toString()) }
            } catch (e: CancellationException) {
                // Abbruch durch den Aufrufer: native Generierung stoppen, bevor die Unterhaltung schließt.
                runCatching { it.cancelProcess() }
                throw e
            }
        }
    }.flowOn(Dispatchers.Default)

    override fun close() = engine.close()
}

class LiteRtLmEngineFactory internal constructor(
    private val cacheDir: File?,
    private val config: LlmGenerationConfig,
) : LlmEngineFactory {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.cacheDir, "litertlm"), LlmGenerationConfig())

    override fun create(model: ModelFile, backend: LlmBackend): LlmEngine {
        cacheDir?.mkdirs()
        val engine = Engine(
            EngineConfig(
                modelPath = model.path,
                backend = when (backend) {
                    LlmBackend.GPU -> Backend.GPU()
                    LlmBackend.CPU -> Backend.CPU()
                },
                maxNumTokens = config.maxNumTokens,
                // Beschleunigt das zweite Laden (kompilierte GPU-Kernel); darf vom System geleert werden.
                cacheDir = cacheDir?.absolutePath,
            ),
        )
        try {
            engine.initialize()
        } catch (t: Throwable) {
            runCatching { engine.close() }
            throw t
        }
        return LiteRtLmEngine(engine, backend, config)
    }
}
