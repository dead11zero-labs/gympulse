package app.gympulse.core.modeldownload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.gympulse.core.data.ai.ModelDownloadManager
import app.gympulse.core.data.ai.ModelDownloadState
import app.gympulse.core.data.ai.StartDownloadResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** [ModelDownloadManager] auf Basis eines eindeutigen WorkManager-Jobs. */
@Singleton
class WorkManagerModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: ModelStorage,
) : ModelDownloadManager {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** Wird nach Dateiänderungen (Löschen, Abbruch) erhöht, damit [state] neu berechnet wird. */
    private val fileChanges = MutableStateFlow(0)

    /** Job, dessen Endzustand (z. B. Failed) nach Löschen oder Abbruch nicht mehr angezeigt wird. */
    private val dismissedWorkId = MutableStateFlow<UUID?>(null)

    @Volatile
    private var latestWorkId: UUID? = null

    override val state: Flow<ModelDownloadState> =
        combine(
            workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME),
            fileChanges,
            dismissedWorkId,
        ) { infos, _, dismissed ->
            val info = infos.firstOrNull()
            latestWorkId = info?.id
            val snapshot = info?.takeIf { it.id != dismissed }?.let {
                WorkSnapshot(it.state, it.progress, it.outputData)
            }
            ModelStateMapper.map(
                work = snapshot,
                modelPresent = storage.modelFile() != null,
                partialBytes = storage.partialBytes(),
                totalBytes = storage.spec.sizeBytes,
            )
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    override val downloadSizeBytes: Long get() = storage.spec.sizeBytes

    override fun requiredFreeBytes(): Long = storage.requiredFreeBytes()

    override fun startDownload(): StartDownloadResult {
        if (!storage.spec.isConfigured) return StartDownloadResult.NotConfigured
        if (storage.modelFile() != null) return StartDownloadResult.AlreadyReady
        val required = storage.requiredFreeBytes()
        val available = storage.availableBytes()
        if (available < required) return StartDownloadResult.InsufficientStorage(required, available)

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    // Nur WLAN bzw. ungemessene Netze, nie mobile Daten.
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(WORK_NAME)
            .build()
        dismissedWorkId.value = null
        // KEEP: ein laufender oder wartender Job bleibt; ein beendeter wird ersetzt.
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        return StartDownloadResult.Enqueued
    }

    override fun cancelDownload() {
        workManager.cancelUniqueWork(WORK_NAME)
        storage.deletePartial()
        dismissCurrentWork()
    }

    override fun deleteModel() {
        workManager.cancelUniqueWork(WORK_NAME)
        storage.deleteAll()
        dismissCurrentWork()
    }

    private fun dismissCurrentWork() {
        dismissedWorkId.value = latestWorkId
        fileChanges.update { it + 1 }
    }

    companion object {
        const val WORK_NAME = "model_download"
        private const val BACKOFF_SECONDS = 30L
    }
}

/** Ausschnitt aus [WorkInfo], den [ModelStateMapper] braucht. */
internal data class WorkSnapshot(
    val state: WorkInfo.State,
    val progress: Data,
    val output: Data,
)

/** Leitet den sichtbaren Zustand aus Job-Status und Dateien ab. Rein, damit JVM-testbar. */
internal object ModelStateMapper {
    fun map(
        work: WorkSnapshot?,
        modelPresent: Boolean,
        partialBytes: Long,
        totalBytes: Long,
    ): ModelDownloadState {
        if (modelPresent) return ModelDownloadState.Ready(totalBytes)
        if (work == null) return ModelDownloadState.NotDownloaded
        return when (work.state) {
            WorkInfo.State.RUNNING -> {
                val total = work.progress.getLong(ModelDownloadWorker.KEY_TOTAL, totalBytes)
                val done = work.progress.getLong(ModelDownloadWorker.KEY_DOWNLOADED, partialBytes)
                downloading(done, total, waiting = false)
            }
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                downloading(partialBytes, totalBytes, waiting = true)
            WorkInfo.State.FAILED -> ModelDownloadState.Failed(
                ModelDownloadWorker.failureFrom(work.output.getString(ModelDownloadWorker.KEY_FAILURE)),
            )
            // Erfolgreich, aber Datei fehlt (gelöscht) oder abgebrochen: wieder Ausgangszustand.
            WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED -> ModelDownloadState.NotDownloaded
        }
    }

    private fun downloading(done: Long, total: Long, waiting: Boolean): ModelDownloadState.Downloading =
        ModelDownloadState.Downloading(
            progress = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null,
            downloadedBytes = done,
            totalBytes = total,
            waitingForNetwork = waiting,
        )
}
