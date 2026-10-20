package app.gympulse.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.model.Equipment
import app.gympulse.core.model.SetEntry
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

    val detail = state.detail
    when {
        state.isLoading -> LoadingIndicator(modifier)
        detail == null -> EmptyState(
            title = stringResource(R.string.library_exercise_not_found),
            message = stringResource(R.string.library_exercise_not_found_message),
            modifier = modifier,
            actionLabel = stringResource(R.string.library_back),
            onAction = onBack,
        )
        else -> ExerciseDetailContent(
            detail = detail,
            equipment = state.equipment,
            previousSets = state.previousSets,
            onToggleFavorite = viewModel::toggleFavorite,
            onEdit = { onEdit(detail.exercise.id) },
            onDelete = viewModel::delete,
            modifier = modifier,
        )
    }
}

@Composable
private fun ExerciseDetailContent(
    detail: ExerciseDetail,
    equipment: Equipment?,
    previousSets: List<SetEntry>,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exercise = detail.exercise
    val locale = appLocale()
    var confirmDelete by remember { mutableStateOf(false) }

    FormColumn(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (exercise.isFavorite) Icons.Filled.Favorite else Icons.Outlined.Favorite,
                    contentDescription = stringResource(
                        if (exercise.isFavorite) R.string.library_remove_favorite else R.string.library_add_favorite,
                    ),
                    tint = if (exercise.isFavorite) {
                        MaterialTheme.colorScheme.primary
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

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            InfoRow(
                label = stringResource(R.string.library_label_category),
                value = stringResource(exercise.category.categoryLabelRes()),
            )
            InfoRow(
                label = stringResource(R.string.library_label_equipment_type),
                value = equipmentTypeName(exercise.equipmentType),
            )
            InfoRow(
                label = stringResource(R.string.library_label_tracking),
                value = stringResource(exercise.trackingType.trackingLabelRes()),
            )
            if (exercise.primaryMuscles.isNotEmpty()) {
                InfoRow(
                    label = stringResource(R.string.library_label_primary_muscles),
                    value = muscleNames(exercise.primaryMuscles),
                )
            }
            if (exercise.secondaryMuscles.isNotEmpty()) {
                InfoRow(
                    label = stringResource(R.string.library_label_secondary_muscles),
                    value = muscleNames(exercise.secondaryMuscles),
                )
            }
            val bodyweightFactor = exercise.bodyweightFactor
            if (bodyweightFactor != null) {
                InfoRow(
                    label = stringResource(R.string.library_label_bodyweight_factor),
                    value = formatNumber(bodyweightFactor, locale),
                )
            }
            val restSeconds = exercise.defaultRestSeconds
            if (restSeconds != null) {
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
                exercise.instructions.forEachIndexed { index, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = stringResource(R.string.library_step_number, index + 1),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        val notes = exercise.notes
        if (!notes.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SectionTitle(stringResource(R.string.library_notes))
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionTitle(stringResource(R.string.library_last_session))
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
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

/** Zugeordnete Maschine mit ihren gespeicherten Einstellungen (z. B. "Sitzhöhe: 4"). */
@Composable
private fun EquipmentBlock(equipment: Equipment, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionTitle(stringResource(R.string.library_machine))
        Text(
            text = listOfNotNull(equipment.name, equipment.location?.takeIf { it.isNotBlank() }).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        equipment.settings.forEach { setting ->
            InfoRow(label = setting.key, value = setting.value.ifBlank { stringResource(R.string.library_unknown) })
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
            .clip(MaterialTheme.shapes.medium)
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
