package app.gympulse.feature.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun WorkoutScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val restTimer by viewModel.restTimerState.collectAsStateWithLifecycle()
    val exerciseOptions by viewModel.exerciseOptions.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()

    NotificationPermissionEffect(active = uiState is WorkoutUiState.Active)

    val finishedSummary = summary
    if (finishedSummary != null) {
        WorkoutSummaryScreen(
            summary = finishedSummary,
            onDone = {
                viewModel.dismissSummary()
                onClose()
            },
            modifier = modifier,
        )
        return
    }

    when (val state = uiState) {
        WorkoutUiState.Loading -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is WorkoutUiState.SelectPlan -> PlanSelectionContent(
            plans = state.plans,
            onStartPlan = viewModel::startWorkout,
            onStartEmpty = { viewModel.startWorkout(null) },
            modifier = modifier,
        )
        is WorkoutUiState.Active -> ActiveWorkoutContent(
            session = state.session,
            now = { now },
            restTimer = restTimer,
            exerciseOptions = exerciseOptions,
            actions = WorkoutActions(
                onWeightChange = viewModel::onWeightChange,
                onRepsChange = viewModel::onRepsChange,
                onDurationChange = viewModel::onDurationChange,
                onDistanceChange = viewModel::onDistanceChange,
                onToggleCompleted = viewModel::toggleSetCompleted,
                onToggleWarmup = viewModel::toggleWarmup,
                onAddSet = viewModel::addSet,
                onDeleteSet = viewModel::deleteSet,
                onAddExercise = viewModel::addExercise,
                onRemoveExercise = viewModel::removeExercise,
                onNotesChange = viewModel::onNotesChange,
                onSkipRest = viewModel::skipRest,
                onAdjustRest = viewModel::adjustRest,
                onFinish = viewModel::finishWorkout,
                onDiscard = viewModel::discardWorkout,
            ),
            modifier = modifier,
        )
    }
}

/**
 * Asks for POST_NOTIFICATIONS (Android 13+) once a workout is running. The foreground service
 * runs either way; without the permission its notification is just not shown.
 */
@Composable
private fun NotificationPermissionEffect(active: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(active) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (active && !asked && !granted) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
