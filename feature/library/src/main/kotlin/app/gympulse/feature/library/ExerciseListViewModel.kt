package app.gympulse.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MuscleGroup
import app.gympulse.feature.library.data.ExerciseFilter
import app.gympulse.feature.library.data.LibraryRepository
import app.gympulse.feature.library.data.filterExercises
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.Locale
import javax.inject.Inject

data class ExerciseListUiState(
    val isLoading: Boolean = true,
    /** Der Filter, mit dem [exercises] berechnet wurde (Suchtext bereits entprellt). */
    val filter: ExerciseFilter = ExerciseFilter(),
    val exercises: List<Exercise> = emptyList(),
    /** Anzahl aller Übungen ohne Filter, für die Unterscheidung der leeren Zustände. */
    val totalCount: Int = 0,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseListViewModel @Inject constructor(
    private val repository: LibraryRepository,
) : ViewModel() {

    private val locale = MutableStateFlow(Locale.getDefault().language)
    private val _query = MutableStateFlow("")
    private val facets = MutableStateFlow(ExerciseFilter())

    /** Text im Suchfeld, sofort aktuell (die Liste folgt entprellt). */
    val query: StateFlow<String> = _query.asStateFlow()

    val uiState: StateFlow<ExerciseListUiState> = combine(
        locale.flatMapLatest { repository.observeExercises(it) },
        _query.debounce { if (it.isBlank()) 0L else QUERY_DEBOUNCE_MILLIS },
        facets,
    ) { all, debouncedQuery, facet ->
        val filter = facet.copy(query = debouncedQuery)
        ExerciseListUiState(
            isLoading = false,
            filter = filter,
            exercises = filterExercises(all, filter),
            totalCount = all.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ExerciseListUiState())

    /** Sprache der App (z. B. "de"); bestimmt Namen und Suchreihenfolge. */
    fun setLocale(language: String) {
        locale.value = language
    }

    fun setQuery(text: String) {
        _query.value = text
    }

    fun setMuscle(muscle: MuscleGroup?) = facets.update { it.copy(muscle = muscle) }

    fun setEquipmentType(type: EquipmentType?) = facets.update { it.copy(equipmentType = type) }

    fun setCategory(category: ExerciseCategory?) = facets.update { it.copy(category = category) }

    fun toggleFavoritesOnly() = facets.update { it.copy(favoritesOnly = !it.favoritesOnly) }

    fun clearFilters() {
        _query.value = ""
        facets.value = ExerciseFilter()
    }

    companion object {
        const val QUERY_DEBOUNCE_MILLIS = 250L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
