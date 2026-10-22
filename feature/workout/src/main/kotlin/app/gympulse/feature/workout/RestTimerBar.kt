package app.gympulse.feature.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.workout.timer.RestTimer
import app.gympulse.core.workout.timer.RestTimerState
import java.time.Instant
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.FilledTonalButton
import app.gympulse.core.designsystem.component.GymPulseProgressBar
import app.gympulse.core.designsystem.component.LabelCaps
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.metricMedium

/** Rest countdown below the set list, with -15 s / +15 s and skip. */
@Composable
internal fun RestTimerBar(
    state: RestTimerState,
    now: () -> Instant,
    onSkip: () -> Unit,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = RestTimer.ADJUST_STEP_SECONDS
    val remaining = WorkoutFormat.formatElapsed(state.remainingSeconds(now()).toLong())
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(Sizing.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = Spacing.maxContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenMargin, vertical = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabelCaps(
                        text = stringResource(R.string.feature_workout_rest),
                        modifier = Modifier.weight(1f),
                    )
                    // Active timers are shown in the accent color.
                    Text(
                        text = remaining,
                        style = MaterialTheme.typography.metricMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                GymPulseProgressBar(
                    progress = state.remainingFraction(now()),
                    animate = false,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(onClick = { onAdjust(-step) }) {
                        Text(stringResource(R.string.feature_workout_rest_minus, step))
                    }
                    FilledTonalButton(onClick = { onAdjust(step) }) {
                        Text(stringResource(R.string.feature_workout_rest_plus, step))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = onSkip) {
                        Text(stringResource(R.string.feature_workout_rest_skip))
                    }
                }
            }
        }
    }
}
