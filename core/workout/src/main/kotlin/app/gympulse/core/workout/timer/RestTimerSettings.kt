package app.gympulse.core.workout.timer

import kotlinx.coroutines.flow.Flow

/**
 * Configured default rest duration. The implementation is bound in the app module from the
 * user settings (DataStore).
 */
interface RestTimerSettings {
    val restSeconds: Flow<Int>
}
