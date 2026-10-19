package app.gympulse.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import app.gympulse.feature.calendar.calendarScreen
import app.gympulse.feature.dashboard.DashboardRoute
import app.gympulse.feature.dashboard.dashboardScreen
import app.gympulse.feature.library.libraryScreen
import app.gympulse.feature.plans.plansScreen
import app.gympulse.feature.settings.settingsScreen
import app.gympulse.feature.workout.WorkoutRoute
import app.gympulse.feature.workout.workoutScreen

@Composable
fun GymPulseNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = DashboardRoute,
        modifier = modifier,
    ) {
        // TODO: planId an die Workout-Route übergeben, sobald sie ein optionales Argument hat.
        dashboardScreen(
            onStartWorkout = { _ -> navController.navigate(WorkoutRoute) },
            onResumeWorkout = { navController.navigate(WorkoutRoute) },
        )
        plansScreen(navController)
        libraryScreen()
        calendarScreen()
        workoutScreen()
        settingsScreen()
    }
}
