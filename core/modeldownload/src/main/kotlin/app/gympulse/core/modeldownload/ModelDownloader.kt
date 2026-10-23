package app.gympulse.core.modeldownload

import app.gympulse.core.data.ai.ModelDownloadFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Ergebnis eines Downloadversuchs. */
sealed interface DownloadOutcome {
    data class Success(val file: File) : DownloadOutcome

    /**
     * @property retryable true, wenn ein späterer Versuch Erfolg haben kann (Netz, 5xx). Die
     *   Teildatei bleibt dann für die Wiederaufnahme liegen.
     */
    data class Failure(
        val reason: ModelDownloadFailure,
        val retryable: Boolean = false,
        val message: String? = null,
        val requiredBytes: Long = 0L,
        val availableBytes: Long = 0L,
    ) : DownloadOutcome
}

/**
 * Lädt die Modelldatei per HTTP in die Teildatei von [ModelStorage], setzt einen abgebrochenen
 * Download per `Range`-Header fort, prüft Größe und SHA-256 und benennt die Datei erst danach
 * atomar um. Kein Android-Code, damit die Logik mit MockWebServer auf der JVM testbar ist.
 *
 * Abbruch: Wird die Coroutine abgebrochen, bricht der laufende HTTP-Aufruf ab und die
 * [kotlinx.coroutines.CancellationException] wird weitergereicht. Die Teildatei bleibt liegen.
 */
