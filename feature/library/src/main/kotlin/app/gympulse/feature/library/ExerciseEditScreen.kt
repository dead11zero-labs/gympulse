package app.gympulse.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.data.ui.labelRes
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MuscleGroup
import app.gympulse.core.model.TrackingType

private const val INSTRUCTIONS_MIN_LINES = 4
private const val NOTES_MIN_LINES = 2

@Composable
internal fun ExerciseEditScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    when {
        state.isLoading -> LoadingIndicator(modifier)
        state.notFound -> EmptyState(
            title = stringResource(R.string.library_exercise_not_found),
            message = stringResource(R.string.library_exercise_not_found_message),
            modifier = modifier,
            actionLabel = stringResource(R.string.library_back),
            onAction = onDone,
        )
        else -> ExerciseForm(state = state, viewModel = viewModel, modifier = modifier)
    }
}

@Composable
private fun ExerciseForm(
    state: ExerciseFormState,
    viewModel: ExerciseEditViewModel,
    modifier: Modifier = Modifier,
) {
    FormColumn(modifier = modifier) {
        Text(
            text = stringResource(if (state.isNew) R.string.library_new_exercise else R.string.library_edit_exercise),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (state.isCustom) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.library_field_name)) },
                isError = state.showErrors && state.nameInvalid,
                supportingText = {
                    if (state.showErrors && state.nameInvalid) {
                        Text(stringResource(R.string.library_error_name_required))
                    }
                },
            )
            ChipGroup(
                title = stringResource(R.string.library_label_category),
                options = ExerciseCategory.entries,
                isSelected = { it == state.category },
                label = { stringResource(it.categoryLabelRes()) },
                onClick = viewModel::setCategory,
            )
            ChipGroup(
                title = stringResource(R.string.library_label_equipment_type),
                options = EquipmentType.entries,
                isSelected = { it == state.equipmentType },
                label = { stringResource(it.labelRes()) },
                onClick = viewModel::setEquipmentType,
            )
            ChipGroup(
                title = stringResource(R.string.library_label_tracking),
                options = TrackingType.entries,
                isSelected = { it == state.trackingType },
                label = { stringResource(it.trackingLabelRes()) },
                onClick = viewModel::setTrackingType,
            )
            if (state.equipmentType == EquipmentType.BODYWEIGHT) {
                OutlinedTextField(
                    value = state.bodyweightFactor,
                    onValueChange = viewModel::setBodyweightFactor,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text(stringResource(R.string.library_label_bodyweight_factor)) },
                    isError = state.showErrors && state.bodyweightFactorInvalid,
                    supportingText = {
                        Text(
                            stringResource(
                                if (state.showErrors && state.bodyweightFactorInvalid) {
                                    R.string.library_error_bodyweight_factor
                                } else {
                                    R.string.library_hint_bodyweight_factor
                                },
                            ),
                        )
                    },
                )
            }
            ChipGroup(
                title = stringResource(R.string.library_label_primary_muscles),
                options = MuscleGroup.entries,
                isSelected = { it in state.primaryMuscles },
                label = { stringResource(it.labelRes()) },
                onClick = viewModel::togglePrimary,
            )
            ChipGroup(
                title = stringResource(R.string.library_label_secondary_muscles),
                options = MuscleGroup.entries,
                isSelected = { it in state.secondaryMuscles },
                label = { stringResource(it.labelRes()) },
                onClick = viewModel::toggleSecondary,
            )
            OutlinedTextField(
                value = state.instructions,
                onValueChange = viewModel::setInstructions,
                modifier = Modifier.fillMaxWidth(),
                minLines = INSTRUCTIONS_MIN_LINES,
                label = { Text(stringResource(R.string.library_field_instructions)) },
                supportingText = { Text(stringResource(R.string.library_hint_instructions)) },
            )
        } else {
            Text(
                text = stringResource(R.string.library_builtin_hint, state.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = state.notes,
            onValueChange = viewModel::setNotes,
            modifier = Modifier.fillMaxWidth(),
            minLines = NOTES_MIN_LINES,
            label = { Text(stringResource(R.string.library_notes)) },
        )
        OutlinedTextField(
            value = state.defaultRestSeconds,
            onValueChange = viewModel::setDefaultRestSeconds,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text(stringResource(R.string.library_label_rest)) },
            isError = state.showErrors && state.restInvalid,
            supportingText = {
                Text(
                    stringResource(
                        if (state.showErrors && state.restInvalid) R.string.library_error_rest else R.string.library_hint_rest,
                    ),
                )
            },
        )

        Button(
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.library_save))
        }
    }
}

/** Eine Gruppe von Filter-Chips, die umbrechen; Anzeige und Auswahl liefert der Aufrufer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(
    title: String,
    options: List<T>,
    isSelected: (T) -> Boolean,
    label: @Composable (T) -> String,
    onClick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionTitle(title)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = isSelected(option),
                    onClick = { onClick(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}
