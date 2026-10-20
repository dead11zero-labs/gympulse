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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.gympulse.core.designsystem.component.EmptyState
import app.gympulse.core.designsystem.component.GymPulseCard
import app.gympulse.core.designsystem.component.GymPulseCardVariant
import app.gympulse.core.designsystem.component.ListGroup
import app.gympulse.core.designsystem.component.ListRow
import app.gympulse.core.designsystem.component.ListRowDivider
import app.gympulse.core.designsystem.component.SectionHeader
import app.gympulse.core.designsystem.theme.ExpressiveShapes
import app.gympulse.core.designsystem.theme.Sizing

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
        verticalArrangement = Arrangement.spacedBy(Spacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(modifier = itemModifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = stringResource(R.string.feature_workout_choose_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.feature_workout_choose_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            GymPulseCard(
                onClick = onStartEmpty,
                modifier = itemModifier,
                variant = GymPulseCardVariant.Primary,
                shape = ExpressiveShapes.extraLargeIncreased,
                contentPadding = PaddingValues(Spacing.l),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    Surface(
                        shape = ExpressiveShapes.full,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(Sizing.minTouchTarget),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.feature_workout_empty_workout),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.feature_workout_empty_workout_description),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        item {
            SectionHeader(
                title = stringResource(R.string.feature_workout_plans_header),
                modifier = itemModifier,
                contentPadding = PaddingValues(vertical = Spacing.xs),
            )
        }
        if (plans.isEmpty()) {
            item {
                GymPulseCard(variant = GymPulseCardVariant.Outlined, modifier = itemModifier) {
                    EmptyState(
                        icon = Icons.Filled.DateRange,
                        title = stringResource(R.string.feature_workout_no_plans_title),
                        message = stringResource(R.string.feature_workout_no_plans),
                    )
                }
            }
        } else {
            item(key = "plans") {
                ListGroup(modifier = itemModifier) {
                    plans.forEachIndexed { index, plan ->
                        if (index > 0) ListRowDivider()
                        ListRow(
                            headline = plan.name,
                            supportingText = pluralStringResource(
                                R.plurals.feature_workout_exercise_count,
                                plan.exerciseCount,
                                plan.exerciseCount,
                            ),
                            leadingIcon = Icons.Filled.DateRange,
                            trailingContent = {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.feature_workout_start_plan, plan.name),
                                )
                            },
                            onClick = { onStartPlan(plan.id) },
                        )
                    }
                }
            }
        }
    }
}
