package app.gympulse.feature.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import app.gympulse.core.designsystem.component.GymPulseCard
import app.gympulse.core.designsystem.component.GymPulseCardVariant
import app.gympulse.core.designsystem.component.SectionHeader
import app.gympulse.core.designsystem.theme.Spacing

private const val DESCRIPTION_MIN_LINES = 2
private const val NAME_MAX_LINES = 2
private const val SETS_DIGITS = 2
private const val REPS_DIGITS = 3
private const val REST_DIGITS = 4

@Composable
internal fun EditorContent(
    state: PlanEditorUiState,
    viewModel: PlanEditorViewModel,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    val validation = remember(draft) { draft.validate() }
    val labels = remember(draft.items) { supersetLabels(draft.items) }
    val showErrors = state.showErrors

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screenMargin,
                end = Spacing.screenMargin,
                top = Spacing.m,
                bottom = FabClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            item(key = "title") {
                Text(
                    text = stringResource(
                        if (state.isNew) R.string.plans_editor_title_new else R.string.plans_editor_title_edit,
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            item(key = "name") {
                val nameError = showErrors && validation.nameBlank
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text(stringResource(R.string.plans_field_name)) },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.plans_field_name_error)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "description") {
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = { Text(stringResource(R.string.plans_field_notes)) },
                    minLines = DESCRIPTION_MIN_LINES,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(
                        title = stringResource(R.string.plans_exercises_header),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = Spacing.xs),
                    )
                    FilledTonalButton(onClick = viewModel::openPicker) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.plans_add_exercises))
                    }
                }
            }
            if (draft.items.isEmpty()) {
                item(key = "empty") {
                    val color = if (showErrors && validation.noExercises) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    GymPulseCard(
                        modifier = Modifier.fillMaxWidth().animateItem(),
                        variant = GymPulseCardVariant.Outlined,
                    ) {
                        Text(
                            text = stringResource(R.string.plans_no_exercises_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = color,
                        )
                    }
                }
            }
            items(draft.items, key = { it.id }) { item ->
                val index = draft.items.indexOfFirst { it.id == item.id }
                val next = draft.items.getOrNull(index + 1)
                ExerciseCard(
                    item = item,
                    supersetLabel = labels[item.id],
                    isFirst = index == 0,
                    isLast = next == null,
                    linkedToNext = item.supersetGroup != null && next?.supersetGroup == item.supersetGroup,
                    errors = if (showErrors) validation.itemErrors[item.id] ?: ItemErrors() else ItemErrors(),
                    onChange = { transform -> viewModel.onUpdateItem(item.id, transform) },
                    onMoveUp = { viewModel.onMove(item.id, -1) },
                    onMoveDown = { viewModel.onMove(item.id, 1) },
                    onRemove = { viewModel.onRemove(item.id) },
                    onToggleLink = { viewModel.onToggleLink(item.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    item: DraftExercise,
    supersetLabel: String?,
    isFirst: Boolean,
    isLast: Boolean,
    linkedToNext: Boolean,
    errors: ItemErrors,
    onChange: ((DraftExercise) -> DraftExercise) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onToggleLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inSuperset = supersetLabel != null
    val invalid = stringResource(R.string.plans_error_invalid)

    GymPulseCard(
        modifier = modifier.fillMaxWidth(),
        variant = if (inSuperset) GymPulseCardVariant.Secondary else GymPulseCardVariant.Filled,
        contentPadding = PaddingValues(Spacing.none),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        val bracketColor = MaterialTheme.colorScheme.primary
        Column {
            Column(
                // Klammer am linken Rand: verbindet die Karten eines Supersatzes optisch.
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        if (inSuperset) {
                            drawRect(color = bracketColor, size = Size(Spacing.xs.toPx(), size.height))
                        }
                    }
                    .padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (supersetLabel != null) {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary) {
                            Text(
                                text = supersetLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
                            )
                        }
                        Spacer(Modifier.width(Spacing.xs))
                    }
                    Text(
                        text = item.name ?: stringResource(R.string.plans_unknown_exercise),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = NAME_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onMoveUp, enabled = !isFirst) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.plans_move_up))
                    }
                    IconButton(onClick = onMoveDown, enabled = !isLast) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.plans_move_down),
                        )
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.plans_remove_exercise))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    PlanNumberField(
                        value = item.sets,
                        onValueChange = { value -> onChange { it.copy(sets = sanitizeDigits(value, SETS_DIGITS)) } },
                        label = stringResource(R.string.plans_field_sets),
                        errorText = invalid.takeIf { errors.sets },
                        modifier = Modifier.weight(1f),
                    )
                    PlanNumberField(
                        value = item.repsMin,
                        onValueChange = { value -> onChange { it.copy(repsMin = sanitizeDigits(value, REPS_DIGITS)) } },
                        label = stringResource(R.string.plans_field_reps_min),
                        errorText = null,
                        isError = errors.reps,
                        modifier = Modifier.weight(1f),
                    )
                    PlanNumberField(
                        value = item.repsMax,
                        onValueChange = { value -> onChange { it.copy(repsMax = sanitizeDigits(value, REPS_DIGITS)) } },
                        label = stringResource(R.string.plans_field_reps_max),
                        errorText = invalid.takeIf { errors.reps },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    PlanNumberField(
                        value = item.weight,
                        onValueChange = { value -> onChange { it.copy(weight = sanitizeDecimal(value)) } },
                        label = stringResource(R.string.plans_field_weight),
                        errorText = invalid.takeIf { errors.weight },
                        decimal = true,
                        modifier = Modifier.weight(1f),
                    )
                    PlanNumberField(
                        value = item.rest,
                        onValueChange = { value -> onChange { it.copy(rest = sanitizeDigits(value, REST_DIGITS)) } },
                        label = stringResource(R.string.plans_field_rest),
                        errorText = invalid.takeIf { errors.rest },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!isLast) {
                    TextButton(onClick = onToggleLink) {
                        Text(
                            stringResource(
                                if (linkedToNext) R.string.plans_superset_unlink else R.string.plans_superset_link,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorText: String?,
    modifier: Modifier = Modifier,
    isError: Boolean = errorText != null,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        isError = isError,
        supportingText = errorText?.let { message -> { Text(message) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}
