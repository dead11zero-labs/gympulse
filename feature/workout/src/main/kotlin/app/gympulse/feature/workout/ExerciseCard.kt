package app.gympulse.feature.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.gympulse.core.designsystem.component.GymPulseCard
import app.gympulse.core.designsystem.component.GymPulseCardVariant
import app.gympulse.core.designsystem.component.LabelCaps
import app.gympulse.core.designsystem.component.NumberStepper
import app.gympulse.core.designsystem.component.SetCheckbox
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.setValue as setValueStyle
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.SetType
import androidx.compose.animation.animateContentSize
import app.gympulse.core.designsystem.theme.ExpressiveShapes

// Relative column widths of the set table.
private const val WEIGHT_SET = 0.6f
private const val WEIGHT_PREVIOUS = 1.2f
private const val WEIGHT_FIELD = 1f
private const val WEIGHT_DONE = 0.8f

/**
 * Exercise block with its set table. The active exercise (the first one with an open set) sits on a
 * level-2 card and shows the stepper panel under its current set; all others use level 1.
 */
@Composable
internal fun ExerciseCard(
    exercise: ExerciseUi,
    actions: WorkoutActions,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showNotes by rememberSaveable(exercise.sessionExerciseId) { mutableStateOf(exercise.notes != null) }
    val fields = setFieldsFor(exercise.trackingType, exercise.isBodyweight)
    // Only one weight slider is open at a time within a card; null means all are collapsed.
    var expandedWeightSetId by rememberSaveable(exercise.sessionExerciseId) { mutableStateOf<String?>(null) }
    val activeSetId = if (isActive) exercise.sets.firstOrNull { !it.isCompleted }?.id else null

    GymPulseCard(
        modifier = modifier.animateContentSize(),
        variant = if (isActive) GymPulseCardVariant.Level2 else GymPulseCardVariant.Level1,
        shape = ExpressiveShapes.largeIncreased,
        contentPadding = PaddingValues(vertical = Spacing.xs),
        verticalSpacing = Spacing.none,
    ) {
        run {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.m, end = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleLarge,
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
                        isActive = set.id == activeSetId,
                        weightExpanded = expandedWeightSetId == set.id,
                        onWeightExpandedChange = { expand ->
                            expandedWeightSetId = if (expand) set.id else null
                        },
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
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        LabelCaps(text = text)
    }
}

@Composable
private fun SetRow(
    sessionExerciseId: String,
    set: SetUi,
    fields: List<SetField>,
    actions: WorkoutActions,
    isActive: Boolean,
    weightExpanded: Boolean,
    onWeightExpandedChange: (Boolean) -> Unit,
) {
    val isWarmup = set.setType == SetType.WARMUP
    val label = if (isWarmup) {
        stringResource(R.string.feature_workout_warmup_short) + set.number
    } else {
        set.number.toString()
    }
    val doneDescription = stringResource(R.string.feature_workout_set_done)
    val scheme = MaterialTheme.colorScheme
    val activeModifier = if (isActive) {
        Modifier
            .background(color = scheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium)
            .border(
                border = BorderStroke(Sizing.hairline, scheme.primary.copy(alpha = ACTIVE_BORDER_ALPHA)),
                shape = MaterialTheme.shapes.medium,
            )
            .padding(Spacing.xs)
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs)
            .then(activeModifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SetLabel(
                label = label,
                isWarmup = isWarmup,
                isActive = isActive,
                onToggleWarmup = { actions.onToggleWarmup(sessionExerciseId, set.id) },
                onDelete = { actions.onDeleteSet(set.id) },
                modifier = Modifier.weight(WEIGHT_SET),
            )
            Text(
                text = previousText(set.previous, fields),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(WEIGHT_PREVIOUS),
            )
            fields.forEachIndexed { index, field ->
                val fieldModifier = Modifier.weight(WEIGHT_FIELD).heightIn(min = Sizing.minTouchTarget)
                // The first field of the active set carries the accent (weight in the mockup).
                val highlighted = isActive && index == 0
                val description = stringResource(field.labelRes())
                when (field) {
                    SetField.WEIGHT, SetField.EXTRA_WEIGHT -> WeightCell(
                        storedValue = WorkoutFormat.formatDecimalInput(set.weightKg),
                        onValueChange = { actions.onWeightChange(sessionExerciseId, set.id, it) },
                        expanded = weightExpanded,
                        onExpandedChange = onWeightExpandedChange,
                        contentDescription = description,
                        highlighted = highlighted,
                        modifier = fieldModifier,
                    )
                    SetField.REPS -> SetInputField(
                        storedValue = WorkoutFormat.formatIntegerInput(set.reps),
                        onValueChange = { actions.onRepsChange(sessionExerciseId, set.id, it) },
                        sanitize = WorkoutFormat::sanitizeInteger,
                        keyboardType = KeyboardType.Number,
                        contentDescription = description,
                        highlighted = highlighted,
                        modifier = fieldModifier,
                    )
                    SetField.DURATION -> SetInputField(
                        storedValue = WorkoutFormat.formatIntegerInput(set.durationSeconds),
                        onValueChange = { actions.onDurationChange(sessionExerciseId, set.id, it) },
                        sanitize = WorkoutFormat::sanitizeInteger,
                        keyboardType = KeyboardType.Number,
                        contentDescription = description,
                        highlighted = highlighted,
                        modifier = fieldModifier,
                    )
                    SetField.DISTANCE -> SetInputField(
                        storedValue = WorkoutFormat.formatDecimalInput(set.distanceMeters),
                        onValueChange = { actions.onDistanceChange(sessionExerciseId, set.id, it) },
                        sanitize = WorkoutFormat::sanitizeDecimal,
                        keyboardType = KeyboardType.Decimal,
                        contentDescription = description,
                        highlighted = highlighted,
                        modifier = fieldModifier,
                    )
                }
            }
            Box(modifier = Modifier.weight(WEIGHT_DONE), contentAlignment = Alignment.Center) {
                SetCheckbox(
                    checked = set.isCompleted,
                    onCheckedChange = { actions.onToggleCompleted(sessionExerciseId, set.id) },
                    contentDescription = doneDescription,
                )
            }
        }
        if (weightExpanded) {
            WeightSlider(
                weightKg = set.weightKg,
                onValueChange = { actions.onWeightChange(sessionExerciseId, set.id, it) },
                onClose = { onWeightExpandedChange(false) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.s, end = Spacing.xxs),
            )
        }
        if (isActive) {
            ActiveSetPanel(sessionExerciseId = sessionExerciseId, set = set, fields = fields, actions = actions)
        }
    }
}

