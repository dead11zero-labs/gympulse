package app.gympulse.wear.workout

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.gympulse.wear.data.WatchWorkoutRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground Service (Typ `health`) während eines Workouts auf der Uhr.
 *
 * Zeigt Trainingszeit bzw. Pausen-Countdown als Ongoing Activity (Zifferblatt, Launcher), hält bis
 * zum Pausenende einen Wake Lock und vibriert dann. Die Logik steckt im [WatchRestTracker]; der
 * Dienst liefert nur die Effekte. Er beendet sich selbst, sobald keine Session mehr aktiv ist
 * (Beenden oder Verwerfen, egal ob auf Uhr oder Telefon).
 *
 * Solange er läuft, meldet die Uhr dem Telefon über [RestAlertAdvertiser], dass sie das Pausenende
 * selbst signalisiert; das Telefon vibriert dann nicht.
 */
@AndroidEntryPoint
class WatchWorkoutService : Service(), WatchRestEffects {
    @Inject lateinit var repository: WatchWorkoutRepository

    @Inject lateinit var clock: WearClock

    @Inject lateinit var advertiser: RestAlertAdvertiser

    @Inject lateinit var heartRateRecording: HeartRateRecording

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var trackJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var inForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        WorkoutOngoingNotification.ensureChannel(this)
        if (!inForeground) {
            if (!startInForeground(WorkoutOngoingNotification.build(this, null))) {
                stopSelf()
                return START_NOT_STICKY
            }
            inForeground = true
            advertiser.setActive(true)
            // Im Vordergrund darf die Pulsmessung starten, falls sie vorher im Hintergrund scheiterte.
            heartRateRecording.resume()
        }
        if (trackJob == null) {
            trackJob = scope.launch { WatchRestTracker(clock, this@WatchWorkoutService).run(repository.session) }
        }
        // Nicht sticky: Nach einem Prozess-Ende startet ihn der nächste Snapshot bzw. das Öffnen der App.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        releaseAwake()
        if (inForeground) advertiser.setActive(false)
        super.onDestroy()
    }

    override fun show(state: WorkoutOngoingState) {
        notify(WorkoutOngoingNotification.build(this, state, clock.nowMillis()))
    }

    override fun keepAwakeUntil(endsAt: Long) {
        releaseAwake()
        val timeout = (endsAt - clock.nowMillis()).coerceAtLeast(0) + WAKE_LOCK_GRACE_MILLIS
        val lock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?: return
        lock.setReferenceCounted(false)
        lock.acquire(timeout)
        wakeLock = lock
    }

    override fun releaseAwake() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun restFinished() {
        RestHaptics.vibrate(this)
    }

    override fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Die Berechtigung wird direkt darunter geprüft; lint folgt der Bedingung nicht.
    @SuppressLint("MissingPermission")
    private fun notify(notification: Notification) {
        val allowed = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) {
            NotificationManagerCompat.from(this).notify(WorkoutOngoingNotification.NOTIFICATION_ID, notification)
        }
    }

    private fun startInForeground(notification: Notification): Boolean = try {
        ServiceCompat.startForeground(
            this,
            WorkoutOngoingNotification.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
        )
        true
    } catch (e: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException (API 31+) beim Start aus dem Hintergrund.
        false
    } catch (e: SecurityException) {
        // Voraussetzungen für den Typ health fehlen.
        false
    }

    private companion object {
        const val WAKE_LOCK_TAG = "GymPulse:WatchRestTimer"

        /** Etwas länger als die Pause, damit Vibration und Notification-Update sicher noch laufen. */
        const val WAKE_LOCK_GRACE_MILLIS = 5_000L
    }
}
