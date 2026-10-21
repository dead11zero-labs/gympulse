package app.gympulse.feature.plans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.gympulse.core.data.ui.labelRes
import app.gympulse.core.designsystem.component.EmptyState
import app.gympulse.core.designsystem.component.GymPulseSearchField
import app.gympulse.core.designsystem.component.ListRow
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.model.Exercise

/** Vollflächige Übungsauswahl mit Suche und Mehrfachauswahl. */
@Composable
internal fun ExercisePicker(
    exercises: List<Exercise>,
    state: ExercisePickerState,
    onQueryChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = remember(exercises, state.query) { filterExercises(exercises, state.query) }
    val selectedCount = state.selectedIds.size

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = Spacing.screenMargin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.plans_picker_close))
                }
                Text(
                    text = stringResource(R.string.plans_add_exercises),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
            }
            GymPulseSearchField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.plans_picker_search),
                clearContentDescription = stringResource(R.string.plans_picker_clear),
                modifier = Modifier.padding(horizontal = Spacing.screenMargin, vertical = Spacing.xs),
            )
            if (filtered.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.plans_picker_empty),
                    message = stringResource(R.string.plans_picker_empty_hint),
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    items(filtered, key = { it.id }) { exercise ->
                        val selected = exercise.id in state.selectedIds
                        Surface(
                            modifier = Modifier
                                .animateItem()
                                .toggleable(
                                    value = selected,
                                    role = Role.Checkbox,
                                    onValueChange = { onToggle(exercise.id) },
                                ),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            // Gewählte Zeilen bekommen einen Rand in primary statt einer anderen Fläche.
                            border = if (selected) {
                                BorderStroke(Sizing.hairline, MaterialTheme.colorScheme.primary)
                            } else {
                                null
                            },
                        ) {
                            ListRow(
                                headline = exercise.name,
                                supportingText = stringResource(exercise.equipmentType.labelRes()),
                                leadingContent = { Checkbox(checked = selected, onCheckedChange = null) },
                            )
                        }
                    }
                }
            }
            Button(
                onClick = onConfirm,
                enabled = selectedCount > 0,
                modifier = Modifier.fillMaxWidth().padding(Spacing.screenMargin),
            ) {
                Text(
                    if (selectedCount == 0) {
                        stringResource(R.string.plans_picker_select_hint)
                    } else {
                        pluralStringResource(R.plurals.plans_picker_add_count, selectedCount, selectedCount)
                    },
                )
            }
        }
    }
}
