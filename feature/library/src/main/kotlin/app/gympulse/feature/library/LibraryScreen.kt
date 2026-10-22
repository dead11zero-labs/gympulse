package app.gympulse.feature.library

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.data.ui.labelRes
import app.gympulse.core.designsystem.component.GymPulseCard
import app.gympulse.core.designsystem.component.GymPulseCardVariant
import app.gympulse.core.designsystem.component.LabelCaps
import app.gympulse.core.designsystem.component.ListRow
import app.gympulse.core.designsystem.theme.Sizing
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
        // Titel/Umschalter liegen als erstes Element in der jeweiligen Liste und scrollen mit weg.
        val header: @Composable () -> Unit = {
            LibraryHeader(
                selectedTab = tab,
                onSelectTab = { tab = it },
            )
        }
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (tab == TAB_EXERCISES) {
                ExerciseListContent(
                    onOpenExercise = onOpenExercise,
                    header = header,
                    modifier = Modifier.weight(1f),
                )
            } else {
                EquipmentListContent(
                    onOpenEquipment = onOpenEquipment,
                    onCreateEquipment = onCreateEquipment,
                    header = header,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ExerciseListContent(
    onOpenExercise: (String) -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseListViewModel = hiltViewModel(),
) {
    val language = appLanguage()
    LaunchedEffect(language) { viewModel.setLocale(language) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter = state.filter

    ContentColumn(modifier = modifier) {
        // Immer eine Liste: Titel, Suche und Filter sind ihre ersten Elemente und scrollen mit weg.
        // Lade-/Leerzustände sind ebenfalls Elemente, damit das Suchfeld seinen Fokus behält.
        LazyColumn(
            modifier = Modifier.weight(1f),
            // Platz unter der letzten Zeile, damit der Button nichts verdeckt.
            contentPadding = listPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            item(key = "header") {
                Column {
                    header()
                    SearchField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        label = stringResource(R.string.library_search_exercises),
                        modifier = Modifier.padding(horizontal = Spacing.screenMargin, vertical = Spacing.xs),
                    )
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.screenMargin)
                            .animateContentSize(),
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
                }
            }
            when {
                state.isLoading -> item(key = "loading") {
                    LoadingIndicator(modifier = Modifier.fillParentMaxHeight(PLACEHOLDER_HEIGHT_FRACTION))
                }
                state.totalCount == 0 -> item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.Search,
                        title = stringResource(R.string.library_exercises_empty_title),
                        message = stringResource(R.string.library_exercises_empty_message),
                        modifier = Modifier.fillParentMaxHeight(PLACEHOLDER_HEIGHT_FRACTION),
                    )
                }
                state.exercises.isEmpty() -> item(key = "no_match") {
                    EmptyState(
                        title = stringResource(R.string.library_exercises_no_match_title),
                        message = stringResource(R.string.library_exercises_no_match_message),
                        modifier = Modifier.fillParentMaxHeight(PLACEHOLDER_HEIGHT_FRACTION),
                        actionLabel = stringResource(R.string.library_clear_filters),
                        onAction = viewModel::clearFilters,
                    )
                }
                else -> {
                    item(key = "count") {
                        LabelCaps(
                            text = stringResource(R.string.library_exercise_count, state.exercises.size),
                            modifier = Modifier.padding(horizontal = Spacing.screenMargin, vertical = Spacing.xs),
                        )
                    }
                    items(items = state.exercises, key = { it.id }) { exercise ->
                        ExerciseRow(
                            exercise = exercise,
                            onClick = { onOpenExercise(exercise.id) },
                            modifier = Modifier.padding(horizontal = Spacing.screenMargin).animateItem(),
                        )
                    }
                }
            }
        }
    }
}

private const val PLACEHOLDER_HEIGHT_FRACTION = 0.6f

/**
 * Platz unter der letzten Zeile, damit der Button nichts verdeckt. Den Seitenrand tragen die
 * Elemente selbst, da der scrollende Kopf (Titel, Suche, Filter) eigene Abstände mitbringt.
 */
private fun listPadding() = PaddingValues(bottom = Spacing.xxl * 2)

@Composable
private fun ExerciseRow(exercise: Exercise, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val muscle = exercise.primaryMuscles.firstOrNull()?.let { stringResource(it.labelRes()) }
    val equipment = stringResource(exercise.equipmentType.labelRes())
    val custom = if (exercise.isCustom) stringResource(R.string.library_custom_badge) else null
    val scheme = MaterialTheme.colorScheme
    GymPulseCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        variant = GymPulseCardVariant.Filled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(Spacing.none),
    ) {
        ListRow(
            headline = exercise.name,
            supportingText = listOfNotNull(muscle, equipment, custom).joinToString(" · "),
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (exercise.isFavorite) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = stringResource(R.string.library_favorite),
                            tint = scheme.secondary,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

/**
 * Kopf der Bibliothek: Titel und Umschalter Übungen/Geräte als tonale Fläche, die aktive Seite
 * als hellere Pille (Kinetic Material, Tabs in `surfaceContainerLow`).
 */
@Composable
private fun LibraryHeader(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = Spacing.maxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                text = stringResource(R.string.feature_library_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    LibraryTab(
                        label = stringResource(R.string.library_tab_exercises),
                        selected = selectedTab == TAB_EXERCISES,
                        onClick = { onSelectTab(TAB_EXERCISES) },
                        modifier = Modifier.weight(1f),
                    )
                    LibraryTab(
                        label = stringResource(R.string.library_tab_equipment),
                        selected = selectedTab == TAB_EQUIPMENT,
                        onClick = { onSelectTab(TAB_EQUIPMENT) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) scheme.surfaceContainerHighest else Color.Transparent,
        contentColor = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .heightIn(min = Sizing.minTouchTarget)
                .padding(horizontal = Spacing.s),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun EquipmentListContent(
    onOpenEquipment: (String) -> Unit,
    onCreateEquipment: () -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EquipmentListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ContentColumn(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = listPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            item(key = "header") { header() }
            when {
                state.isLoading -> item(key = "loading") {
                    LoadingIndicator(modifier = Modifier.fillParentMaxHeight(PLACEHOLDER_HEIGHT_FRACTION))
                }
                state.equipment.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        icon = Icons.Filled.Settings,
                        title = stringResource(R.string.library_equipment_empty_title),
                        message = stringResource(R.string.library_equipment_empty_message),
                        modifier = Modifier.fillParentMaxHeight(PLACEHOLDER_HEIGHT_FRACTION),
                        actionLabel = stringResource(R.string.library_new_equipment),
                        onAction = onCreateEquipment,
                    )
                }
                else -> items(items = state.equipment, key = { it.id }) { equipment ->
                    EquipmentRow(
                        equipment = equipment,
                        onClick = { onOpenEquipment(equipment.id) },
                        modifier = Modifier.padding(horizontal = Spacing.screenMargin).animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun EquipmentRow(equipment: Equipment, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val settingsCount = stringResource(R.string.library_settings_count, equipment.settings.size)
    GymPulseCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        variant = GymPulseCardVariant.Filled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(Spacing.none),
    ) {
        ListRow(
            headline = equipment.name,
            supportingText = listOfNotNull(equipment.location?.takeIf { it.isNotBlank() }, settingsCount)
                .joinToString(" · "),
            leadingIcon = Icons.Filled.Settings,
        )
    }
}
