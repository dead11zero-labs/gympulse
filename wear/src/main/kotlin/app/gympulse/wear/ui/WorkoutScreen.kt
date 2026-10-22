package app.gympulse.wear.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.gympulse.core.wearprotocol.ExerciseSnapshot
import app.gympulse.core.wearprotocol.SetSnapshot
import app.gympulse.core.wearprotocol.WearTrackingType
import app.gympulse.wear.R
import app.gympulse.wear.theme.WearSpacing
import kotlinx.coroutines.delay

private const val CROWN_PIXELS_PER_STEP = 40f
private const val REST_STEP_SECONDS = 15

/**
 * Aktive Übung. Drehkrone ändert den gewählten Wert (Gewicht oder Wiederholungen), ein Tipp auf "Fertig"
 * hakt den Satz ab. Der Pausen-Countdown ([restRemainingSeconds]) kommt von außen, damit der Screen zustandslos bleibt.
 */
@Composable
fun WorkoutScreen(
    state: WorkoutUiState,
    restRemainingSeconds: Int?,
    onSelectField: (EditField) -> Unit,
    onRotate: (steps: Int) -> Unit,
    onCompleteSet: () -> Unit,
    onPreviousExercise: () -> Unit,
    onNextExercise: () -> Unit,
    onOpenOverview: () -> Unit,
    onFinish: () -> Unit,
    onAdjustRest: (deltaSeconds: Int) -> Unit,
    onSkipRest: () -> Unit,
    onDismissNotices: () -> Unit,
) {
    val exercise = state.exercise
    val set = state.currentSet
    val haptics = LocalHapticFeedback.current
    val status = statusMessage(state.connection, state.incompatibleProtocol, state.notices)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .crownInput { steps ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRotate(steps)
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = WearSpacing.roundScreenHorizontal, vertical = WearSpacing.roundScreenVertical),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (status != null) {
            StatusBanner(message = status, onDismissNotice = onDismissNotices)
        }
        if (exercise == null || set == null) {
            Text(
                text = stringResource(R.string.workout_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onOpenOverview,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.overview_title), maxLines = 1) },
            )
            return@Column
        }

        ExerciseHeader(
            state = state,
            exercise = exercise,
            onPrevious = onPreviousExercise,
            onNext = onNextExercise,
            onOpenOverview = onOpenOverview,
        )

        val unitWeight = if (exercise.isBodyweight) R.string.workout_unit_kg_added else R.string.workout_unit_kg
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = WearSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(WearSpacing.s),
        ) {
            when (state.trackingType) {
                WearTrackingType.WEIGHT_REPS -> {
                    val weight = formatKg(state.displayWeightKg)
                    ValueTile(
                        value = weight,
                        unit = stringResource(unitWeight),
                        description = stringResource(R.string.cd_weight_value, weight),
                        selected = state.field == EditField.WEIGHT,
                        onClick = { onSelectField(EditField.WEIGHT) },
                        modifier = Modifier.weight(1f),
                    )
                    ValueTile(
                        value = state.displayReps.toString(),
                        unit = stringResource(R.string.workout_unit_reps),
                        description = stringResource(R.string.cd_reps_value, state.displayReps),
                        selected = state.field == EditField.REPS,
                        onClick = { onSelectField(EditField.REPS) },
                        modifier = Modifier.weight(1f),
                    )
                }
                WearTrackingType.REPS -> ValueTile(
                    value = state.displayReps.toString(),
                    unit = stringResource(R.string.workout_unit_reps),
                    description = stringResource(R.string.cd_reps_value, state.displayReps),
                    selected = true,
                    onClick = { onSelectField(EditField.REPS) },
                    modifier = Modifier.weight(1f),
                )
                WearTrackingType.DURATION -> ValueTile(
                    value = formatCountdown(set.durationSeconds ?: 0),
                    unit = stringResource(R.string.workout_unit_time),
                    description = formatCountdown(set.durationSeconds ?: 0),
                    selected = false,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
                WearTrackingType.DISTANCE -> ValueTile(
                    value = formatKg(set.distanceMeters ?: 0.0),
                    unit = stringResource(R.string.workout_unit_meters),
                    description = formatKg(set.distanceMeters ?: 0.0),
                    selected = false,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (restRemainingSeconds != null) {
            RestStrip(
                remainingSeconds = restRemainingSeconds,
                onAdjust = onAdjustRest,
                onSkip = onSkipRest,
            )
        } else {
            previousText(set)?.let { previous ->
                Text(
                    text = previous,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (state.allDone) {
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.workout_finish), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        } else if (set.isCompleted) {
            Button(
                onClick = onCompleteSet,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(),
                label = { Text(stringResource(R.string.workout_undo), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        } else {
            Button(
                onClick = onCompleteSet,
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Default.Check, contentDescription = null) },
                label = { Text(stringResource(R.string.workout_complete), maxLines = 1) },
            )
        }
    }
}

@Composable
private fun ExerciseHeader(
    state: WorkoutUiState,
    exercise: ExerciseSnapshot,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenOverview: () -> Unit,
) {
    val overviewDescription = stringResource(R.string.cd_open_overview, exercise.name)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onPrevious, enabled = state.exerciseIndex > 0) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_previous_exercise))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = WearSpacing.minTouchTarget)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onOpenOverview)
                .semantics { contentDescription = overviewDescription },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (state.exerciseDone) {
                    stringResource(R.string.workout_all_done)
                } else {
                    stringResource(R.string.workout_set_progress, state.setPosition, state.setCount)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onNext, enabled = state.exerciseIndex < state.exercises.lastIndex) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.cd_next_exercise))
        }
    }
}

