package app.gympulse.feature.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import app.gympulse.core.data.ui.labelRes
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
                )
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.plans_picker_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.plans_picker_clear),
                            )
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin),
            )
            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(R.string.plans_picker_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(Spacing.l),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Top,
                ) {
                    items(filtered, key = { it.id }) { exercise ->
                        val selected = exercise.id in state.selectedIds
                        ListItem(
                            headlineContent = { Text(exercise.name) },
                            supportingContent = { Text(stringResource(exercise.equipmentType.labelRes())) },
                            leadingContent = { Checkbox(checked = selected, onCheckedChange = null) },
                            modifier = Modifier.toggleable(
                                value = selected,
                                role = Role.Checkbox,
                                onValueChange = { onToggle(exercise.id) },
                            ),
                        )
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
