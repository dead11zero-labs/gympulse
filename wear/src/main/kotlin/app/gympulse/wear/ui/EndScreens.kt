package app.gympulse.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import app.gympulse.wear.R

enum class EndKind { FINISH, DISCARD }

/** Übungsliste zum Wechseln plus Beenden/Verwerfen. */
@Composable
fun OverviewScreen(
    state: WorkoutUiState,
    onSelectExercise: (sessionExerciseId: String) -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    val exercises = state.exercises
    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(state = listState, horizontalAlignment = Alignment.CenterHorizontally) {
            item { ListHeader { Text(stringResource(R.string.overview_title)) } }
            items(exercises.size) { index ->
                val exercise = exercises[index]
                val done = exercise.sets.count { it.isCompleted }
                val selected = index == state.exerciseIndex
                Button(
                    onClick = { onSelectExercise(exercise.sessionExerciseId) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    label = { Text(exercise.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = {
                        Text(stringResource(R.string.overview_sets_progress, done, exercise.sets.size), maxLines = 1)
                    },
                )
            }
            item {
                Button(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.overview_finish), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
            item {
                Button(
                    onClick = onDiscard,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    label = { Text(stringResource(R.string.overview_discard), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

/** Bestätigung vor Beenden/Verwerfen. Wischen nach rechts bricht ab. */
@Composable
fun ConfirmEndScreen(
    kind: EndKind,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    val title = if (kind == EndKind.FINISH) R.string.confirm_finish_title else R.string.confirm_discard_title
    val text = if (kind == EndKind.FINISH) R.string.confirm_finish_text else R.string.confirm_discard_text
    val action = if (kind == EndKind.FINISH) R.string.confirm_finish_action else R.string.confirm_discard_action
    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(state = listState, horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                Text(
                    text = stringResource(text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (kind == EndKind.DISCARD) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    label = { Text(stringResource(action), maxLines = 1) },
                )
            }
            item {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    label = { Text(stringResource(R.string.confirm_cancel), maxLines = 1) },
                )
            }
        }
    }
}
