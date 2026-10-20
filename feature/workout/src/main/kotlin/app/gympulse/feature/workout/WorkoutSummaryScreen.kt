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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "header") { SummaryHeader(summary, itemModifier) }
            item(key = "stats") { SummaryStats(summary, itemModifier) }
            if (summary.recordCount > 0) {
                item(key = "records") { RecordsCard(summary, itemModifier) }
            }
            if (summary.exercises.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.feature_workout_summary_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column {
            Text(
                text = stringResource(R.string.feature_workout_summary_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (summary.title != null) {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryStats(summary: WorkoutSummary, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        StatTile(
            label = stringResource(R.string.feature_workout_summary_duration_label),
            value = WorkoutFormat.formatElapsed(summary.durationSeconds),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = stringResource(R.string.feature_workout_summary_volume_label),
            value = stringResource(R.string.feature_workout_kg_value, WorkoutFormat.formatKg(summary.volumeKg)),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = stringResource(R.string.feature_workout_summary_sets_label),
            value = summary.completedSets.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun RecordsCard(summary: WorkoutSummary, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = exercise.name ?: stringResource(R.string.feature_workout_summary_exercise_fallback),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (exercise.records.isNotEmpty()) {
                    RecordBadge()
                }
            }
            Text(
                text = pluralStringResource(
                    R.plurals.feature_workout_summary_exercise_sets,
                    exercise.sets.size,
                    exercise.sets.size,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.m),
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = Spacing.xs),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            exercise.sets.forEach { set -> SummarySetRow(set) }
        }
    }
}

@Composable
private fun RecordBadge() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
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
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            headlineColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
