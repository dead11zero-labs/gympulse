package app.gympulse.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.designsystem.adaptive.WindowWidthClass
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.insightcards.InsightsSection
import app.gympulse.feature.dashboard.coach.CoachSection
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun DashboardScreen(
    onStartWorkout: (planId: String?) -> Unit,
    onResumeWorkout: () -> Unit,
    onOpenWeekAnalysis: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Schritte ändern sich außerhalb der App; beim Zurückkehren neu lesen.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshHealth()
        onPauseOrDispose { }
    }
    DashboardContent(
        state = state,
        // TODO: Sobald der Kalender den heutigen Plan liefert, hier dessen planId übergeben.
        onStartWorkout = { onStartWorkout(null) },
        onResumeWorkout = onResumeWorkout,
        onOpenWeekAnalysis = onOpenWeekAnalysis,
        modifier = modifier,
    )
}

@Composable
internal fun DashboardContent(
    state: DashboardUiState,
    onStartWorkout: () -> Unit,
    onResumeWorkout: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenWeekAnalysis: () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = Spacing.maxContentWidth)
                .fillMaxSize(),
        ) {
            // Ab Material-3-Klasse Medium stehen die Widgets nebeneinander.
            val widthClass = WindowWidthClass.fromWidth(maxWidth)
            val twoColumns = widthClass.isAtLeastMedium
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = widthClass.horizontalMargin, vertical = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.gutter),
            ) {
                Header(today = state.today, greeting = state.greeting)
                state.activeWorkout?.let { active ->
                    ActiveWorkoutCard(workout = active, zone = state.zone, onResume = onResumeWorkout)
                }
                if (twoColumns) {
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.gutter),
                    ) {
                        WeeklyVolumeCard(
                            volume = state.weeklyVolume,
                            onClick = onOpenWeekAnalysis,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        WeeklyProgressCard(
                            progress = state.weeklyProgress,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    WeeklyVolumeCard(volume = state.weeklyVolume, onClick = onOpenWeekAnalysis)
                    WeeklyProgressCard(progress = state.weeklyProgress)
                }
                StepsCard(steps = state.steps)
                SleepCard(sleep = state.sleep)
                CoachSection()
                InsightsSection(insights = state.insights)
                RecentSessionsSection(sessions = state.recentSessions, today = state.today)
                // Platz, damit der FAB den letzten Eintrag nicht verdeckt.
                Box(modifier = Modifier.height(Spacing.xxl + Spacing.l))
            }
            StartWorkoutButton(
                isResume = state.activeWorkout != null,
                onClick = if (state.activeWorkout != null) onResumeWorkout else onStartWorkout,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.m),
            )
        }
    }
}

@Composable
private fun Header(today: LocalDate, greeting: Greeting, modifier: Modifier = Modifier) {
    val dateText = remember(today) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(today) }
    val greetingRes = when (greeting) {
        Greeting.MORNING -> R.string.feature_dashboard_greeting_morning
        Greeting.AFTERNOON -> R.string.feature_dashboard_greeting_afternoon
        Greeting.EVENING -> R.string.feature_dashboard_greeting_evening
    }
    Column(modifier = modifier.fillMaxWidth().padding(bottom = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = stringResource(greetingRes),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = dateText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StartWorkoutButton(isResume: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
        text = {
            Text(
                text = stringResource(
                    if (isResume) R.string.feature_dashboard_resume_workout else R.string.feature_dashboard_start_workout,
                ).uppercase(),
                style = MaterialTheme.typography.labelLarge,
            )
        },
        modifier = modifier,
    )
}
