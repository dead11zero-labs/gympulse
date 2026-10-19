package app.gympulse.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.model.Equipment
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.SetType
import app.gympulse.feature.library.data.ExerciseDetail
import app.gympulse.feature.library.data.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class ExerciseDetailUiState(
    val isLoading: Boolean = true,
    /** Null, wenn die Übung nicht (mehr) existiert. */
    val detail: ExerciseDetail? = null,
    /** Dem Gerät zugeordnete Maschine samt Einstellungen. */
    val equipment: Equipment? = null,
    /** Erledigte Arbeitssätze der letzten Session mit dieser Übung. */
    val previousSets: List<SetEntry> = emptyList(),
    val deleted: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LibraryRepository,
) : ViewModel() {

    private val exerciseId: String = requireNotNull(savedStateHandle.get<String>(EXERCISE_ID_KEY)) {
        "ExerciseDetailRoute requires an exerciseId"
    }

    private val locale = MutableStateFlow(Locale.getDefault().language)
    private val previousSets = MutableStateFlow<List<SetEntry>>(emptyList())
    private val deleted = MutableStateFlow(false)

    val uiState: StateFlow<ExerciseDetailUiState> = combine(
        locale.flatMapLatest { repository.observeExercise(exerciseId, it) },
        repository.observeEquipment(),
        previousSets,
        deleted,
    ) { detail, equipment, sets, isDeleted ->
        ExerciseDetailUiState(
            isLoading = false,
            detail = detail,
            equipment = detail?.exercise?.equipmentId?.let { id -> equipment.firstOrNull { it.id == id } },
            previousSets = sets,
            deleted = isDeleted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ExerciseDetailUiState())

    init {
        viewModelScope.launch {
            previousSets.value = repository.previousSets(exerciseId)
                .filter { it.isCompleted && it.setType != SetType.WARMUP }
        }
    }

    fun setLocale(language: String) {
        locale.value = language
    }

    fun toggleFavorite() {
        val exercise = uiState.value.detail?.exercise ?: return
        viewModelScope.launch { repository.setFavorite(exercise.id, !exercise.isFavorite) }
    }

    /** Löscht nur eigene Übungen; für Seed-Übungen ist die Aktion wirkungslos. */
    fun delete() {
        val exercise = uiState.value.detail?.exercise ?: return
        if (!exercise.isCustom) return
        viewModelScope.launch {
            repository.deleteCustomExercise(exercise.id)
            deleted.value = true
        }
    }

    companion object {
        const val EXERCISE_ID_KEY = "exerciseId"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
