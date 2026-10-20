package app.gympulse.feature.workout.timer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Configured default rest duration. The settings screen will provide its own implementation;
 * then only the Hilt binding in [app.gympulse.feature.workout.di.WorkoutModule] changes.
 */
interface RestTimerSettings {
    val restSeconds: Flow<Int>
}

class DefaultRestTimerSettings @Inject constructor() : RestTimerSettings {
    override val restSeconds: Flow<Int> = flowOf(DEFAULT_REST_SECONDS)

    companion object {
        const val DEFAULT_REST_SECONDS = 90
    }
}