@Composable
private fun ValueTile(
    value: String,
    unit: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.large
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .heightIn(min = WearSpacing.minTouchTarget + WearSpacing.s)
            .clip(shape)
            .background(container)
            .border(
                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                this.selected = selected
            }
            .padding(vertical = WearSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.numeralSmall,
            color = content,
            maxLines = 1,
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) content else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Countdown mit -15/+15; ein Tipp auf die Zeit überspringt die Pause. */
@Composable
private fun RestStrip(
    remainingSeconds: Int,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
) {
    val time = formatCountdown(remainingSeconds)
    val skipDescription = stringResource(R.string.cd_rest_skip, time)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RestStepButton(
            label = stringResource(R.string.rest_subtract),
            description = stringResource(R.string.cd_rest_subtract),
            onClick = { onAdjust(-REST_STEP_SECONDS) },
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = WearSpacing.minTouchTarget)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onSkip)
                .semantics(mergeDescendants = true) { contentDescription = skipDescription },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
            )
        }
        RestStepButton(
            label = stringResource(R.string.rest_add),
            description = stringResource(R.string.cd_rest_add),
            onClick = { onAdjust(REST_STEP_SECONDS) },
        )
    }
}

@Composable
private fun RestStepButton(label: String, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun previousText(set: SetSnapshot): String? {
    val previous = set.previous ?: return null
    return when {
        previous.weightKg != null && previous.reps != null ->
            stringResource(R.string.workout_previous_weight_reps, formatKg(previous.weightKg!!), previous.reps!!)
        previous.reps != null -> stringResource(R.string.workout_previous_reps, previous.reps!!)
        previous.durationSeconds != null -> stringResource(R.string.workout_previous_time, formatCountdown(previous.durationSeconds!!))
        previous.distanceMeters != null -> stringResource(R.string.workout_previous_distance, formatKg(previous.distanceMeters!!))
        else -> null
    }
}

/**
 * Drehkrone: sammelt die Scroll-Pixel und meldet ganze Schritte (positiv = im Uhrzeigersinn).
 * Das Element holt sich den Fokus, damit Rotary-Events ankommen.
 */
@Composable
private fun Modifier.crownInput(onSteps: (Int) -> Unit): Modifier {
    val focusRequester = remember { FocusRequester() }
    val currentOnSteps by rememberUpdatedState(onSteps)
    var accumulated by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    return this
        .onRotaryScrollEvent { event ->
            accumulated += event.verticalScrollPixels
            val steps = (accumulated / CROWN_PIXELS_PER_STEP).toInt()
            if (steps != 0) {
                accumulated -= steps * CROWN_PIXELS_PER_STEP
                currentOnSteps(steps)
            }
            true
        }
        .focusRequester(focusRequester)
        .focusable()
}

/** Tickt, solange eine Pause läuft; null, wenn keine Pause läuft oder sie abgelaufen ist. */
@Composable
fun rememberRestRemainingSeconds(
    restEndsAt: Long?,
    now: () -> Long = { System.currentTimeMillis() },
): State<Int?> = produceState(initialValue = restRemainingSeconds(restEndsAt, now()), restEndsAt) {
    while (true) {
        value = restRemainingSeconds(restEndsAt, now())
        if (value == null) break
        delay(250)
    }
}
