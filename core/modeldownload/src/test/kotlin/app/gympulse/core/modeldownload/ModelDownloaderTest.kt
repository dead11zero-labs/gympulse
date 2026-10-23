package app.gympulse.core.modeldownload

import app.gympulse.core.data.ai.ModelDownloadFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class ModelDownloaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val content: ByteArray = Random(42).nextBytes(300 * 1024)
    private val downloader = ModelDownloader(OkHttpClient(), bufferSize = 8 * 1024)
    private lateinit var dir: File

    @Before
    fun setUp() {
        server.start()
        dir = tmp.newFolder("models")
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `fresh download verifies hash and renames atomically`() = runBlocking {
        server.enqueue(ok(content))
        val storage = storage()
        val progress = mutableListOf<Long>()

        val outcome = downloader.download(storage) { done, _ -> progress += done }

        assertTrue(outcome is DownloadOutcome.Success)
        assertArrayEquals(content, storage.targetFile.readBytes())
        assertFalse(storage.partialFile.exists())
        assertEquals(storage.targetFile, storage.modelFile())
        assertEquals(content.size.toLong(), progress.last())
        assertNull(server.takeRequest().headers["Range"])
    }

    @Test
    fun `resumes partial download with range request`() = runBlocking {
        val offset = 100 * 1024
        val storage = storage()
        storage.partialFile.writeBytes(content.copyOfRange(0, offset))
        server.enqueue(
            MockResponse.Builder()
                .code(206)
                .addHeader("Content-Range", "bytes $offset-${content.size - 1}/${content.size}")
                .body(Buffer().write(content, offset, content.size - offset))
                .build(),
        )

        val outcome = downloader.download(storage)

        assertTrue(outcome is DownloadOutcome.Success)
        assertEquals("bytes=$offset-", server.takeRequest().headers["Range"])
        assertArrayEquals(content, storage.targetFile.readBytes())
    }

    @Test
    fun `restarts from zero when server ignores range`() = runBlocking {
        val storage = storage()
        storage.partialFile.writeBytes(ByteArray(50 * 1024) { 7 })
        server.enqueue(ok(content))

        val outcome = downloader.download(storage)

        assertTrue(outcome is DownloadOutcome.Success)
        assertArrayEquals(content, storage.targetFile.readBytes())
    }

    @Test
    fun `hash mismatch deletes partial file and keeps no model`() = runBlocking {
        server.enqueue(ok(content))
        val storage = storage(sha256 = "0".repeat(64))

        val outcome = downloader.download(storage)

        assertEquals(ModelDownloadFailure.HASH_MISMATCH, (outcome as DownloadOutcome.Failure).reason)
        assertFalse(outcome.retryable)
        assertFalse(storage.targetFile.exists())
        assertFalse(storage.partialFile.exists())
        assertNull(storage.modelFile())
    }

    @Test
    fun `insufficient storage fails before any request`() = runBlocking {
        val storage = storage(freeBytes = 1_000L)

        val outcome = downloader.download(storage)

        outcome as DownloadOutcome.Failure
        assertEquals(ModelDownloadFailure.INSUFFICIENT_STORAGE, outcome.reason)
        assertEquals(content.size + RESERVE, outcome.requiredBytes)
        assertEquals(1_000L, outcome.availableBytes)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `storage check only counts the missing part when resuming`() {
        val storage = storage()
        storage.partialFile.writeBytes(ByteArray(100 * 1024))

        assertEquals(content.size - 100L * 1024 + RESERVE, storage.requiredFreeBytes())
    }

    @Test
    fun `cancellation stops the download and keeps the partial file for resume`() = runBlocking {
        server.enqueue(
            ok(content).newBuilder()
                .throttleBody(8 * 1024, 20, TimeUnit.MILLISECONDS)
                .build(),
        )
        val storage = storage()

        val job = async {
            downloader.download(storage) { done, _ ->
                if (done >= 32 * 1024) this@async.cancel()
            }
        }
        try {
            withTimeout(10_000) { job.await() }
            fail("expected cancellation")
        } catch (_: CancellationException) {
            // erwartet
        }

        assertTrue(job.isCancelled)
        assertFalse(storage.targetFile.exists())
        assertTrue(storage.partialFile.exists())
        assertTrue(storage.partialBytes() in 1 until content.size.toLong())
    }

    @Test
    fun `server error is retryable and keeps partial file`() = runBlocking {
        val storage = storage()
        storage.partialFile.writeBytes(content.copyOfRange(0, 1024))
        server.enqueue(MockResponse.Builder().code(503).build())

        val outcome = downloader.download(storage)

        outcome as DownloadOutcome.Failure
        assertEquals(ModelDownloadFailure.HTTP_ERROR, outcome.reason)
        assertTrue(outcome.retryable)
        assertEquals(1024L, storage.partialBytes())
    }

    @Test
    fun `not found is not retryable`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).build())

        val outcome = downloader.download(storage())

        outcome as DownloadOutcome.Failure
        assertEquals(ModelDownloadFailure.HTTP_ERROR, outcome.reason)
        assertFalse(outcome.retryable)
    }

    @Test
    fun `placeholder config never downloads`() = runBlocking {
        val spec = ModelConfig.CURRENT
        val storage = ModelStorage(dir, spec) { Long.MAX_VALUE }

        val outcome = downloader.download(storage)

        if (spec.isConfigured) return@runBlocking // Werte wurden eingetragen; dann greift dieser Test nicht.
        assertEquals(ModelDownloadFailure.NOT_CONFIGURED, (outcome as DownloadOutcome.Failure).reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `current config downloads over https from a pinned hugging face commit`() {
        val spec = ModelConfig.CURRENT
        assertTrue(spec.url.startsWith("https://huggingface.co/"))
        assertFalse(spec.url.contains("/resolve/main/"))
        assertTrue(spec.url.endsWith("/" + spec.fileName))
    }

    @Test
    fun `existing complete model is returned without request`() = runBlocking {
        val storage = storage()
        storage.targetFile.writeBytes(content)

        val outcome = downloader.download(storage)

        assertTrue(outcome is DownloadOutcome.Success)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `model locator ignores truncated files`() {
        val storage = storage()
        storage.targetFile.writeBytes(content.copyOfRange(0, 10))

        assertNull(storage.modelFile())
    }

    private fun storage(
        sha256: String = sha256(content),
        freeBytes: Long = Long.MAX_VALUE,
    ): ModelStorage = ModelStorage(
        directory = dir,
        spec = ModelSpec(
            id = "test",
            fileName = "model.litertlm",
            url = server.url("/model.litertlm").toString(),
            sha256 = sha256,
            sizeBytes = content.size.toLong(),
            reserveBytes = RESERVE,
        ),
        freeSpace = { freeBytes },
    )

    private fun ok(bytes: ByteArray): MockResponse =
        MockResponse.Builder().code(200).body(Buffer().write(bytes)).build()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val RESERVE = 4096L
    }
}
