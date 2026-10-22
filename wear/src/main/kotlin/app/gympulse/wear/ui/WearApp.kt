package app.gympulse.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import app.gympulse.wear.theme.GymPulseWearTheme

/*
 * Navigation: SwipeDismissableNavHost (Wear Compose Navigation). Entscheidung gegen compose-navigation3
 * siehe PR-Beschreibung. Home zeigt je nach Zustand die Planauswahl oder die aktive Übung; Übersicht und
 * Bestätigung liegen darüber und lassen sich per Wischen nach rechts schließen.
 */
private object Routes {
    const val HOME = "home"
    const val OVERVIEW = "overview"
    const val CONFIRM_FINISH = "confirm/finish"
    const val CONFIRM_DISCARD = "confirm/discard"
}

@Composable
fun WearApp(
    plansViewModel: PlansViewModel = hiltViewModel(),
    workoutViewModel: WorkoutViewModel = hiltViewModel(),
) {
    GymPulseWearTheme {
        AppScaffold {
            val navController = rememberSwipeDismissableNavController()
            val plansState by plansViewModel.uiState.collectAsStateWithLifecycle()
            val workoutState by workoutViewModel.uiState.collectAsStateWithLifecycle()
            val hasSession = workoutState.session != null

            // Das Workout wurde am Handy beendet oder hier bestätigt: zurück zur Planauswahl.
            LaunchedEffect(hasSession) {
                if (!hasSession) navController.popBackStack(Routes.HOME, inclusive = false)
            }

            SwipeDismissableNavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    if (hasSession) {
                        val restRemaining by rememberRestRemainingSeconds(workoutState.session?.restEndsAt)
                        WorkoutScreen(
                            state = workoutState,
                            restRemainingSeconds = restRemaining,
                            onSelectField = workoutViewModel::selectField,
                            onRotate = workoutViewModel::rotate,
                            onCompleteSet = workoutViewModel::completeSet,
                            onPreviousExercise = workoutViewModel::previousExercise,
                            onNextExercise = workoutViewModel::nextExercise,
                            onOpenOverview = { navController.navigate(Routes.OVERVIEW) },
                            onFinish = { navController.navigate(Routes.CONFIRM_FINISH) },
                            onAdjustRest = workoutViewModel::adjustRest,
                            onSkipRest = workoutViewModel::skipRest,
                            onDismissNotices = workoutViewModel::dismissNotices,
                        )
                    } else {
                        PlansScreen(
                            state = plansState,
                            onStart = plansViewModel::startWorkout,
                            onDismissNotices = plansViewModel::dismissNotices,
                        )
                    }
                }
                composable(Routes.OVERVIEW) {
                    OverviewScreen(
                        state = workoutState,
                        onSelectExercise = { id ->
                            workoutViewModel.selectExercise(id)
                            navController.popBackStack()
                        },
                        onFinish = { navController.navigate(Routes.CONFIRM_FINISH) },
                        onDiscard = { navController.navigate(Routes.CONFIRM_DISCARD) },
                    )
                }
                composable(Routes.CONFIRM_FINISH) {
                    ConfirmEndScreen(
                        kind = EndKind.FINISH,
                        onConfirm = { confirmEnd(navController, workoutViewModel::finish) },
                        onCancel = { navController.popBackStack() },
                    )
                }
                composable(Routes.CONFIRM_DISCARD) {
                    ConfirmEndScreen(
                        kind = EndKind.DISCARD,
                        onConfirm = { confirmEnd(navController, workoutViewModel::discard) },
                        onCancel = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

private fun confirmEnd(navController: NavHostController, action: () -> Unit) {
    action()
    navController.popBackStack(Routes.HOME, inclusive = false)
}
