package app.gympulse.feature.workout.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.gympulse.core.workout.ActiveWorkoutForeground
import app.gympulse.core.workout.WorkoutForegroundMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts [ActiveWorkoutService] like the workout screen does. If Android 12+ refuses a foreground
 * service because the app is in the background (e.g. the watch started the workout), only a
 * plain notification is shown (plan B); the session itself is already stored.
 */
@Singleton
class ServiceActiveWorkoutForeground @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActiveWorkoutForeground {
    private val notificationOnly = AtomicBoolean(false)

    override fun show(title: String?, startedAt: Instant): WorkoutForegroundMode {
        if (ActiveWorkoutService.start(context)) {
            notificationOnly.set(false)
            return WorkoutForegroundMode.FOREGROUND_SERVICE
        }
        WorkoutNotifications.ensureChannel(context)
        notify(WorkoutNotifications.buildStartedInBackground(context, title, startedAt))
        notificationOnly.set(true)
        return WorkoutForegroundMode.NOTIFICATION_ONLY
    }

    override fun clearNotificationOnly() {
        // Only our own plain notification; the service removes its notification itself.
        if (notificationOnly.getAndSet(false)) {
            NotificationManagerCompat.from(context).cancel(WorkoutNotifications.NOTIFICATION_ID)
        }
    }

    // The permission is checked right below; lint does not follow the combined condition.
    @SuppressLint("MissingPermission")
    private fun notify(notification: Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) {
            NotificationManagerCompat.from(context).notify(WorkoutNotifications.NOTIFICATION_ID, notification)
        }
    }
}
