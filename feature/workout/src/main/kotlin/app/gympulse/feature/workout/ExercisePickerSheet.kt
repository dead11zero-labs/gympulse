package app.gympulse.feature.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.gympulse.core.designsystem.theme.Spacing

/** Bottom sheet with a searchable exercise list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExercisePickerSheet(
    exercises: List<ExerciseOption>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, exercises) { filterExercises(exercises, query) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = stringResource(R.string.feature_workout_add_exercise),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Spacing.screenMargin),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.feature_workout_search_exercises)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.xs),
        )
        if (filtered.isEmpty()) {
            Text(
                text = stringResource(R.string.feature_workout_no_exercise_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.screenMargin),
            )
        }
        LazyColumn {
            items(filtered, key = { it.id }) { exercise ->
                ListItem(
                    headlineContent = { Text(exercise.name) },
                    trailingContent = if (exercise.isFavorite) {
                        {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = stringResource(R.string.feature_workout_favorite),
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.clickable { onSelect(exercise.id) },
                )
            }
        }
    }
}

/** Case-insensitive search on the name; every word of the query must match. */
internal fun filterExercises(exercises: List<ExerciseOption>, query: String): List<ExerciseOption> {
    val words = query.trim().lowercase().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return exercises
    return exercises.filter { option ->
        val name = option.name.lowercase()
        words.all { it in name }
    }
}
