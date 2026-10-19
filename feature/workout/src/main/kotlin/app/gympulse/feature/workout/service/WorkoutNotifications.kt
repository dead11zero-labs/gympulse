package app.gympulse.feature.workout.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.gympulse.feature.workout.R
import app.gympulse.feature.workout.timer.RestTimerState
import java.time.Instant
import java.util.Date

/** What the ongoing notification shows. */
internal data class WorkoutNotificationContent(
    val title: String?,
    val startedAt: Instant,
    /** Running rest period, or null. */
    val rest: RestTimerState?,
)

internal object WorkoutNotifications {
    const val CHANNEL_ID = "active_workout"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.feature_workout_notification_channel))
            .setDescription(context.getString(R.string.feature_workout_notification_channel_description))
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /**
     * Builds the notification. Elapsed time and rest countdown use the system chronometer,
     * so the notification only needs an update when the state changes.
     */
    fun build(context: Context, content: WorkoutNotificationContent?): Notification {
        val title = content?.title ?: context.getString(R.string.feature_workout_empty_workout)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_workout_notification)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        openAppIntent(context)?.let(builder::setContentIntent)

        if (content == null) {
            return builder.setContentText(context.getString(R.string.feature_workout_notification_running)).build()
        }
        val startedText = context.getString(
            R.string.feature_workout_notification_started_at,
            DateFormat.getTimeFormat(context).format(Date(content.startedAt.toEpochMilli())),
        )
        val rest = content.rest
        if (rest != null) {
            builder
                .setContentText(context.getString(R.string.feature_workout_rest))
                .setSubText(startedText)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(rest.endsAt.toEpochMilli())
        } else {
            builder
                .setContentText(context.getString(R.string.feature_workout_notification_running))
                .setSubText(startedText)
                .setUsesChronometer(true)
                .setChronometerCountDown(false)
                .setWhen(content.startedAt.toEpochMilli())
        }
        return builder.build()
    }

    /** Tapping the notification brings the app's task to the front. */
    private fun openAppIntent(context: Context): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
