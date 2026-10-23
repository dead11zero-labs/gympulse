package app.gympulse.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import app.gympulse.wear.R

/** Planauswahl: ein Tipp startet das Workout über das Telefon. */
@Composable
fun PlansScreen(
    state: PlansUiState,
    onStart: (planId: String?) -> Unit,
    onDismissNotices: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    val plans = state.plans
    val status = statusMessage(state.connection, state.incompatibleProtocol, state.notices)
    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(state = listState, horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                ListHeader { Text(stringResource(R.string.plans_title)) }
            }
            if (status != null) {
                item { StatusBanner(message = status, onDismissNotice = onDismissNotices) }
            }
            if (state.startPending) {
                item {
                    Text(
                        text = stringResource(R.string.plans_starting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                Button(
                    onClick = { onStart(null) },
                    enabled = !state.startPending,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.plans_free_workout), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text(stringResource(R.string.plans_free_workout_hint), maxLines = 1) },
                )
            }
            when {
                plans == null -> item {
                    Text(
                        text = stringResource(R.string.plans_waiting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                plans.isEmpty() -> item {
                    Text(
                        text = stringResource(R.string.plans_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> items(plans.size) { index ->
                    val plan = plans[index]
                    Button(
                        onClick = { onStart(plan.planId) },
                        enabled = !state.startPending,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(plan.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        secondaryLabel = {
                            Text(
                                pluralStringResource(R.plurals.plans_exercise_count, plan.exerciseCount, plan.exerciseCount),
                                maxLines = 1,
                            )
                        },
                    )
                }
            }
        }
    }
}
