package app.gympulse.feature.dashboard

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

/**
 * @param onStartWorkout startet ein Workout; `planId` ist null für ein freies Workout.
 * @param onResumeWorkout öffnet das bereits laufende Workout.
 */
fun NavGraphBuilder.dashboardScreen(
    onStartWorkout: (planId: String?) -> Unit,
    onResumeWorkout: () -> Unit,
) {
    composable<DashboardRoute> {
        DashboardScreen(onStartWorkout = onStartWorkout, onResumeWorkout = onResumeWorkout)
    }
}
