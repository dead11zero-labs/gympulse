package app.gympulse.core.ai

import kotlinx.coroutines.flow.Flow

/** Rechen-Backend des Sprachmodells. Reihenfolge der Fallback-Kette: GPU, dann CPU. */
enum class LlmBackend { GPU, CPU }

/**
 * Ein geladenes Sprachmodell. Abstraktion über LiteRT-LM, damit Lebenszyklus, Fallback und Narrator
 * ohne native Bibliothek testbar sind (siehe `FakeLlmEngine` in den Tests).
 */
interface LlmEngine : AutoCloseable {
    val backend: LlmBackend

    /**
     * Startet eine frische Unterhaltung mit [system] als Systemanweisung und streamt die Antwort auf
     * [user] als Deltas. Fehler kommen als Exception aus dem Flow.
     */
    fun generate(system: String, user: String): Flow<String>

    /** Gibt den nativen Speicher frei. Danach ist die Engine unbrauchbar. */
    override fun close()
}

/** Lädt ein Modell. Darf blockieren (LiteRT-LM: bis ca. 10 s) und wirft bei Fehlern. */
fun interface LlmEngineFactory {
    fun create(model: ModelFile, backend: LlmBackend): LlmEngine
}
