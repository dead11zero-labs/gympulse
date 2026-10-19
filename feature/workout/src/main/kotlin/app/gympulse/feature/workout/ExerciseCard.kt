package app.gympulse.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.SetType

// Relative column widths of the set table.
private const val WEIGHT_SET = 0.6f
private const val WEIGHT_PREVIOUS = 1.2f
private const val WEIGHT_FIELD = 1f
private const val WEIGHT_DONE = 0.8f

@Composable
internal fun ExerciseCard(
    exercise: ExerciseUi,
    actions: WorkoutActions,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showNotes by rememberSaveable(exercise.sessionExerciseId) { mutableStateOf(exercise.notes != null) }
    val fields = setFieldsFor(exercise.trackingType, exercise.isBodyweight)

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.m, end = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.feature_workout_exercise_options),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (!showNotes) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.feature_workout_add_note)) },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    showNotes = true
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.feature_workout_remove_exercise)) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                actions.onRemoveExercise(exercise.sessionExerciseId)
                            },
                        )
                    }
                }
            }

            if (showNotes) {
                NotesField(
                    notes = exercise.notes,
                    onNotesChange = { actions.onNotesChange(exercise.sessionExerciseId, it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.m, vertical = Spacing.xxs),
                )
            }

            SetTableHeader(fields = fields)
            exercise.sets.forEach { set ->
                key(set.id) {
                    SetRow(
                        sessionExerciseId = exercise.sessionExerciseId,
                        set = set,
                        fields = fields,
                        actions = actions,
                    )
                }
            }

            TextButton(
                onClick = { actions.onAddSet(exercise.sessionExerciseId) },
                modifier = Modifier.padding(horizontal = Spacing.xs),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.feature_workout_add_set),
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun NotesField(notes: String?, onNotesChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf(notes.orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onNotesChange(it)
        },
        label = { Text(stringResource(R.string.feature_workout_note)) },
        modifier = modifier,
    )
}

@Composable
private fun SetTableHeader(fields: List<SetField>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell(stringResource(R.string.feature_workout_column_set), Modifier.weight(WEIGHT_SET))
        HeaderCell(stringResource(R.string.feature_workout_column_previous), Modifier.weight(WEIGHT_PREVIOUS))
        fields.forEach { field ->
            HeaderCell(stringResource(field.labelRes()), Modifier.weight(WEIGHT_FIELD))
        }
        Box(modifier = Modifier.weight(WEIGHT_DONE), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun SetRow(
    sessionExerciseId: String,
    set: SetUi,
    fields: List<SetField>,
    actions: WorkoutActions,
) {
    val isWarmup = set.setType == SetType.WARMUP
    val rowColor = if (set.isCompleted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val label = if (isWarmup) {
        stringResource(R.string.feature_workout_warmup_short) + set.number
    } else {
        set.number.toString()
    }
    val doneDescription = stringResource(R.string.feature_workout_set_done)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs)
            .background(color = rowColor, shape = MaterialTheme.shapes.small)
            .padding(vertical = Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetLabel(
            label = label,
            isWarmup = isWarmup,
            onToggleWarmup = { actions.onToggleWarmup(sessionExerciseId, set.id) },
            onDelete = { actions.onDeleteSet(set.id) },
            modifier = Modifier.weight(WEIGHT_SET),
        )
        Text(
            text = previousText(set.previous, fields),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(WEIGHT_PREVIOUS),
        )
        fields.forEach { field ->
            val fieldModifier = Modifier.weight(WEIGHT_FIELD)
            val description = stringResource(field.labelRes())
            when (field) {
                SetField.WEIGHT, SetField.EXTRA_WEIGHT -> SetInputField(
                    storedValue = WorkoutFormat.formatDecimalInput(set.weightKg),
                    onValueChange = { actions.onWeightChange(sessionExerciseId, set.id, it) },
                    sanitize = WorkoutFormat::sanitizeDecimal,
                    keyboardType = KeyboardType.Decimal,
                    contentDescription = description,
                    modifier = fieldModifier,
                )
                SetField.REPS -> SetInputField(
                    storedValue = WorkoutFormat.formatIntegerInput(set.reps),
                    onValueChange = { actions.onRepsChange(sessionExerciseId, set.id, it) },
                    sanitize = WorkoutFormat::sanitizeInteger,
                    keyboardType = KeyboardType.Number,
                    contentDescription = description,
                    modifier = fieldModifier,
                )
                SetField.DURATION -> SetInputField(
                    storedValue = WorkoutFormat.formatIntegerInput(set.durationSeconds),
                    onValueChange = { actions.onDurationChange(sessionExerciseId, set.id, it) },
                    sanitize = WorkoutFormat::sanitizeInteger,
                    keyboardType = KeyboardType.Number,
                    contentDescription = description,
                    modifier = fieldModifier,
                )
                SetField.DISTANCE -> SetInputField(
                    storedValue = WorkoutFormat.formatDecimalInput(set.distanceMeters),
                    onValueChange = { actions.onDistanceChange(sessionExerciseId, set.id, it) },
                    sanitize = WorkoutFormat::sanitizeDecimal,
                    keyboardType = KeyboardType.Decimal,
                    contentDescription = description,
                    modifier = fieldModifier,
                )
            }
        }
        Box(modifier = Modifier.weight(WEIGHT_DONE), contentAlignment = Alignment.Center) {
            Checkbox(
                checked = set.isCompleted,
                onCheckedChange = { actions.onToggleCompleted(sessionExerciseId, set.id) },
                modifier = Modifier.semantics { contentDescription = doneDescription },
            )
        }
    }
}

/** Set number; tapping opens a menu to switch warm-up / working set or delete the set. */
@Composable
private fun SetLabel(
    label: String,
    isWarmup: Boolean,
    onToggleWarmup: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val optionsLabel = stringResource(R.string.feature_workout_set_options, label)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = optionsLabel) { menuOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (isWarmup) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (isWarmup) R.string.feature_workout_mark_working else R.string.feature_workout_mark_warmup,
                        ),
                    )
                },
                onClick = {
                    menuOpen = false
                    onToggleWarmup()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_workout_delete_set)) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

