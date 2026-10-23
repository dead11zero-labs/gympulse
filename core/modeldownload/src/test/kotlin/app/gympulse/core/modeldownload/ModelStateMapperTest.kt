package app.gympulse.core.modeldownload

import androidx.work.Data
import androidx.work.WorkInfo
import app.gympulse.core.data.ai.ModelDownloadFailure
import app.gympulse.core.data.ai.ModelDownloadState
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelStateMapperTest {
    private val total = 1_000L

    @Test
    fun `present model is ready regardless of work state`() {
        val state = map(snapshot(WorkInfo.State.FAILED), modelPresent = true)
        assertEquals(ModelDownloadState.Ready(total), state)
    }

    @Test
    fun `no work and no model is not downloaded`() {
        assertEquals(ModelDownloadState.NotDownloaded, map(null))
    }

    @Test
    fun `running work reports progress from worker data`() {
        val progress = Data.Builder()
            .putLong(ModelDownloadWorker.KEY_DOWNLOADED, 250L)
            .putLong(ModelDownloadWorker.KEY_TOTAL, total)
            .build()

        val state = map(snapshot(WorkInfo.State.RUNNING, progress = progress))

        assertEquals(ModelDownloadState.Downloading(0.25f, 250L, total, waitingForNetwork = false), state)
    }

    @Test
    fun `enqueued work waits for wifi and shows partial progress`() {
        val state = map(snapshot(WorkInfo.State.ENQUEUED), partialBytes = 500L)

        assertEquals(ModelDownloadState.Downloading(0.5f, 500L, total, waitingForNetwork = true), state)
    }

    @Test
    fun `failed work maps reason from output`() {
        val output = Data.Builder()
            .putString(ModelDownloadWorker.KEY_FAILURE, ModelDownloadFailure.HASH_MISMATCH.name)
            .build()

        val state = map(snapshot(WorkInfo.State.FAILED, output = output))

        assertEquals(ModelDownloadState.Failed(ModelDownloadFailure.HASH_MISMATCH), state)
    }

    @Test
    fun `failed work without reason is unknown`() {
        val state = map(snapshot(WorkInfo.State.FAILED))
        assertEquals(ModelDownloadState.Failed(ModelDownloadFailure.UNKNOWN), state)
    }

    @Test
    fun `succeeded work without file and cancelled work are not downloaded`() {
        assertEquals(ModelDownloadState.NotDownloaded, map(snapshot(WorkInfo.State.SUCCEEDED)))
        assertEquals(ModelDownloadState.NotDownloaded, map(snapshot(WorkInfo.State.CANCELLED)))
    }

    private fun map(
        work: WorkSnapshot?,
        modelPresent: Boolean = false,
        partialBytes: Long = 0L,
    ) = ModelStateMapper.map(work, modelPresent, partialBytes, total)

    private fun snapshot(
        state: WorkInfo.State,
        progress: Data = Data.EMPTY,
        output: Data = Data.EMPTY,
    ) = WorkSnapshot(state, progress, output)
}
