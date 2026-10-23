package app.gympulse.core.ai

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Warum kein Modell genutzt werden kann. Der Narrator fällt dann auf Textbausteine zurück. */
enum class UnavailableReason {
    /** Keine (vollständige) Modelldatei in `filesDir/models/`. */
    MODEL_MISSING,

    /** Gerät hat insgesamt zu wenig RAM oder meldet gerade knappen Speicher. */
    INSUFFICIENT_MEMORY,

    /** Laden ist auf allen Backends gescheitert (Datei beschädigt, Treiber, OOM). */
    LOAD_FAILED,
}

class ModelUnavailableException(val reason: UnavailableReason, cause: Throwable? = null) :
    Exception("On-device model unavailable: $reason", cause)

data class LlmEngineConfig(
    /** Fallback-Kette der Backends. */
    val backends: List<LlmBackend> = listOf(LlmBackend.GPU, LlmBackend.CPU),
    /** Plan: unter 6 GB Gesamt-RAM kein LLM (Gemma 4 E2B braucht ca. 0,7 GB CPU-RAM plus GPU-Puffer). */
    val minTotalRamBytes: Long = 6L * 1024 * 1024 * 1024,
    /** Engine nach so langer Inaktivität schließen, um ca. 1–2 GB nativen Speicher freizugeben. */
    val idleTimeout: Duration = 60.seconds,
)

/**
 * Hält höchstens eine geladene [LlmEngine]: lädt sie erst bei Bedarf (GPU, bei Fehler CPU), führt
 * Generierungen nacheinander aus und schließt sie nach [LlmEngineConfig.idleTimeout] Inaktivität.
 * Ist das Laden einer Datei gescheitert, wird dieselbe Datei nicht erneut versucht.
 *
 * @param scope lebt so lange wie die App; darin läuft der Inaktivitäts-Timer.
 * @param loadDispatcher für das blockierende Laden (bis ca. 10 s).
 */
class LlmEngineManager(
    private val factory: LlmEngineFactory,
    private val modelLocator: ModelLocator,
    private val deviceMemory: DeviceMemory,
    private val scope: CoroutineScope,
    private val config: LlmEngineConfig = LlmEngineConfig(),
    private val loadDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutex = Mutex()

    /** LiteRT-LM verarbeitet pro Engine eine Unterhaltung zur Zeit. */
    private val generation = Semaphore(1)
    private var engine: LlmEngine? = null
    private var failedModel: ModelFile? = null
    private var idleJob: Job? = null

    /** Backend der aktuell geladenen Engine, `null` wenn keine geladen ist. */
    val loadedBackend: LlmBackend? get() = engine?.backend

    /**
     * Grobe Vorabprüfung ohne Laden, z. B. für die Einstellungen: `null`, wenn ein Versuch sinnvoll ist.
     */
    fun precheck(): UnavailableReason? {
        val model = modelLocator.locate() ?: return UnavailableReason.MODEL_MISSING
        if (deviceMemory.totalBytes in 1 until config.minTotalRamBytes) return UnavailableReason.INSUFFICIENT_MEMORY
        if (model == failedModel) return UnavailableReason.LOAD_FAILED
        return null
    }

    /**
     * Führt [block] mit der geladenen Engine aus und hält sie für die Dauer des Streams. Wirft
     * [ModelUnavailableException], wenn kein Modell geladen werden kann.
     */
    fun <T> withEngine(block: (LlmEngine) -> Flow<T>): Flow<T> = flow {
        generation.withPermit {
            val current = acquire()
            try {
                emitAll(block(current))
            } finally {
                withContext(NonCancellable) { scheduleIdleClose() }
            }
        }
    }

    /** Schließt die Engine sofort, sofern gerade keine Generierung läuft (z. B. bei `onTrimMemory`). */
    suspend fun releaseIfIdle() {
        if (!generation.tryAcquire()) return
        try {
            mutex.withLock { closeLocked() }
        } finally {
            generation.release()
        }
    }

    private suspend fun acquire(): LlmEngine = mutex.withLock {
        idleJob?.cancel()
        idleJob = null
        engine ?: load().also { engine = it }
    }

    private suspend fun load(): LlmEngine {
        val model = modelLocator.locate() ?: throw ModelUnavailableException(UnavailableReason.MODEL_MISSING)
        if (model == failedModel) throw ModelUnavailableException(UnavailableReason.LOAD_FAILED)
        val total = deviceMemory.totalBytes
        if ((total in 1 until config.minTotalRamBytes) || deviceMemory.isLow) {
            throw ModelUnavailableException(UnavailableReason.INSUFFICIENT_MEMORY)
        }
        var lastError: Throwable? = null
        for (backend in config.backends) {
            try {
                return withContext(loadDispatcher) { factory.create(model, backend) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // LiteRtLmJniException, IllegalStateException, fehlende OpenCL-Treiber …
                lastError = e
            } catch (e: LinkageError) {
                // UnsatisfiedLinkError: native Bibliothek fehlt für dieses ABI.
                lastError = e
            } catch (e: OutOfMemoryError) {
                lastError = e
            }
        }
        failedModel = model
        throw ModelUnavailableException(UnavailableReason.LOAD_FAILED, lastError)
    }

    private suspend fun scheduleIdleClose() = mutex.withLock {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(config.idleTimeout)
            mutex.withLock {
                idleJob = null
                closeLocked(cancelTimer = false)
            }
        }
    }

    private fun closeLocked(cancelTimer: Boolean = true) {
        if (cancelTimer) {
            idleJob?.cancel()
            idleJob = null
        }
        val current = engine ?: return
        engine = null
        runCatching { current.close() }
    }
}
