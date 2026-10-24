package app.gympulse.feature.plans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.data.repository.ExerciseRepository
import app.gympulse.core.data.repository.PlanRepository
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.Ids
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** Zustand der Übungsauswahl; [selectedIds] behält die Reihenfolge, in der gewählt wurde. */
internal data class ExercisePickerState(
    val query: String = "",
    val selectedIds: List<String> = emptyList(),
)

internal data class PlanEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val draft: PlanDraft = PlanDraft(planId = ""),
    /** Stand beim Öffnen bzw. nach dem Laden; Grundlage für die Prüfung auf ungespeicherte Änderungen. */
    val initial: PlanDraft = PlanDraft(planId = ""),
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val saveFailed: Boolean = false,
    val picker: ExercisePickerState? = null,
) {
    val isDirty: Boolean get() = draft != initial
}

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

@HiltViewModel
internal class PlanEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val planRepository: PlanRepository,
    private val exerciseRepository: ExerciseRepository,
    private val time: TimeProvider,
) : ViewModel() {

    /** Name des Argumentfelds in [PlanEditorRoute]. */
    private val existingPlanId: String? = savedStateHandle.get<String>(PLAN_ID_ARG)

    private val _state = MutableStateFlow(PlanEditorUiState())
    val state: StateFlow<PlanEditorUiState> = _state.asStateFlow()

    /** Alle Übungen in der Gerätesprache, für die Auswahl. */
    val exercises: StateFlow<List<Exercise>> = exerciseRepository.observeExercises(Locale.getDefault().language)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), emptyList())

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val plan = existingPlanId?.let { planRepository.observePlan(it).first() }
        val draft = if (plan != null) {
            val catalog = exerciseRepository.observeExercises(Locale.getDefault().language).first()
            PlanDraft.from(
                plan,
                names = catalog.associate { it.id to it.name },
                cardioIds = catalog.filter { it.category == ExerciseCategory.CARDIO }.map { it.id }.toSet(),
            )
        } else {
            PlanDraft(planId = newId())
        }
        _state.update { it.copy(isLoading = false, isNew = plan == null, draft = draft, initial = draft) }
    }

    private fun newId(): String = Ids.newId(time.now().toEpochMilli())

    private fun updateDraft(transform: (PlanDraft) -> PlanDraft) {
        _state.update { it.copy(draft = transform(it.draft)) }
    }

    fun onNameChange(value: String) = updateDraft { it.copy(name = value.take(PlanDraft.MAX_NAME_LENGTH)) }

    fun onDescriptionChange(value: String) = updateDraft { it.copy(description = value) }

    fun onUpdateItem(itemId: String, transform: (DraftExercise) -> DraftExercise) =
        updateDraft { it.updateItem(itemId, transform) }

    fun onMove(itemId: String, delta: Int) = updateDraft { it.move(itemId, delta) }

    fun onRemove(itemId: String) = updateDraft { it.remove(itemId) }

    fun onToggleLink(itemId: String) = updateDraft { it.toggleLinkWithNext(itemId) }

    fun openPicker() = _state.update { it.copy(picker = ExercisePickerState()) }

    fun closePicker() = _state.update { it.copy(picker = null) }

    fun onPickerQueryChange(query: String) =
        _state.update { s -> s.copy(picker = s.picker?.copy(query = query)) }

    fun onPickerToggle(exerciseId: String) = _state.update { s ->
        val picker = s.picker ?: return@update s
        val selected = if (exerciseId in picker.selectedIds) {
            picker.selectedIds - exerciseId
        } else {
            picker.selectedIds + exerciseId
        }
        s.copy(picker = picker.copy(selectedIds = selected))
    }

    /** Übernimmt die gewählten Übungen in der Reihenfolge der Auswahl und schließt die Auswahl. */
    fun confirmPicker() {
        val picker = _state.value.picker ?: return
        val byId = exercises.value.associateBy { it.id }
        val chosen = picker.selectedIds.mapNotNull { byId[it] }
        val millis = time.now().toEpochMilli()
        _state.update { s ->
            s.copy(
                draft = s.draft.addExercises(chosen) { Ids.newId(millis) },
                picker = null,
            )
        }
    }

    fun save() {
        val current = _state.value
        if (current.isSaving || current.isLoading) return
        val plan = current.draft.toPlanOrNull()
        if (plan == null) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                planRepository.save(plan)
                _state.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (error: Exception) {
                // Der Entwurf bleibt erhalten; die UI zeigt einen Hinweis und der Nutzer kann es erneut versuchen.
                error.printStackTrace()
                _state.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    fun onSaveFailureShown() = _state.update { it.copy(saveFailed = false) }

    companion object {
        const val PLAN_ID_ARG = "planId"
    }
}
