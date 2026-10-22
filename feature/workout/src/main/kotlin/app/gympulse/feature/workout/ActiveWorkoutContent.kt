package app.gympulse.feature.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.gympulse.core.designsystem.adaptive.WindowWidthClass
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.feature.workout.timer.RestTimerState
import app.gympulse.feature.workout.timer.runningAt
import java.time.Duration
import java.time.Instant
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.gympulse.core.designsystem.component.EmptyState
import app.gympulse.core.designsystem.component.GymPulseCard
import app.gympulse.core.designsystem.component.GymPulseCardVariant
import app.gympulse.core.designsystem.component.GymPulseProgressBar
import app.gympulse.core.designsystem.component.LabelCaps
import app.gympulse.core.designsystem.component.ProgressMath
import app.gympulse.core.designsystem.theme.metricMedium

/** Callbacks of the active workout, grouped to keep the composable signatures short. */
internal data class WorkoutActions(
    val onWeightChange: (sessionExerciseId: String, setId: String, text: String) -> Unit,
    val onRepsChange: (sessionExerciseId: String, setId: String, text: String) -> Unit,
    val onDurationChange: (sessionExerciseId: String, setId: String, text: String) -> Unit,
    val onDistanceChange: (sessionExerciseId: String, setId: String, text: String) -> Unit,
    val onToggleCompleted: (sessionExerciseId: String, setId: String) -> Unit,
    val onToggleWarmup: (sessionExerciseId: String, setId: String) -> Unit,
    val onAddSet: (sessionExerciseId: String) -> Unit,
    val onDeleteSet: (setId: String) -> Unit,
    val onAddExercise: (exerciseId: String) -> Unit,
    val onRemoveExercise: (sessionExerciseId: String) -> Unit,
    val onNotesChange: (sessionExerciseId: String, notes: String) -> Unit,
    val onSkipRest: () -> Unit,
    val onAdjustRest: (deltaSeconds: Int) -> Unit,
    val onFinish: () -> Unit,
    val onFinishAll: () -> Unit,
    val onConfirmFinishAll: (durationMinutes: Int) -> Unit,
    val onDismissFinishAll: () -> Unit,
    val onDiscard: () -> Unit,
)

@Composable
internal fun ActiveWorkoutContent(
    session: ActiveSessionUi,
    now: () -> Instant,
    restTimer: RestTimerState?,
    exerciseOptions: List<ExerciseOption>,
    finishAllProposal: FinishAllProposal?,
    actions: WorkoutActions,
    modifier: Modifier = Modifier,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showFinishDialog by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val itemModifier = Modifier
        .widthIn(max = Spacing.maxContentWidth)
        .fillMaxWidth()

    // The first exercise with an open set is the active one (level-2 card with the stepper panel).
    val activeExerciseId = session.exercises.firstOrNull { exercise -> exercise.sets.any { !it.isCompleted } }
        ?.sessionExerciseId

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthClass = WindowWidthClass.fromWidth(maxWidth)
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = widthClass.horizontalMargin, vertical = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(widthClass.gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "header") {
                    WorkoutHeader(
                        session = session,
                        now = now,
                        onFinish = { showFinishDialog = true },
                        onFinishAll = actions.onFinishAll,
                        onDiscard = { showDiscardDialog = true },
                        modifier = itemModifier,
                    )
                }
                if (session.exercises.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Filled.Add,
                            title = stringResource(R.string.feature_workout_no_exercises_title),
                            message = stringResource(R.string.feature_workout_no_exercises),
                            actionLabel = stringResource(R.string.feature_workout_add_exercise),
                            onAction = { showPicker = true },
                            modifier = itemModifier,
                        )
                    }
                }
                items(session.exercises, key = { it.sessionExerciseId }) { exercise ->
                    ExerciseCard(
                        exercise = exercise,
                        actions = actions,
                        isActive = exercise.sessionExerciseId == activeExerciseId,
                        modifier = itemModifier,
                    )
                }
                item(key = "add_exercise") {
                    FilledTonalButton(onClick = { showPicker = true }, modifier = itemModifier) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(
                            text = stringResource(R.string.feature_workout_add_exercise),
                            modifier = Modifier.padding(start = Spacing.xs),
                        )
                    }
                }
            }

            RestTimerSection(
                restTimer = restTimer,
                now = now,
                onSkip = actions.onSkipRest,
                onAdjust = actions.onAdjustRest,
            )
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            exercises = exerciseOptions,
            onSelect = { exerciseId ->
                showPicker = false
                actions.onAddExercise(exerciseId)
            },
            onDismiss = { showPicker = false },
        )
    }

    if (showFinishDialog) {
        FinishWorkoutDialog(
            openSets = session.openSets,
            onConfirm = {
                showFinishDialog = false
                actions.onFinish()
            },
            onDismiss = { showFinishDialog = false },
        )
    }

    if (finishAllProposal != null) {
        FinishAllDialog(
            proposal = finishAllProposal,
            onConfirm = actions.onConfirmFinishAll,
            onDismiss = actions.onDismissFinishAll,
        )
    }

    if (showDiscardDialog) {
        DiscardWorkoutDialog(
            onConfirm = {
                showDiscardDialog = false
                actions.onDiscard()
            },
            onDismiss = { showDiscardDialog = false },
        )
    }
}

