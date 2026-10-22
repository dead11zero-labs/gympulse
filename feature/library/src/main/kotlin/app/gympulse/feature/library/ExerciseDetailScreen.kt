package app.gympulse.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.designsystem.component.GymPulseCard
import app.gympulse.core.designsystem.component.GymPulseBottomSheet
import app.gympulse.core.designsystem.component.GymPulseCardVariant
import app.gympulse.core.designsystem.component.ListGroup
import app.gympulse.core.designsystem.component.ListRow
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.model.Equipment
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.WorkoutPlan
import app.gympulse.feature.library.data.ExerciseDetail
import coil3.compose.SubcomposeAsyncImage
import java.util.Locale

@Composable
internal fun ExerciseDetailScreen(
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val language = appLanguage()
    LaunchedEffect(language) { viewModel.setLocale(language) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val planAddResult = state.planAddResult
    val planAddMessage = when (planAddResult) {
        is PlanAddResult.Added -> stringResource(R.string.library_added_to_plan, planAddResult.planName)
        PlanAddResult.Failed -> stringResource(R.string.library_add_to_plan_failed)
        null -> null
    }
    LaunchedEffect(planAddResult) {
        if (planAddMessage != null) {
            snackbarHostState.showSnackbar(planAddMessage)
            viewModel.consumePlanAddResult()
        }
    }

    val detail = state.detail
    Box(modifier = modifier) {
        when {
            state.isLoading -> LoadingIndicator()
            detail == null -> EmptyState(
                title = stringResource(R.string.library_exercise_not_found),
                message = stringResource(R.string.library_exercise_not_found_message),
                actionLabel = stringResource(R.string.library_back),
                onAction = onBack,
            )
            else -> ExerciseDetailContent(
                detail = detail,
                isFavorite = state.isFavorite,
                equipment = state.equipment,
                previousSets = state.previousSets,
                plans = state.plans,
                onToggleFavorite = viewModel::toggleFavorite,
                onAddToPlan = viewModel::addToPlan,
                onEdit = { onEdit(detail.exercise.id) },
                onDelete = viewModel::delete,
            )
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ExerciseDetailContent(
    detail: ExerciseDetail,
    isFavorite: Boolean,
    equipment: Equipment?,
    previousSets: List<SetEntry>,
    plans: List<WorkoutPlan>,
    onToggleFavorite: () -> Unit,
    onAddToPlan: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exercise = detail.exercise
    val locale = appLocale()
    var confirmDelete by remember { mutableStateOf(false) }
    var showPlanSheet by remember { mutableStateOf(false) }

    FormColumn(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onToggleFavorite) {
                // Ungefüllt und neutral, bis die Übung Favorit ist; dann gefüllt in der Akzentfarbe.
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.Favorite,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.library_remove_favorite else R.string.library_add_favorite,
                    ),
                    tint = if (isFavorite) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.library_edit))
            }
            if (exercise.isCustom) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.library_delete))
                }
            }
        }

        OutlinedButton(
            onClick = { showPlanSheet = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = Sizing.minTouchTarget),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(
                text = stringResource(R.string.library_add_to_plan),
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }

        if (exercise.imageUrls.isNotEmpty()) {
            // Die Bilder werden erst geladen, wenn die Detailseite offen ist (Coil, mit Cache).
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(items = exercise.imageUrls) { url ->
                    ExerciseImage(
                        url = url,
                        contentDescription = stringResource(R.string.library_image_description, exercise.name),
                        modifier = Modifier.fillParentMaxWidth(IMAGE_WIDTH_FRACTION),
                    )
                }
            }
        }

        ListGroup(modifier = Modifier.fillMaxWidth()) {
            InfoRow(
                label = stringResource(R.string.library_label_category),
                value = stringResource(exercise.category.categoryLabelRes()),
            )
            InfoRowDivider()
            InfoRow(
                label = stringResource(R.string.library_label_equipment_type),
                value = equipmentTypeName(exercise.equipmentType),
            )
            InfoRowDivider()
            InfoRow(
                label = stringResource(R.string.library_label_tracking),
                value = stringResource(exercise.trackingType.trackingLabelRes()),
            )
            if (exercise.primaryMuscles.isNotEmpty()) {
                InfoRowDivider()
                InfoRow(
                    label = stringResource(R.string.library_label_primary_muscles),
                    value = muscleNames(exercise.primaryMuscles),
                )
            }
            if (exercise.secondaryMuscles.isNotEmpty()) {
                InfoRowDivider()
                InfoRow(
                    label = stringResource(R.string.library_label_secondary_muscles),
                    value = muscleNames(exercise.secondaryMuscles),
                )
            }
            val bodyweightFactor = exercise.bodyweightFactor
            if (bodyweightFactor != null) {
                InfoRowDivider()
                InfoRow(
                    label = stringResource(R.string.library_label_bodyweight_factor),
                    value = formatNumber(bodyweightFactor, locale),
                )
            }
            val restSeconds = exercise.defaultRestSeconds
            if (restSeconds != null) {
                InfoRowDivider()
                InfoRow(
                    label = stringResource(R.string.library_label_rest),
                    value = stringResource(R.string.library_seconds_value, restSeconds),
                )
            }
        }

        if (equipment != null) {
            EquipmentBlock(equipment = equipment)
        }

        if (exercise.instructions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SectionTitle(stringResource(R.string.library_instructions))
                if (detail.instructionsInEnglishOnly) {
                    Text(
                        text = stringResource(R.string.library_instructions_english_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GymPulseCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = GymPulseCardVariant.Filled,
                    verticalSpacing = Spacing.s,
                ) {
                    exercise.instructions.forEachIndexed { index, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(
                                text = stringResource(R.string.library_step_number, index + 1),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        val notes = exercise.notes
        if (!notes.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SectionTitle(stringResource(R.string.library_notes))
                GymPulseCard(modifier = Modifier.fillMaxWidth(), variant = GymPulseCardVariant.Level2) {
                    Text(text = notes, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionTitle(stringResource(R.string.library_last_session))
            GymPulseCard(
                modifier = Modifier.fillMaxWidth(),
                variant = GymPulseCardVariant.Filled,
                verticalSpacing = Spacing.xxs,
            ) {
                if (previousSets.isEmpty()) {
                    Text(
                        text = stringResource(R.string.library_last_session_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    previousSets.forEachIndexed { index, set ->
                        Text(
                            text = setLine(index = index, set = set, locale = locale),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        val source = detail.source
        Text(
            text = if (source != null) {
                stringResource(R.string.library_source_license, source, detail.license ?: stringResource(R.string.library_unknown))
            } else {
                stringResource(R.string.library_source_custom)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showPlanSheet) {
        PlanPickerSheet(
            plans = plans,
            onPick = { planId ->
                showPlanSheet = false
                onAddToPlan(planId)
            },
            onDismiss = { showPlanSheet = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.library_delete_exercise_title)) },
            text = { Text(stringResource(R.string.library_delete_exercise_message, exercise.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) { Text(stringResource(R.string.library_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.library_cancel)) }
            },
        )
    }
}

/** Auswahl des Plans, dem die Übung hinzugefügt wird; ohne Pläne ein Hinweis statt der Liste. */
@Composable
private fun PlanPickerSheet(
    plans: List<WorkoutPlan>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    GymPulseBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.library_add_to_plan_title),
    ) {
        if (plans.isEmpty()) {
            Text(
                text = stringResource(R.string.library_add_to_plan_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.m),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                items(items = plans, key = { it.id }) { plan ->
                    ListRow(
                        headline = plan.name,
                        supportingText = pluralStringResource(
                            R.plurals.library_plan_exercise_count,
                            plan.exercises.size,
                            plan.exercises.size,
                        ),
                        onClick = { onPick(plan.id) },
                        modifier = Modifier.heightIn(min = Sizing.minTouchTarget),
                    )
                }
            }
        }
    }
}

/** Zugeordnete Maschine mit ihren gespeicherten Einstellungen (z. B. "Sitzhöhe: 4"). */
@Composable
private fun EquipmentBlock(equipment: Equipment, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionTitle(stringResource(R.string.library_machine))
        ListGroup(modifier = Modifier.fillMaxWidth()) {
            ListRow(
                headline = equipment.name,
                supportingText = equipment.location?.takeIf { it.isNotBlank() },
                leadingIcon = Icons.Filled.Settings,
            )
            equipment.settings.forEach { setting ->
                InfoRowDivider()
                InfoRow(label = setting.key, value = setting.value.ifBlank { stringResource(R.string.library_unknown) })
            }
        }
    }
}

@Composable
private fun setLine(index: Int, set: SetEntry, locale: Locale): String {
    val weight = set.weightKg
    val reps = set.reps
    val duration = set.durationSeconds
    val distance = set.distanceMeters
    val value = when {
        weight != null && reps != null -> stringResource(R.string.library_set_weight_reps, formatNumber(weight, locale), reps)
        reps != null -> stringResource(R.string.library_set_reps, reps)
        duration != null -> stringResource(R.string.library_set_duration, duration)
        distance != null -> stringResource(R.string.library_set_distance, formatNumber(distance, locale))
        else -> stringResource(R.string.library_unknown)
    }
    return stringResource(R.string.library_set_line, index + 1, value)
}

/** Ein Übungsbild mit Ladeanzeige und Fehlerzustand (z. B. offline). */
@Composable
private fun ExerciseImage(url: String, contentDescription: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(IMAGE_ASPECT_RATIO)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            loading = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
            error = {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.library_image_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            },
        )
    }
}

private const val IMAGE_WIDTH_FRACTION = 0.85f
private const val IMAGE_ASPECT_RATIO = 4f / 3f
