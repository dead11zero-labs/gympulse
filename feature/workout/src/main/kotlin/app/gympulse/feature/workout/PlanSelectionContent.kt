package app.gympulse.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.gympulse.core.designsystem.adaptive.WindowWidthClass
import app.gympulse.core.designsystem.component.EmptyState
import app.gympulse.core.designsystem.component.GymPulseCard
import app.gympulse.core.designsystem.component.GymPulseCardVariant
import app.gympulse.core.designsystem.theme.ExpressiveShapes
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing

/** "Workout starten": großes "Leeres Workout" und die Pläne als Ebene-1-Karten. */
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
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthClass = WindowWidthClass.fromWidth(maxWidth)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = widthClass.horizontalMargin, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(widthClass.gutter),
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
            item { EmptyWorkoutCard(onClick = onStartEmpty, modifier = itemModifier) }
            item {
                Row(
                    modifier = itemModifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = stringResource(R.string.feature_workout_plans_header),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    if (plans.isNotEmpty()) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.feature_workout_plans_available,
                                plans.size,
                                plans.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                items(plans, key = { it.id }) { plan ->
                    PlanCard(plan = plan, onStart = { onStartPlan(plan.id) }, modifier = itemModifier)
                }
            }
        }
    }
}

/** Hero-Karte in `primary`: ein leeres Workout starten und Übungen spontan hinzufügen. */
@Composable
private fun EmptyWorkoutCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = ExpressiveShapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = ICON_TILE_ALPHA),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(Sizing.iconTile),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(Sizing.iconLarge))
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

/** Plan als Ebene-1-Karte mit Kalender-Kachel, Übungsanzahl und Start-Knopf. */
@Composable
private fun PlanCard(plan: PlanOption, onStart: () -> Unit, modifier: Modifier = Modifier) {
    GymPulseCard(
        onClick = onStart,
        modifier = modifier,
        variant = GymPulseCardVariant.Level1,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Sizing.minTouchTarget),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.DateRange, contentDescription = null)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.feature_workout_exercise_count,
                        plan.exerciseCount,
                        plan.exerciseCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = ExpressiveShapes.full,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(Sizing.minTouchTarget),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.feature_workout_start_plan, plan.name),
                    )
                }
            }
        }
    }
}

private const val ICON_TILE_ALPHA = 0.12f
