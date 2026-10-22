package app.gympulse.feature.workout.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.workout.ActiveWorkoutController
import app.gympulse.core.workout.timer.runningAt
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Keeps the process alive during a workout and shows the ongoing notification with plan,
 * elapsed time and rest timer. Stops itself as soon as no session is active anymore.
 */
@AndroidEntryPoint
class ActiveWorkoutService : Service() {
    @Inject lateinit var controller: ActiveWorkoutController

    @Inject lateinit var time: TimeProvider

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        WorkoutNotifications.ensureChannel(this)
        if (!startInForeground(WorkoutNotifications.build(this, null))) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (observeJob == null) {
            observeJob = scope.launch { observeSession() }
        }
        // Not sticky: after process death the app restarts the service when it is opened again.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun observeSession() {
        val header = controller.activeSession
            .map { session -> session?.let { HeaderData(it.title, it.startedAt) } }
            .distinctUntilChanged()
        combine(header, controller.restTimerState) { data, rest -> data to rest }.collectLatest { (data, rest) ->
            if (data == null) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@collectLatest
            }
            val running = rest.runningAt(time.now())
            notify(WorkoutNotificationContent(data.title, data.startedAt, running))
            if (running != null) {
                // Switch back to the elapsed time once the rest is over.
                delay(Duration.between(time.now(), running.endsAt).toMillis().coerceAtLeast(0))
                notify(WorkoutNotificationContent(data.title, data.startedAt, null))
            }
        }
    }

    // The permission is checked right below; lint does not follow the combined condition.
    @SuppressLint("MissingPermission")
    private fun notify(content: WorkoutNotificationContent) {
        val notification = WorkoutNotifications.build(this, content)
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) {
            NotificationManagerCompat.from(this).notify(WorkoutNotifications.NOTIFICATION_ID, notification)
        }
    }

    private fun startInForeground(notification: Notification): Boolean {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        return try {
            ServiceCompat.startForeground(this, WorkoutNotifications.NOTIFICATION_ID, notification, type)
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) when started from the background.
            false
        }
    }

    private data class HeaderData(val title: String?, val startedAt: Instant)

    companion object {
        /** Starts the service; safe to call repeatedly while a session is running. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ActiveWorkoutService::class.java))
            } catch (e: IllegalStateException) {
                // App is not in the foreground; the next visible start will try again.
            }
        }
    }
}