/**
 * Compact outlined number field. Keeps the typed text locally (e.g. "62," while typing) and
 * only takes over the stored value when it differs numerically, e.g. after another device wrote.
 */
@Composable
private fun SetInputField(
    storedValue: String,
    onValueChange: (String) -> Unit,
    sanitize: (String) -> String?,
    keyboardType: KeyboardType,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf(storedValue) }
    LaunchedEffect(storedValue) {
        if (WorkoutFormat.parseDecimal(text) != WorkoutFormat.parseDecimal(storedValue)) {
            text = storedValue
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()
    BasicTextField(
        value = text,
        onValueChange = { input ->
            val sanitized = sanitize(input) ?: return@BasicTextField
            text = sanitized
            onValueChange(sanitized)
        },
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        singleLine = true,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = text,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                colors = colors,
                contentPadding = OutlinedTextFieldDefaults.contentPadding(
                    start = Spacing.xxs,
                    top = Spacing.xs,
                    end = Spacing.xxs,
                    bottom = Spacing.xs,
                ),
            )
        },
    )
}

@Composable
private fun previousText(previous: SetEntry?, fields: List<SetField>): String {
    val none = stringResource(R.string.feature_workout_previous_none)
    if (previous == null) return none
    val primary = fields.first()
    return when (primary) {
        SetField.WEIGHT -> {
            val reps = previous.reps
            val weight = previous.weightKg
            when {
                reps != null && weight != null ->
                    stringResource(R.string.feature_workout_previous_weight_reps, WorkoutFormat.formatDecimalInput(weight), reps)
                reps != null -> stringResource(R.string.feature_workout_previous_reps, reps)
                else -> none
            }
        }
        SetField.EXTRA_WEIGHT, SetField.REPS -> {
            val reps = previous.reps ?: return none
            val extra = previous.weightKg
            if (primary == SetField.EXTRA_WEIGHT && extra != null && extra > 0.0) {
                stringResource(R.string.feature_workout_previous_extra_weight_reps, WorkoutFormat.formatDecimalInput(extra), reps)
            } else {
                stringResource(R.string.feature_workout_previous_reps, reps)
            }
        }
        SetField.DURATION -> previous.durationSeconds?.let {
            stringResource(R.string.feature_workout_previous_duration, it)
        } ?: none
        SetField.DISTANCE -> previous.distanceMeters?.let {
            stringResource(R.string.feature_workout_previous_distance, WorkoutFormat.formatDecimalInput(it))
        } ?: none
    }
}

private fun SetField.labelRes(): Int = when (this) {
    SetField.WEIGHT -> R.string.feature_workout_column_kg
    SetField.EXTRA_WEIGHT -> R.string.feature_workout_column_extra_kg
    SetField.REPS -> R.string.feature_workout_column_reps
    SetField.DURATION -> R.string.feature_workout_column_duration
    SetField.DISTANCE -> R.string.feature_workout_column_distance
}
