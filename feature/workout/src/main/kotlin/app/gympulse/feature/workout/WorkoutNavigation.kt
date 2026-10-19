package app.gympulse.feature.workout

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object WorkoutRoute

fun NavGraphBuilder.workoutScreen() {
    composable<WorkoutRoute> {
        WorkoutScreen()
    }
}
