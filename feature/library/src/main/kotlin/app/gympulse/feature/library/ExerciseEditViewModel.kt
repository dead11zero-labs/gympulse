package app.gympulse.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.resolveTracking
import app.gympulse.core.model.MuscleGroup
import app.gympulse.core.model.TrackingType
import app.gympulse.feature.library.data.ExerciseDraft
import app.gympulse.feature.library.data.LibraryRepository
import app.gympulse.feature.library.data.parseDecimalOrNull
import app.gympulse.feature.library.data.parseIntOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** Obergrenze für den Körpergewichtsfaktor, z. B. 0,64 für Liegestütze. */
const val MAX_BODYWEIGHT_FACTOR = 2.0

data class ExerciseFormState(
    val isLoading: Boolean = false,
    /** Eigene Übungen sind voll editierbar, Seed-Übungen nur Notiz, Pause und Gerät. */
    val isCustom: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val category: ExerciseCategory = ExerciseCategory.STRENGTH,
    val equipmentType: EquipmentType = EquipmentType.BARBELL,
    val trackingType: TrackingType = TrackingType.WEIGHT_REPS,
    val bodyweightFactor: String = "",
    val primaryMuscles: Set<MuscleGroup> = emptySet(),
    val secondaryMuscles: Set<MuscleGroup> = emptySet(),
    val instructions: String = "",
    val notes: String = "",
    val defaultRestSeconds: String = "",
    val equipmentId: String? = null,
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
    val notFound: Boolean = false,
    /** Wird true, sobald gespeichert ist; der Screen navigiert dann zurück. */
    val saved: Boolean = false,
) {
    val nameInvalid: Boolean get() = isCustom && name.isBlank()

    val bodyweightFactorInvalid: Boolean
        get() {
            if (!isCustom || equipmentType != EquipmentType.BODYWEIGHT || bodyweightFactor.isBlank()) return false
            val value = parseDecimalOrNull(bodyweightFactor)
            return value == null || value < 0.0 || value > MAX_BODYWEIGHT_FACTOR
        }

    val restInvalid: Boolean
        get() {
            if (defaultRestSeconds.isBlank()) return false
            val value = parseIntOrNull(defaultRestSeconds)
            return value == null || value < 0
        }

    val isValid: Boolean get() = !nameInvalid && !bodyweightFactorInvalid && !restInvalid

    fun toDraft(id: String?) = ExerciseDraft(
        id = id,
        name = name.trim(),
        category = category,
        equipmentType = equipmentType,
        trackingType = category.resolveTracking(trackingType),
        bodyweightFactor = if (equipmentType == EquipmentType.BODYWEIGHT) parseDecimalOrNull(bodyweightFactor) else null,
        primaryMuscles = primaryMuscles,
        secondaryMuscles = secondaryMuscles,
        instructions = instructions.lines(),
        notes = notes.trim().ifEmpty { null },
        defaultRestSeconds = parseIntOrNull(defaultRestSeconds),
        equipmentId = equipmentId,
    )
}

@HiltViewModel
class ExerciseEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LibraryRepository,
) : ViewModel() {

    private val exerciseId: String? = savedStateHandle.get<String>(EXERCISE_ID_KEY)
    private val language = Locale.getDefault().language

    private val _state = MutableStateFlow(ExerciseFormState(isLoading = exerciseId != null, isNew = exerciseId == null))
    val state: StateFlow<ExerciseFormState> = _state.asStateFlow()

    init {
        if (exerciseId != null) {
            viewModelScope.launch {
                val detail = repository.getExercise(exerciseId, language)
                _state.update { current ->
                    if (detail == null) current.copy(isLoading = false, notFound = true) else detail.exercise.toFormState(current)
                }
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }

    fun setCategory(value: ExerciseCategory) =
        _state.update { it.copy(category = value, trackingType = value.resolveTracking(it.trackingType)) }

    fun setEquipmentType(value: EquipmentType) = _state.update { it.copy(equipmentType = value) }

    fun setTrackingType(value: TrackingType) =
        _state.update { it.copy(trackingType = it.category.resolveTracking(value)) }

    fun setBodyweightFactor(value: String) = _state.update { it.copy(bodyweightFactor = value) }

    fun setInstructions(value: String) = _state.update { it.copy(instructions = value) }

    fun setNotes(value: String) = _state.update { it.copy(notes = value) }

    fun setDefaultRestSeconds(value: String) = _state.update { it.copy(defaultRestSeconds = value) }

    fun setEquipmentId(value: String?) = _state.update { it.copy(equipmentId = value) }

    /** Ein Muskel ist entweder primär oder sekundär, nie beides. */
    fun togglePrimary(muscle: MuscleGroup) = _state.update {
        if (muscle in it.primaryMuscles) {
            it.copy(primaryMuscles = it.primaryMuscles - muscle)
        } else {
            it.copy(primaryMuscles = it.primaryMuscles + muscle, secondaryMuscles = it.secondaryMuscles - muscle)
        }
    }

    fun toggleSecondary(muscle: MuscleGroup) = _state.update {
        if (muscle in it.secondaryMuscles) {
            it.copy(secondaryMuscles = it.secondaryMuscles - muscle)
        } else {
            it.copy(secondaryMuscles = it.secondaryMuscles + muscle, primaryMuscles = it.primaryMuscles - muscle)
        }
    }

    fun save() {
        val current = _state.value
        if (current.isSaving || current.isLoading || current.notFound) return
        if (!current.isValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            if (current.isCustom) {
                repository.saveCustomExercise(current.toDraft(exerciseId), language)
            } else if (exerciseId != null) {
                val draft = current.toDraft(exerciseId)
                repository.updateExerciseUserFields(exerciseId, draft.notes, draft.defaultRestSeconds, draft.equipmentId)
            }
            _state.update { it.copy(isSaving = false, saved = true) }
        }
    }

    companion object {
        const val EXERCISE_ID_KEY = "exerciseId"
    }
}

private fun Exercise.toFormState(current: ExerciseFormState) = current.copy(
    isLoading = false,
    isNew = false,
    isCustom = isCustom,
    name = name,
    category = category,
    equipmentType = equipmentType,
    trackingType = trackingType,
    bodyweightFactor = bodyweightFactor?.toString().orEmpty(),
    primaryMuscles = primaryMuscles.toSet(),
    secondaryMuscles = secondaryMuscles.toSet(),
    instructions = instructions.joinToString("\n"),
    notes = notes.orEmpty(),
    defaultRestSeconds = defaultRestSeconds?.toString().orEmpty(),
    equipmentId = equipmentId,
)
