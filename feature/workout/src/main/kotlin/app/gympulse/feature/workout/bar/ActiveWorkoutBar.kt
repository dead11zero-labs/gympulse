package app.gympulse.feature.workout.bar

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.gympulse.core.data.repository.WorkoutRepository
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.feature.workout.R
import app.gympulse.feature.workout.WorkoutFormat
import app.gympulse.feature.workout.service.ActiveWorkoutService
import app.gympulse.feature.workout.timer.RestTimer
import app.gympulse.feature.workout.timer.RestTimerState
import app.gympulse.feature.workout.timer.Ticker
import app.gympulse.feature.workout.timer.runningAt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import androidx.compose.foundation.layout.heightIn
import app.gympulse.core.designsystem.component.ListRowIcon
import app.gympulse.core.designsystem.theme.Sizing

internal data class ActiveWorkoutBarState(
    val sessionId: String,
    val title: String?,
    val startedAt: Instant,
    val rest: RestTimerState?,
)

@HiltViewModel
internal class ActiveWorkoutBarViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    restTimer: RestTimer,
    ticker: Ticker,
    time: TimeProvider,
) : ViewModel() {
    val state: StateFlow<ActiveWorkoutBarState?> = combine(
        workoutRepository.observeActiveSession()
            .map { session -> session?.let { Triple(it.id, it.title, it.startedAt) } }
            .distinctUntilChanged(),
        restTimer.state,
    ) { session, rest ->
        session?.let { (id, title, startedAt) -> ActiveWorkoutBarState(id, title, startedAt, rest) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val now: StateFlow<Instant> = ticker.ticks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), time.now())

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Starts the foreground service while a session is active. Place it once at the app root,
 * where it runs while the app is visible (a foreground service may only start from there).
 */
@Composable
fun ActiveWorkoutServiceEffect() {
    val viewModel: ActiveWorkoutBarViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sessionId = state?.sessionId
    LaunchedEffect(sessionId) {
        if (sessionId != null) ActiveWorkoutService.start(context)
    }
}

/**
 * Compact bar above the navigation while a workout runs and another screen is shown
 * (plan, elapsed time, rest timer). Renders nothing without an active session.
 */
@Composable
fun ActiveWorkoutBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ActiveWorkoutBarViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return
    val now by viewModel.now.collectAsStateWithLifecycle()

    val elapsed = WorkoutFormat.formatElapsed(Duration.between(current.startedAt, now).seconds)
    val rest = current.rest.runningAt(now)
    val supporting = if (rest != null) {
        val restText = stringResource(
            R.string.feature_workout_bar_rest,
            WorkoutFormat.formatElapsed(rest.remainingSeconds(now).toLong()),
        )
        "$elapsed · $restText"
    } else {
        elapsed
    }
    val openLabel = stringResource(R.string.feature_workout_bar_open)

    ListItem(
        headlineContent = {
            Text(current.title ?: stringResource(R.string.feature_workout_empty_workout), maxLines = 1)
        },
        supportingContent = { Text(supporting, maxLines = 1) },
        leadingContent = {
            ListRowIcon(
                icon = Icons.Filled.PlayArrow,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        trailingContent = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = openLabel) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            headlineColor = MaterialTheme.colorScheme.onSecondaryContainer,
            supportingColor = MaterialTheme.colorScheme.onSecondaryContainer,
            leadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            trailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = modifier
            .heightIn(min = Sizing.minTouchTarget)
            .clickable(onClickLabel = openLabel, onClick = onClick),
    )
}