/** Reads the clock in its own scope so only this part recomposes every second. */
@Composable
private fun RestTimerSection(
    restTimer: RestTimerState?,
    now: () -> Instant,
    onSkip: () -> Unit,
    onAdjust: (Int) -> Unit,
) {
    val runningRest = restTimer.runningAt(now())
    AnimatedVisibility(visible = runningRest != null) {
        if (runningRest != null) {
            RestTimerBar(state = runningRest, now = now, onSkip = onSkip, onAdjust = onAdjust)
        }
    }
}

@Composable
private fun WorkoutHeader(
    session: ActiveSessionUi,
    now: () -> Instant,
    onFinish: () -> Unit,
    onFinishAll: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    GymPulseCard(
        modifier = modifier,
        variant = GymPulseCardVariant.Level1,
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(Spacing.l),
        verticalSpacing = Spacing.m,
    ) {
        Text(
            text = session.title ?: stringResource(R.string.feature_workout_empty_workout),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            val elapsed = Duration.between(session.startedAt, now()).seconds
            HeaderStat(
                label = stringResource(R.string.feature_workout_duration),
                value = WorkoutFormat.formatElapsed(elapsed),
                modifier = Modifier.weight(1f),
            )
            HeaderStat(
                label = stringResource(R.string.feature_workout_volume),
                value = WorkoutFormat.formatKg(session.volumeKg),
                unit = stringResource(R.string.feature_workout_column_kg),
                modifier = Modifier.weight(1f),
            )
            HeaderStat(
                label = stringResource(R.string.feature_workout_sets),
                value = stringResource(
                    R.string.feature_workout_sets_progress,
                    session.completedSets,
                    session.completedSets + session.openSets,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        val totalSets = session.completedSets + session.openSets
        if (totalSets > 0) {
            GymPulseProgressBar(progress = ProgressMath.fraction(session.completedSets.toDouble(), totalSets.toDouble()))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.feature_workout_more_actions),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feature_workout_finish_all)) },
                        enabled = session.openSets > 0,
                        onClick = {
                            menuOpen = false
                            onFinishAll()
                        },
                    )
                }
            }
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.feature_workout_discard))
            }
            Button(onClick = onFinish) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Text(
                    text = stringResource(R.string.feature_workout_finish),
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun HeaderStat(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        LabelCaps(text = label)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = value,
                style = MaterialTheme.typography.metricMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            if (unit != null) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}

@Composable
private fun FinishWorkoutDialog(openSets: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_workout_finish_title)) },
        text = {
            Text(
                if (openSets > 0) {
                    pluralStringResource(R.plurals.feature_workout_finish_open_sets, openSets, openSets)
                } else {
                    stringResource(R.string.feature_workout_finish_text)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.feature_workout_finish_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_workout_cancel)) }
        },
    )
}

/** Shows the estimated duration and lets the user change it in minutes before everything is ticked off. */
@Composable
private fun FinishAllDialog(proposal: FinishAllProposal, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val proposedMinutes = ((proposal.durationSeconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE).toInt().coerceAtLeast(1)
    var minutesText by rememberSaveable(proposal.durationSeconds) { mutableStateOf(proposedMinutes.toString()) }
    val minutes = minutesText.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_workout_finish_all_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.feature_workout_finish_all_text, proposedMinutes))
                Text(
                    text = stringResource(R.string.feature_workout_finish_all_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { value -> minutesText = value.filter { it.isDigit() }.take(MAX_MINUTES_DIGITS) },
                    label = { Text(stringResource(R.string.feature_workout_finish_all_minutes)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { minutes?.let(onConfirm) }, enabled = minutes != null && minutes >= 1) {
                Text(stringResource(R.string.feature_workout_finish_all_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_workout_cancel)) }
        },
    )
}

private const val SECONDS_PER_MINUTE = 60L
private const val MAX_MINUTES_DIGITS = 4

@Composable
private fun DiscardWorkoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_workout_discard_title)) },
        text = { Text(stringResource(R.string.feature_workout_discard_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.feature_workout_discard_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_workout_cancel)) }
        },
    )
}