class ModelDownloader(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE_BYTES,
) {
    /**
     * @param onProgress Fortschritt in Bytes (bereits vorhanden + neu geladen, Gesamtgröße); wird
     *   gedrosselt aufgerufen, etwa alle 0,1 %.
     */
    suspend fun download(
        storage: ModelStorage,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): DownloadOutcome = withContext(ioDispatcher) {
        val spec = storage.spec
        if (!spec.isConfigured) {
            return@withContext DownloadOutcome.Failure(ModelDownloadFailure.NOT_CONFIGURED)
        }
        storage.modelFile()?.let { return@withContext DownloadOutcome.Success(it) }

        if (!storage.directory.isDirectory && !storage.directory.mkdirs()) {
            return@withContext DownloadOutcome.Failure(ModelDownloadFailure.UNKNOWN, message = "mkdirs failed")
        }
        if (storage.partialBytes() > spec.sizeBytes) storage.deletePartial()

        val required = storage.requiredFreeBytes()
        val available = storage.availableBytes()
        if (available < required) {
            return@withContext DownloadOutcome.Failure(
                reason = ModelDownloadFailure.INSUFFICIENT_STORAGE,
                requiredBytes = required,
                availableBytes = available,
            )
        }

        if (storage.partialBytes() < spec.sizeBytes) {
            fetch(storage, onProgress)?.let { return@withContext it }
        }
        verifyAndCommit(storage)
    }

    /** Lädt den Rest der Datei. Liefert null bei Erfolg, sonst den Fehler. */
    private suspend fun fetch(
        storage: ModelStorage,
        onProgress: suspend (Long, Long) -> Unit,
    ): DownloadOutcome.Failure? {
        val spec = storage.spec
        val offset = storage.partialBytes()
        val request = Request.Builder()
            .url(spec.url)
            // Ohne Kompression, damit Range-Offsets und Bytezahlen der Datei entsprechen.
            .header("Accept-Encoding", "identity")
            .apply { if (offset > 0) header("Range", "bytes=$offset-") }
            .build()

        val call = client.newCall(request)
        return try {
            cancelCallOnAbort(call) {
                call.execute().use { response ->
                    val append = when {
                        response.code == HTTP_PARTIAL && offset > 0 && contentRangeStart(response) == offset -> true
                        response.code == HTTP_OK -> false // Server ignoriert Range: von vorn beginnen.
                        response.code == HTTP_RANGE_NOT_SATISFIABLE || response.code == HTTP_PARTIAL -> {
                            // Teildatei passt nicht zum Server-Stand: verwerfen und neu versuchen.
                            storage.deletePartial()
                            return@use DownloadOutcome.Failure(
                                ModelDownloadFailure.HTTP_ERROR,
                                retryable = true,
                                message = "HTTP ${response.code} for range $offset",
                            )
                        }
                        else -> return@use DownloadOutcome.Failure(
                            ModelDownloadFailure.HTTP_ERROR,
                            retryable = response.code in RETRYABLE_HTTP_CODES,
                            message = "HTTP ${response.code}",
                        )
                    }
                    writeBody(response, storage, if (append) offset else 0L, onProgress)
                }
            }
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            DownloadOutcome.Failure(ModelDownloadFailure.NETWORK, retryable = true, message = e.message)
        }
    }

    /**
     * Ein blockierendes read() reagiert nicht auf Coroutine-Abbruch. Ein Wächter ruft deshalb bei
     * Abbruch [Call.cancel] auf; der Lesevorgang endet dann mit einer IOException.
     */
    private suspend fun <T> cancelCallOnAbort(call: Call, block: suspend () -> T): T = coroutineScope {
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            block()
        } finally {
            watcher.cancel()
        }
    }

    private suspend fun writeBody(
        response: Response,
        storage: ModelStorage,
        startAt: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ): DownloadOutcome.Failure? {
        val total = storage.spec.sizeBytes
        val reportStep = maxOf(total / PROGRESS_STEPS, bufferSize.toLong())
        var downloaded = startAt
        var lastReported = -reportStep
        FileOutputStream(storage.partialFile, startAt > 0).use { out ->
            response.body.byteStream().use { input ->
                val buffer = ByteArray(bufferSize)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (downloaded + read > total) {
                        out.close()
                        storage.deletePartial()
                        return DownloadOutcome.Failure(
                            ModelDownloadFailure.SIZE_MISMATCH,
                            message = "server sent more than $total bytes",
                        )
                    }
                    out.write(buffer, 0, read)
                    downloaded += read
                    if (downloaded - lastReported >= reportStep) {
                        lastReported = downloaded
                        onProgress(downloaded, total)
                    }
                }
            }
        }
        onProgress(downloaded, total)
        return null
    }

    private suspend fun verifyAndCommit(storage: ModelStorage): DownloadOutcome {
        val spec = storage.spec
        val part = storage.partialFile
        val length = part.length()
        if (length != spec.sizeBytes) {
            // Zu kurz: Verbindung sauber beendet, aber unvollständig. Beim nächsten Versuch fortsetzen.
            return DownloadOutcome.Failure(
                ModelDownloadFailure.SIZE_MISMATCH,
                retryable = length < spec.sizeBytes,
                message = "expected ${spec.sizeBytes} bytes, got $length",
            )
        }
        val actual = sha256(part)
        if (!actual.equals(spec.sha256, ignoreCase = true)) {
            storage.deletePartial()
            return DownloadOutcome.Failure(
                ModelDownloadFailure.HASH_MISMATCH,
                message = "expected ${spec.sha256}, got $actual",
            )
        }
        return try {
            Files.move(
                part.toPath(),
                storage.targetFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            storage.deleteStaleFiles()
            DownloadOutcome.Success(storage.targetFile)
        } catch (e: IOException) {
            DownloadOutcome.Failure(ModelDownloadFailure.UNKNOWN, message = e.message)
        }
    }

    private suspend fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(bufferSize)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun contentRangeStart(response: Response): Long? =
        response.header("Content-Range")
            ?.let { CONTENT_RANGE.find(it) }
            ?.groupValues?.get(1)
            ?.toLongOrNull()

    companion object {
        const val DEFAULT_BUFFER_SIZE_BYTES = 64 * 1024
        private const val PROGRESS_STEPS = 1000L
        private const val HTTP_OK = 200
        private const val HTTP_PARTIAL = 206
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private val RETRYABLE_HTTP_CODES = setOf(408, 425, 429, 500, 502, 503, 504)
        private val CONTENT_RANGE = Regex("""bytes\s+(\d+)-\d+/(\d+|\*)""")
    }
}
