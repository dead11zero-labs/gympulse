package app.gympulse.core.workout

import java.time.Instant

/**
 * Decides whether the phone itself signals the end of a rest period (vibration). While a watch
 * with the GymPulse app is connected, only the watch vibrates. Bound in `core:wearsync`.
 */
interface RestAlertPolicy {
    suspend fun alertOnPhone(): Boolean
}

/** How a running workout is shown outside the app, see [ActiveWorkoutForeground.show]. */
enum class WorkoutForegroundMode {
    /** The foreground service with the ongoing notification runs. */
    FOREGROUND_SERVICE,

    /**
     * The system did not allow a foreground service (Android 12+, app in the background); only a
     * plain notification is shown. Opening the app starts the service as usual.
     */
    NOTIFICATION_ONLY,
}

/**
 * Shows a running workout outside the app, e.g. when the watch started it while the phone app
 * was in the background. Bound in `feature:workout` (foreground service and notification).
 */
interface ActiveWorkoutForeground {
    fun show(title: String?, startedAt: Instant): WorkoutForegroundMode

    /** Removes the plain notification of [WorkoutForegroundMode.NOTIFICATION_ONLY] after the workout ended. */
    fun clearNotificationOnly()
}
