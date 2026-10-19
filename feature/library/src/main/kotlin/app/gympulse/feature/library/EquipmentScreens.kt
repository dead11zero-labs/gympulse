package app.gympulse.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.data.ui.labelRes
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.model.EquipmentTemplate

private const val PHOTO_ASPECT_RATIO = 16f / 9f
private const val NOTES_MIN_LINES = 2

// ------------------------------------------------------------ Vorlagenauswahl

/** Auswahl einer Gerätevorlage oder "Leeres Gerät" als Ausgangspunkt für ein neues Gerät. */
@Composable
internal fun EquipmentTemplatesScreen(
    onPickTemplate: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EquipmentTemplatesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    ContentColumn(modifier = modifier) {
        Text(
            text = stringResource(R.string.library_new_equipment),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Spacing.screenMargin, vertical = Spacing.xs),
        )
        SearchField(
            value = query,
            onValueChange = viewModel::setQuery,
            label = stringResource(R.string.library_search_templates),
            modifier = Modifier.padding(horizontal = Spacing.screenMargin, vertical = Spacing.xs),
        )
        when {
            state.isLoading -> LoadingIndicator(modifier = Modifier.weight(1f))
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = Spacing.l),
            ) {
                item(key = "empty") {
                    ListItem(
                        modifier = Modifier.clickable { onPickTemplate(null) },
                        leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.library_empty_equipment)) },
                        supportingContent = { Text(stringResource(R.string.library_empty_equipment_hint)) },
                    )
                }
                if (state.templates.isEmpty()) {
                    item(key = "no-match") {
                        Text(
                            text = stringResource(R.string.library_templates_no_match),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.screenMargin),
                        )
                    }
                }
                items(items = state.templates, key = { it.key }) { template ->
                    TemplateRow(template = template, onClick = { onPickTemplate(template.key) })
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(template: EquipmentTemplate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val type = stringResource(template.equipmentType.labelRes())
    val settings = template.settingKeys.joinToString(", ") { it.name }
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = { Text(template.name) },
        supportingContent = {
            Text(
                text = if (settings.isEmpty()) type else "$type · $settings",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

// ------------------------------------------------------------ Gerät bearbeiten

@Composable
internal fun EquipmentEditScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EquipmentEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }

    when {
        state.isLoading -> LoadingIndicator(modifier)
        state.notFound -> EmptyState(
            title = stringResource(R.string.library_equipment_not_found),
            message = stringResource(R.string.library_equipment_not_found_message),
            modifier = modifier,
            actionLabel = stringResource(R.string.library_back),
            onAction = onDone,
        )
        else -> EquipmentForm(state = state, viewModel = viewModel, modifier = modifier)
    }
}

@Composable
private fun EquipmentForm(
    state: EquipmentFormState,
    viewModel: EquipmentEditViewModel,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    FormColumn(modifier = modifier) {
        Text(
            text = stringResource(if (state.isNew) R.string.library_new_equipment else R.string.library_edit_equipment),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        PhotoPlaceholder()

        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::setName,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.library_field_equipment_name)) },
            isError = state.showErrors && state.nameInvalid,
            supportingText = {
                if (state.showErrors && state.nameInvalid) {
                    Text(stringResource(R.string.library_error_name_required))
                }
            },
        )
        OutlinedTextField(
            value = state.location,
            onValueChange = viewModel::setLocation,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.library_field_location)) },
        )

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionTitle(stringResource(R.string.library_settings))
            Text(
                text = stringResource(R.string.library_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.settings.forEach { row ->
            key(row.rowId) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = row.key,
                        onValueChange = { viewModel.setSettingKey(row.rowId, it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text(stringResource(R.string.library_field_setting_name)) },
                    )
                    OutlinedTextField(
                        value = row.value,
                        onValueChange = { viewModel.setSettingValue(row.rowId, it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text(stringResource(R.string.library_field_setting_value)) },
                        placeholder = { if (row.hint.isNotEmpty()) Text(row.hint) },
                    )
                    IconButton(onClick = { viewModel.removeSetting(row.rowId) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.library_remove_setting),
                        )
                    }
                }
            }
        }
        OutlinedButton(onClick = viewModel::addSetting) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(stringResource(R.string.library_add_setting))
        }

        OutlinedTextField(
            value = state.notes,
            onValueChange = viewModel::setNotes,
            modifier = Modifier.fillMaxWidth(),
            minLines = NOTES_MIN_LINES,
            label = { Text(stringResource(R.string.library_notes)) },
        )

        if (!state.isNew) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SectionTitle(stringResource(R.string.library_linked_exercises))
                if (state.linkedExercises.isEmpty()) {
                    Text(
                        text = stringResource(R.string.library_linked_exercises_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.linkedExercises.forEach { exercise ->
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        Button(
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.library_save))
        }
        if (!state.isNew) {
            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.library_delete_equipment))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.library_delete_equipment_title)) },
            text = { Text(stringResource(R.string.library_delete_equipment_message, state.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) { Text(stringResource(R.string.library_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.library_cancel)) }
            },
        )
    }
}

/** Platzhalter für ein späteres Gerätefoto. */
@Composable
private fun PhotoPlaceholder(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(PHOTO_ASPECT_RATIO),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.library_photo_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
