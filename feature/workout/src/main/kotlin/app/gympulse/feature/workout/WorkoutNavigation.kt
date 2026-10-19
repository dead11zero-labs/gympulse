package app.gympulse.feature.workout

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/**
 * Active workout. With [planId] the plan is started right away (unless a session is already
 * running, which is then resumed); without it the plan selection or the running session shows.
 */
@Serializable
data class WorkoutRoute(val planId: String? = null)

fun NavController.navigateToWorkout(planId: String? = null) {
    navigate(WorkoutRoute(planId)) {
        launchSingleTop = true
    }
}

/**
 * @param onClose called after the finished workout was confirmed in the summary dialog
 */
fun NavGraphBuilder.workoutScreen(onClose: () -> Unit) {
    composable<WorkoutRoute> {
        WorkoutScreen(onClose = onClose)
    }
}