/**
 * Weight cell of a set. Collapsed it shows the weight compactly; tapping it opens the slider
 * below the row. While the slider is open, tapping the number switches to keyboard input, and
 * finishing the input returns to the plain number display.
 */
@Composable
private fun WeightCell(
    storedValue: String,
    onValueChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    contentDescription: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    var typing by rememberSaveable { mutableStateOf(false) }
    // Collapsing the slider (or opening another one) always ends keyboard input.
    LaunchedEffect(expanded) { if (!expanded) typing = false }

    if (expanded && typing) {
        SetInputField(
            storedValue = storedValue,
            onValueChange = onValueChange,
            sanitize = WorkoutFormat::sanitizeDecimal,
            keyboardType = KeyboardType.Decimal,
            contentDescription = contentDescription,
            highlighted = highlighted,
            modifier = modifier,
            requestFocus = true,
            imeAction = ImeAction.Done,
            onFocusLost = { typing = false },
        )
    } else {
        val clickLabel = stringResource(
            if (expanded) R.string.feature_workout_weight_edit else R.string.feature_workout_weight_adjust,
        )
        val scheme = MaterialTheme.colorScheme
        val shape = MaterialTheme.shapes.small
        Box(
            modifier = modifier
                .clip(shape)
                .background(color = scheme.surfaceContainerHigh, shape = shape)
                .border(
                    width = if (highlighted) Sizing.setCheckboxBorder else Sizing.hairline,
                    color = if (highlighted) scheme.primary else scheme.outlineVariant,
                    shape = shape,
                )
                .clickable(onClickLabel = clickLabel, role = Role.Button) {
                    if (expanded) typing = true else onExpandedChange(true)
                }
                .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
                .padding(horizontal = Spacing.xxs),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = storedValue.ifEmpty { stringResource(R.string.feature_workout_weight_empty) },
                style = MaterialTheme.typography.setValueStyle,
                color = if (highlighted) scheme.primary else scheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** Slider for the weight in kg, snapped to [WorkoutFormat.WEIGHT_SLIDER_STEP_KG], plus a close button. */
@Composable
private fun WeightSlider(
    weightKg: Double?,
    onValueChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = weightKg ?: 0.0
    val sliderDescription = stringResource(R.string.feature_workout_weight_slider)
    val max = WorkoutFormat.sliderMax(current).toFloat()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = current.toFloat().coerceIn(0f, max),
            onValueChange = { raw ->
                val snapped = WorkoutFormat.snapWeight(raw.toDouble())
                if (snapped != weightKg) onValueChange(WorkoutFormat.formatDecimalInput(snapped))
            },
            valueRange = 0f..max,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Sizing.minTouchTarget)
                .semantics { this.contentDescription = sliderDescription },
        )
        IconButton(onClick = onClose) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.feature_workout_weight_close),
            )
        }
    }
}

