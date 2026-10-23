package app.gympulse.core.modeldownload

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.gympulse.core.data.ai.ModelDownloadFailure
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Lädt das Modell als Foreground-Job mit Fortschritts-Notification. Constraints (nur ungemessenes
 * Netz, Speicher nicht knapp) setzt [WorkManagerModelDownloadManager]. Fällt das WLAN weg, stoppt
 * WorkManager den Job und plant ihn neu; die Teildatei bleibt für die Wiederaufnahme liegen.
 *
 * Abhängigkeiten kommen über einen Hilt-EntryPoint, damit die App keine eigene WorkerFactory braucht.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun modelStorage(): ModelStorage
        fun modelDownloader(): ModelDownloader
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val storage = deps.modelStorage()
        ModelDownloadNotifications.ensureChannel(applicationContext)
        updateForeground(storage.partialBytes(), storage.spec.sizeBytes)

        var lastPercent = -1
        val outcome = deps.modelDownloader().download(storage) { downloaded, total ->
            val percent = ((downloaded * 100) / total).toInt()
            if (percent != lastPercent) {
                lastPercent = percent
                setProgress(workDataOf(KEY_DOWNLOADED to downloaded, KEY_TOTAL to total))
                updateForeground(downloaded, total)
            }
        }
        return when (outcome) {
            is DownloadOutcome.Success -> Result.success()
            is DownloadOutcome.Failure ->
                if (outcome.retryable && runAttemptCount < MAX_ATTEMPTS) {
                    Result.retry()
                } else {
                    Result.failure(workDataOf(KEY_FAILURE to outcome.reason.name))
                }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0L, 0L)

    private suspend fun updateForeground(downloaded: Long, total: Long) {
        try {
            setForeground(foregroundInfo(downloaded, total))
        } catch (_: IllegalStateException) {
            // Android 12+: Start aus dem Hintergrund nicht erlaubt (ForegroundServiceStartNotAllowedException).
            // Der Job läuft dann ohne Notification weiter; bricht das System ihn ab, setzt er später fort.
        }
    }

    private fun foregroundInfo(downloaded: Long, total: Long): ForegroundInfo {
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification = ModelDownloadNotifications.build(applicationContext, downloaded, total, cancel)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                ModelDownloadNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(ModelDownloadNotifications.NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_FAILURE = "failure"

        /** Wiederholungen bei Netz- oder Serverfehlern, bevor der Zustand auf Failed wechselt. */
        const val MAX_ATTEMPTS = 10

        internal fun failureFrom(name: String?): ModelDownloadFailure =
            ModelDownloadFailure.entries.firstOrNull { it.name == name } ?: ModelDownloadFailure.UNKNOWN
    }
}
