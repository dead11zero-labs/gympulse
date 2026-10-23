package app.gympulse.core.modeldownload

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Fortschritts-Notification des Modell-Downloads. */
internal object ModelDownloadNotifications {
    const val CHANNEL_ID = "model_download"
    const val NOTIFICATION_ID = 1101

    fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.core_modeldownload_channel))
            .setDescription(context.getString(R.string.core_modeldownload_channel_description))
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /**
     * @param downloadedBytes bereits geladene Bytes; bei [totalBytes] 0 zeigt die Notification
     *   einen unbestimmten Fortschritt.
     */
    fun build(
        context: Context,
        downloadedBytes: Long,
        totalBytes: Long,
        cancelIntent: PendingIntent?,
    ): Notification {
        val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.core_modeldownload_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, percent, totalBytes <= 0)
        if (totalBytes > 0) {
            builder.setContentText(
                context.getString(
                    R.string.core_modeldownload_progress,
                    percent,
                    formatMegabytes(downloadedBytes),
                    formatMegabytes(totalBytes),
                ),
            )
        } else {
            builder.setContentText(context.getString(R.string.core_modeldownload_preparing))
        }
        cancelIntent?.let {
            builder.addAction(0, context.getString(R.string.core_modeldownload_cancel), it)
        }
        return builder.build()
    }

    private fun formatMegabytes(bytes: Long): Long = bytes / (1024 * 1024)
}