/**
 * Panel under the current set: weight and reps steppers (hold to repeat faster), plate shortcuts
 * and a "Done" button. Only shown for tracking types that have weight or reps.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveSetPanel(
    sessionExerciseId: String,
    set: SetUi,
    fields: List<SetField>,
    actions: WorkoutActions,
) {
    val weightField = fields.firstOrNull { it == SetField.WEIGHT || it == SetField.EXTRA_WEIGHT }
    val hasReps = SetField.REPS in fields
    if (weightField == null && !hasReps) return
    val weight = rememberOptimistic(set.weightKg, set.id)
    val reps = rememberOptimistic(set.reps, set.id)
    val changeWeight = { delta: Double ->
        val next = WorkoutFormat.adjustDecimal(weight.value, delta)
        weight.onStep(next)
        actions.onWeightChange(sessionExerciseId, set.id, WorkoutFormat.formatDecimalInput(next))
    }
    val changeReps = { delta: Int ->
        val next = WorkoutFormat.adjustInteger(reps.value, delta)
        reps.onStep(next)
        actions.onRepsChange(sessionExerciseId, set.id, WorkoutFormat.formatIntegerInput(next))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs)
            .background(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium)
            .padding(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (weightField != null) {
                NumberStepper(
                    value = WorkoutFormat.formatDecimalInput(weight.value ?: 0.0),
                    unit = stringResource(R.string.feature_workout_column_kg),
                    label = stringResource(
                        if (weightField == SetField.EXTRA_WEIGHT) {
                            R.string.feature_workout_stepper_extra_weight
                        } else {
                            R.string.feature_workout_stepper_weight
                        },
                    ),
                    onDecrement = { changeWeight(-WEIGHT_STEP_KG) },
                    onIncrement = { changeWeight(WEIGHT_STEP_KG) },
                    decrementEnabled = (weight.value ?: 0.0) > 0.0,
                )
            }
            if (hasReps) {
                NumberStepper(
                    value = WorkoutFormat.formatIntegerInput(reps.value ?: 0),
                    label = stringResource(R.string.feature_workout_stepper_reps),
                    onDecrement = { changeReps(-1) },
                    onIncrement = { changeReps(1) },
                    decrementEnabled = (reps.value ?: 0) > 0,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (weightField != null) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                LabelCaps(text = stringResource(R.string.feature_workout_plates))
                PLATES_KG.forEach { plate ->
                    AssistChip(
                        onClick = { changeWeight(plate) },
                        label = {
                            Text(
                                stringResource(R.string.feature_workout_plate_chip, WorkoutFormat.formatDecimalInput(plate)),
                            )
                        },
                    )
                }
            }
        }
        Button(
            onClick = { actions.onToggleCompleted(sessionExerciseId, set.id) },
            modifier = Modifier.align(Alignment.End),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Text(
                text = stringResource(R.string.feature_workout_set_finish),
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
}

/**
 * Value shown while stepping. Stored values echo back with a delay, so rapid taps would otherwise
 * start again from a stale number; echoes of our own steps are ignored, foreign changes win.
 */
@Stable
internal class OptimisticValue<T>(initial: T) {
    var value: T by mutableStateOf(initial)
        private set
    private val sent = ArrayDeque<T>()

    fun onStep(next: T) {
        value = next
        sent.addLast(next)
    }

    fun onStored(stored: T) {
        val index = sent.indexOf(stored)
        if (index >= 0) {
            repeat(index + 1) { sent.removeFirst() }
        } else {
            sent.clear()
            value = stored
        }
    }
}

@Composable
private fun <T> rememberOptimistic(stored: T, key: Any): OptimisticValue<T> {
    val state = remember(key) { OptimisticValue(stored) }
    LaunchedEffect(stored) { state.onStored(stored) }
    return state
}

private const val WEIGHT_STEP_KG = 2.5
private const val ACTIVE_BORDER_ALPHA = 0.4f
private val PLATES_KG = listOf(1.25, 2.5, 5.0)

/** Set number; tapping opens a menu to switch warm-up / working set or delete the set. */
@Composable
private fun SetLabel(
    label: String,
    isWarmup: Boolean,
    isActive: Boolean,
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
                color = when {
                    isActive -> MaterialTheme.colorScheme.primary
                    isWarmup -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurface
                },
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
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onFocusLost: (() -> Unit)? = null,
) {
    var text by rememberSaveable { mutableStateOf(storedValue) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var hadFocus by remember { mutableStateOf(false) }
    if (requestFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
    LaunchedEffect(storedValue) {
        if (WorkoutFormat.parseDecimal(text) != WorkoutFormat.parseDecimal(storedValue)) {
            text = storedValue
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    BasicTextField(
        value = text,
        onValueChange = { input ->
            val sanitized = sanitize(input) ?: return@BasicTextField
            text = sanitized
            onValueChange(sanitized)
        },
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    hadFocus = true
                } else if (hadFocus) {
                    hadFocus = false
                    onFocusLost?.invoke()
                }
            }
            .semantics { this.contentDescription = contentDescription },
        textStyle = MaterialTheme.typography.setValueStyle.copy(
            color = if (highlighted) scheme.primary else scheme.onSurface,
            textAlign = TextAlign.Center,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        singleLine = true,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            val shape = MaterialTheme.shapes.small
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizing.minTouchTarget)
                    .background(color = scheme.surfaceContainerHigh, shape = shape)
                    .border(
                        width = if (highlighted) Sizing.setCheckboxBorder else Sizing.hairline,
                        color = if (highlighted) scheme.primary else scheme.outlineVariant,
                        shape = shape,
                    )
                    .padding(horizontal = Spacing.xxs),
                contentAlignment = Alignment.Center,
            ) {
                innerTextField()
            }
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
