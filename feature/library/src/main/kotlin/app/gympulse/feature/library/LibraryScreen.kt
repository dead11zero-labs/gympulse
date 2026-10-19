package app.gympulse.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.data.ui.labelRes
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.model.Equipment
import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MuscleGroup

private const val TAB_EXERCISES = 0
private const val TAB_EQUIPMENT = 1

/** Einstieg der Bibliothek: zwei Bereiche (Übungen, Geräte) mit jeweils eigenem "Neu"-Button. */
@Composable
internal fun LibraryScreen(
    onOpenExercise: (String) -> Unit,
    onCreateExercise: () -> Unit,
    onOpenEquipment: (String) -> Unit,
    onCreateEquipment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(TAB_EXERCISES) }

    Scaffold(
        modifier = modifier,
        // Die Insets (Statusleiste, Navigation) verarbeitet bereits das äußere Scaffold der App.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (tab == TAB_EXERCISES) {
                ExtendedFloatingActionButton(
                    onClick = onCreateExercise,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.library_new_exercise)) },
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = onCreateEquipment,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.library_new_equipment)) },
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == TAB_EXERCISES,
                    onClick = { tab = TAB_EXERCISES },
                    text = { Text(stringResource(R.string.library_tab_exercises)) },
                )
                Tab(
                    selected = tab == TAB_EQUIPMENT,
                    onClick = { tab = TAB_EQUIPMENT },
                    text = { Text(stringResource(R.string.library_tab_equipment)) },
                )
            }
            if (tab == TAB_EXERCISES) {
                ExerciseListContent(onOpenExercise = onOpenExercise, modifier = Modifier.weight(1f))
            } else {
                EquipmentListContent(
                    onOpenEquipment = onOpenEquipment,
                    onCreateEquipment = onCreateEquipment,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ExerciseListContent(
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseListViewModel = hiltViewModel(),
) {
    val language = appLanguage()
    LaunchedEffect(language) { viewModel.setLocale(language) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter = state.filter

    ContentColumn(modifier = modifier) {
        SearchField(
            value = query,
            onValueChange = viewModel::setQuery,
            label = stringResource(R.string.library_search_exercises),
            modifier = Modifier.padding(horizontal = Spacing.screenMargin, vertical = Spacing.xs),
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = filter.favoritesOnly,
                onClick = viewModel::toggleFavoritesOnly,
                label = { Text(stringResource(R.string.library_filter_favorites)) },
                leadingIcon = {
                    Icon(
                        imageVector = if (filter.favoritesOnly) Icons.Filled.Favorite else Icons.Outlined.Favorite,
                        contentDescription = null,
                    )
                },
            )
            FilterDropdownChip<MuscleGroup>(
                title = stringResource(R.string.library_filter_muscle),
                allLabel = stringResource(R.string.library_filter_all),
                selected = filter.muscle,
                options = MuscleGroup.entries,
                optionLabel = { stringResource(it.labelRes()) },
                onSelected = viewModel::setMuscle,
            )
            FilterDropdownChip<EquipmentType>(
                title = stringResource(R.string.library_filter_equipment_type),
                allLabel = stringResource(R.string.library_filter_all),
                selected = filter.equipmentType,
                options = EquipmentType.entries,
                optionLabel = { stringResource(it.labelRes()) },
                onSelected = viewModel::setEquipmentType,
            )
            FilterDropdownChip<ExerciseCategory>(
                title = stringResource(R.string.library_filter_category),
                allLabel = stringResource(R.string.library_filter_all),
                selected = filter.category,
                options = ExerciseCategory.entries,
                optionLabel = { stringResource(it.categoryLabelRes()) },
                onSelected = viewModel::setCategory,
            )
            if (filter.isActive || query.isNotEmpty()) {
                TextButton(onClick = viewModel::clearFilters) {
                    Text(stringResource(R.string.library_clear_filters))
                }
            }
        }

        when {
            state.isLoading -> LoadingIndicator(modifier = Modifier.weight(1f))
            state.totalCount == 0 -> EmptyState(
                title = stringResource(R.string.library_exercises_empty_title),
                message = stringResource(R.string.library_exercises_empty_message),
                modifier = Modifier.weight(1f),
            )
            state.exercises.isEmpty() -> EmptyState(
                title = stringResource(R.string.library_exercises_no_match_title),
                message = stringResource(R.string.library_exercises_no_match_message),
                modifier = Modifier.weight(1f),
                actionLabel = stringResource(R.string.library_clear_filters),
                onAction = viewModel::clearFilters,
            )
            else -> {
                Text(
                    text = stringResource(R.string.library_exercise_count, state.exercises.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screenMargin, vertical = Spacing.xs),
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    // Platz unter der letzten Zeile, damit der Button nichts verdeckt.
                    contentPadding = PaddingValues(bottom = Spacing.xxl * 2),
                ) {
                    items(items = state.exercises, key = { it.id }) { exercise ->
                        ExerciseRow(exercise = exercise, onClick = { onOpenExercise(exercise.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val muscle = exercise.primaryMuscles.firstOrNull()?.let { stringResource(it.labelRes()) }
    val equipment = stringResource(exercise.equipmentType.labelRes())
    val custom = if (exercise.isCustom) stringResource(R.string.library_custom_badge) else null
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = { Text(exercise.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(listOfNotNull(muscle, equipment, custom).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            if (exercise.isFavorite) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = stringResource(R.string.library_favorite),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
private fun EquipmentListContent(
    onOpenEquipment: (String) -> Unit,
    onCreateEquipment: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EquipmentListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ContentColumn(modifier = modifier) {
        when {
            state.isLoading -> LoadingIndicator(modifier = Modifier.weight(1f))
            state.equipment.isEmpty() -> EmptyState(
                title = stringResource(R.string.library_equipment_empty_title),
                message = stringResource(R.string.library_equipment_empty_message),
                modifier = Modifier.weight(1f),
                actionLabel = stringResource(R.string.library_new_equipment),
                onAction = onCreateEquipment,
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = Spacing.xxl * 2),
            ) {
                items(items = state.equipment, key = { it.id }) { equipment ->
                    EquipmentRow(equipment = equipment, onClick = { onOpenEquipment(equipment.id) })
                }
            }
        }
    }
}

@Composable
private fun EquipmentRow(equipment: Equipment, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val settingsCount = stringResource(R.string.library_settings_count, equipment.settings.size)
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = { Text(equipment.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(equipment.location?.takeIf { it.isNotBlank() }, settingsCount).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
