package app.gympulse.feature.workout

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
import app.gympulse.feature.workout.timer.RestTimer
import app.gympulse.feature.workout.timer.RestTimerState
import java.time.Instant

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
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = Spacing.maxContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenMargin, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.feature_workout_rest),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = WorkoutFormat.formatElapsed(state.remainingSeconds(now()).toLong()),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                LinearProgressIndicator(
                    progress = { state.remainingFraction(now()) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = { onAdjust(-step) }) {
                        Text(stringResource(R.string.feature_workout_rest_minus, step))
                    }
                    OutlinedButton(onClick = { onAdjust(step) }) {
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
