package app.gympulse.feature.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.gympulse.core.designsystem.theme.Spacing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import app.gympulse.core.designsystem.component.GymPulseCard
import app.gympulse.core.designsystem.component.GymPulseCardVariant
import app.gympulse.core.designsystem.component.ListGroup
import app.gympulse.core.designsystem.component.StatTile
import app.gympulse.core.designsystem.component.StatTileStyle
import app.gympulse.core.designsystem.theme.ExpressiveShapes
import app.gympulse.core.designsystem.theme.Sizing
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import app.gympulse.core.data.health.WorkoutHealthMetrics
import java.text.NumberFormat
import kotlin.math.roundToLong

/** Full-screen summary after finishing a workout: totals, sets per exercise and new records. */
@Composable
internal fun WorkoutSummaryScreen(
    summary: WorkoutSummary,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDone)
    val itemModifier = Modifier
        .widthIn(max = Spacing.maxContentWidth)
        .fillMaxWidth()

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "header") { SummaryHeader(summary, itemModifier) }
            item(key = "stats") { SummaryStats(summary, itemModifier) }
            val health = summary.health
            if (health != null && !health.isEmpty) {
                item(key = "health") { HealthMetricsCard(health, itemModifier) }
            }
            if (summary.recordCount > 0) {
                item(key = "records") { RecordsCard(summary, itemModifier) }
            }
            if (summary.exercises.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.feature_workout_summary_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = itemModifier,
                    )
                }
            }
            items(summary.exercises, key = { it.exerciseId }) { exercise ->
                SummaryExerciseCard(exercise, itemModifier)
            }
        }
        Button(
            onClick = onDone,
            modifier = Modifier
                .widthIn(max = Spacing.maxContentWidth)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.s),
        ) {
            Text(stringResource(R.string.feature_workout_summary_done))
        }
    }
}

@Composable
private fun SummaryHeader(summary: WorkoutSummary, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(top = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Surface(
            shape = ExpressiveShapes.full,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(Sizing.emptyStateIconContainer),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(Sizing.emptyStateIcon),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(R.string.feature_workout_summary_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (summary.title != null) {
            Text(
                text = summary.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SummaryStats(summary: WorkoutSummary, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(Spacing.gutter),
    ) {
        StatTile(
            label = stringResource(R.string.feature_workout_summary_duration_label),
            value = WorkoutFormat.formatElapsed(summary.durationSeconds),
            compact = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        StatTile(
            label = stringResource(R.string.feature_workout_summary_volume_label),
            value = WorkoutFormat.formatKg(summary.volumeKg),
            unit = stringResource(R.string.feature_workout_column_kg),
            style = StatTileStyle.Highlighted,
            compact = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        StatTile(
            label = stringResource(R.string.feature_workout_summary_sets_label),
            value = summary.completedSets.toString(),
            compact = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

/** Heart rate, steps, active calories and sleep from Health Connect, shown only for values that exist. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HealthMetricsCard(metrics: WorkoutHealthMetrics, modifier: Modifier = Modifier) {
    val numbers = NumberFormat.getIntegerInstance()
    val chips = buildList {
        metrics.averageHeartRateBpm?.let {
            add(stringResource(R.string.feature_workout_summary_health_avg_hr, numbers.format(it)))
        }
        metrics.maxHeartRateBpm?.let {
            add(stringResource(R.string.feature_workout_summary_health_max_hr, numbers.format(it)))
        }
        metrics.steps?.let { add(stringResource(R.string.feature_workout_summary_health_steps, numbers.format(it))) }
        metrics.activeCaloriesKcal?.let {
            add(stringResource(R.string.feature_workout_summary_health_calories, numbers.format(it.roundToLong())))
        }
        metrics.sleepMinutes?.let {
            add(stringResource(R.string.feature_workout_summary_health_sleep, it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR))
        }
    }
    ListGroup(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.feature_workout_summary_health_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                chips.forEach { label -> HealthValueChip(label) }
            }
        }
    }
}

/** Non-interactive value pill in the style of [RecordBadge]. */
@Composable
private fun HealthValueChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = ExpressiveShapes.full,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
        )
    }
}

private const val MINUTES_PER_HOUR = 60L

@Composable
private fun RecordsCard(summary: WorkoutSummary, modifier: Modifier = Modifier) {
    GymPulseCard(
        modifier = modifier,
        variant = GymPulseCardVariant.Tertiary,
        shape = ExpressiveShapes.largeIncreased,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(Icons.Filled.Star, contentDescription = null)
            Text(
                text = pluralStringResource(
                    R.plurals.feature_workout_summary_records,
                    summary.recordCount,
                    summary.recordCount,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        summary.exercises.filter { it.records.isNotEmpty() }.forEach { exercise ->
            Text(
                text = exercise.name ?: stringResource(R.string.feature_workout_summary_exercise_fallback),
                style = MaterialTheme.typography.titleSmall,
            )
            exercise.records.forEach { record -> RecordLine(record) }
        }
    }
}

@Composable
private fun RecordLine(record: PersonalRecord) {
    val label = stringResource(
        when (record.type) {
            RecordType.BEST_WEIGHT -> R.string.feature_workout_summary_record_best_weight
            RecordType.ESTIMATED_ONE_REP_MAX -> R.string.feature_workout_summary_record_one_rm
            RecordType.BEST_SET_VOLUME -> R.string.feature_workout_summary_record_set_volume
        },
    )
    val value = stringResource(R.string.feature_workout_kg_value, WorkoutFormat.formatKg(record.valueKg))
    val previous = stringResource(
        R.string.feature_workout_summary_record_previous,
        WorkoutFormat.formatKg(record.previousKg),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = "$value ($previous)", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SummaryExerciseCard(exercise: SummaryExercise, modifier: Modifier = Modifier) {
    ListGroup(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name ?: stringResource(R.string.feature_workout_summary_exercise_fallback),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.feature_workout_summary_exercise_sets,
                        exercise.sets.size,
                        exercise.sets.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (exercise.records.isNotEmpty()) {
                RecordBadge()
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        exercise.sets.forEach { set -> SummarySetRow(set) }
    }
}

@Composable
private fun RecordBadge() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = ExpressiveShapes.full,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Icon(Icons.Filled.Star, contentDescription = null)
            Text(
                text = stringResource(R.string.feature_workout_summary_record_badge),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun SummarySetRow(set: SummarySet) {
    val parts = buildList {
        val weight = set.weightKg
        val reps = set.reps
        if (reps != null && reps > 0) {
            if (weight != null && weight > 0.0) {
                add(stringResource(R.string.feature_workout_summary_set_weight_reps, WorkoutFormat.formatKg(weight), reps))
            } else {
                add(stringResource(R.string.feature_workout_summary_set_reps, reps))
            }
        }
        set.distanceMeters?.takeIf { it > 0.0 }?.let {
            add(stringResource(R.string.feature_workout_summary_set_distance, WorkoutFormat.formatKg(it)))
        }
        set.durationSeconds?.takeIf { it > 0 }?.let { add(WorkoutFormat.formatElapsed(it.toLong())) }
    }
    ListItem(
        headlineContent = { Text(parts.joinToString(" · ")) },
        leadingContent = {
            Text(
                text = set.number?.toString() ?: stringResource(R.string.feature_workout_summary_set_warmup_short),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
