package app.gympulse.wear.workout

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import app.gympulse.wear.MainActivity
import app.gympulse.wear.R

/** Notification-Kanal und Ongoing Activity des laufenden Workouts auf der Uhr. */
internal object WorkoutOngoingNotification {
    const val CHANNEL_ID = "workout_ongoing"
    const val NOTIFICATION_ID = 1

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_workout),
            // Nicht MIN, sonst zeigt Wear OS die Ongoing Activity nicht an; Updates bleiben stumm (setSilent).
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_workout_description)
            setShowBadge(false)
            // Die Haptik bei Pausenende kommt direkt vom Vibrator, nicht über den Kanal.
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Baut die Notification und verknüpft sie mit einer Ongoing Activity (Zifferblatt, App-Launcher).
     * Ohne [state] entsteht ein Platzhalter für den sofortigen `startForeground`.
     */
    fun build(context: Context, state: WorkoutOngoingState?, nowEpochMillis: Long = System.currentTimeMillis()): Notification {
        val title = state?.title ?: context.getString(R.string.notification_workout_free)
        val restEndsAt = state?.restEndsAt
        val touchIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ongoing_workout)
            .setContentTitle(title)
            .setContentText(
                context.getString(if (restEndsAt != null) R.string.notification_resting else R.string.notification_training),
            )
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(touchIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (state != null) {
            builder.setUsesChronometer(true)
                .setWhen(restEndsAt ?: state.startedAt)
                .setChronometerCountDown(restEndsAt != null)
                .setShowWhen(true)
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        val status = when {
            state == null -> Status.forPart(Status.TextPart(title))
            restEndsAt != null -> Status.Builder()
                .addTemplate(context.getString(R.string.ongoing_status_rest_template))
                .addPart(PART_TIME, Status.TimerPart(epochToElapsedRealtime(restEndsAt, nowEpochMillis, nowElapsed)))
                .build()
            else -> Status.Builder()
                .addTemplate(context.getString(R.string.ongoing_status_training_template))
                .addPart(PART_TIME, Status.StopwatchPart(epochToElapsedRealtime(state.startedAt, nowEpochMillis, nowElapsed)))
                .build()
        }
        OngoingActivity.Builder(context, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_ongoing_workout)
            .setTouchIntent(touchIntent)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setStatus(status)
            .build()
            .apply(context)
        return builder.build()
    }

    private const val PART_TIME = "time"
}
