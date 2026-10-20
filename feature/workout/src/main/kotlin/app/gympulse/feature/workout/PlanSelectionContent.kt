package app.gympulse.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.gympulse.core.designsystem.theme.Spacing

@Composable
internal fun PlanSelectionContent(
    plans: List<PlanOption>,
    onStartPlan: (String) -> Unit,
    onStartEmpty: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemModifier = Modifier
        .widthIn(max = Spacing.maxContentWidth)
        .fillMaxWidth()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(modifier = itemModifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = stringResource(R.string.feature_workout_choose_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.feature_workout_choose_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Card(
                onClick = onStartEmpty,
                modifier = itemModifier,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.feature_workout_empty_workout)) },
                    supportingContent = { Text(stringResource(R.string.feature_workout_empty_workout_description)) },
                    leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.feature_workout_plans_header),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = itemModifier.padding(top = Spacing.xs),
            )
        }
        if (plans.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.feature_workout_no_plans),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = itemModifier,
                )
            }
        }
        items(plans, key = { it.id }) { plan ->
            OutlinedCard(onClick = { onStartPlan(plan.id) }, modifier = itemModifier) {
                ListItem(
                    headlineContent = { Text(plan.name) },
                    supportingContent = {
                        Text(
                            pluralStringResource(
                                R.plurals.feature_workout_exercise_count,
                                plan.exerciseCount,
                                plan.exerciseCount,
                            ),
                        )
                    },
                    trailingContent = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}
