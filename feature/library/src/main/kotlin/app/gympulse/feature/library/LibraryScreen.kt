package app.gympulse.feature.library

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MuscleGroup

/** Einstieg der Bibliothek: Überschrift, Suche, Filter und die Übungsliste (Geräte sind Teil der Übungen). */
@Composable
internal fun LibraryScreen(
    onOpenExercise: (String) -> Unit,
    onCreateExercise: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseListViewModel = hiltViewModel(),
) {
    val language = appLanguage()
    LaunchedEffect(language) { viewModel.setLocale(language) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter = state.filter

    Scaffold(
        modifier = modifier,
        // Die Insets (Statusleiste, Navigation) verarbeitet bereits das äußere Scaffold der App.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateExercise,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.library_new_exercise)) },
            )
        },
    ) { innerPadding ->
        ContentColumn(modifier = Modifier.padding(innerPadding)) {
            Text(
                text = stringResource(R.string.feature_library_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = Spacing.screenMargin, vertical = Spacing.xs)
                    .semantics { heading() },
            )
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

            when {
                state.isLoading -> LoadingIndicator(modifier = Modifier.weight(1f))
                state.totalCount == 0 -> EmptyState(
                    icon = Icons.Filled.Search,
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
                    LabelCaps(
                        text = stringResource(R.string.library_exercise_count, state.exercises.size),
                        modifier = Modifier.padding(horizontal = Spacing.screenMargin, vertical = Spacing.xs),
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        // Platz unter der letzten Zeile, damit der Button nichts verdeckt.
                        contentPadding = listPadding(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    ) {
                        items(items = state.exercises, key = { it.id }) { exercise ->
                            ExerciseRow(
                                exercise = exercise,
                                onClick = { onOpenExercise(exercise.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Seitenrand oben/seitlich und Platz unter der letzten Zeile, damit der Button nichts verdeckt. */
private fun listPadding() = PaddingValues(
    start = Spacing.screenMargin,
    end = Spacing.screenMargin,
    top = Spacing.xs,
    bottom = Spacing.xxl * 2,
)

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
