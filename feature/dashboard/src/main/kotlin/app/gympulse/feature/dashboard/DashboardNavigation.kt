package app.gympulse.feature.dashboard

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

/** Detailanalyse der Woche; öffnet sich über die Kachel "Wochenvolumen". */
@Serializable
data object WeekAnalysisRoute

/**
 * @param onStartWorkout startet ein Workout; `planId` ist null für ein freies Workout.
 * @param onResumeWorkout öffnet das bereits laufende Workout.
 * @param onOpenWeekAnalysis öffnet die Wochenanalyse ([WeekAnalysisRoute]).
 */
fun NavGraphBuilder.dashboardScreen(
    onStartWorkout: (planId: String?) -> Unit,
    onResumeWorkout: () -> Unit,
    onOpenWeekAnalysis: () -> Unit,
) {
    composable<DashboardRoute> {
        DashboardScreen(
            onStartWorkout = onStartWorkout,
            onResumeWorkout = onResumeWorkout,
            onOpenWeekAnalysis = onOpenWeekAnalysis,
        )
    }
    composable<WeekAnalysisRoute> {
        WeekAnalysisScreen()
    }
}
