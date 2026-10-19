package app.gympulse.feature.dashboard

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

fun NavGraphBuilder.dashboardScreen(onStartWorkout: () -> Unit) {
    composable<DashboardRoute> {
        DashboardScreen(onStartWorkout = onStartWorkout)
    }
}
