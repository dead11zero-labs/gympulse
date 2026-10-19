package app.gympulse.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.model.Equipment
import app.gympulse.core.model.EquipmentTemplate
import app.gympulse.core.model.Exercise
import app.gympulse.feature.library.data.EquipmentDraft
import app.gympulse.feature.library.data.LibraryRepository
import app.gympulse.feature.library.data.SettingDraft
import app.gympulse.feature.library.data.normalizeForSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

private const val STOP_TIMEOUT_MILLIS = 5_000L

// ---------------------------------------------------------------- Geräteliste

data class EquipmentListUiState(
    val isLoading: Boolean = true,
    val equipment: List<Equipment> = emptyList(),
)

@HiltViewModel
class EquipmentListViewModel @Inject constructor(
    repository: LibraryRepository,
) : ViewModel() {
    val uiState: StateFlow<EquipmentListUiState> = repository.observeEquipment()
        .map { EquipmentListUiState(isLoading = false, equipment = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), EquipmentListUiState())
}

// ------------------------------------------------------------ Vorlagenauswahl

data class EquipmentTemplatesUiState(
    val isLoading: Boolean = true,
    val templates: List<EquipmentTemplate> = emptyList(),
)

/** Filtert Gerätevorlagen nach Name (ohne Beachtung von Groß-/Kleinschreibung und Akzenten). */
fun filterTemplates(templates: List<EquipmentTemplate>, query: String): List<EquipmentTemplate> {
    val tokens = normalizeForSearch(query).split(' ').filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return templates
    return templates.filter { template ->
        val name = normalizeForSearch(template.name)
        tokens.all { name.contains(it) }
    }
}

@HiltViewModel
class EquipmentTemplatesViewModel @Inject constructor(
    private val repository: LibraryRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    private val _state = MutableStateFlow(EquipmentTemplatesUiState())
    private var allTemplates: List<EquipmentTemplate> = emptyList()

    val query: StateFlow<String> = _query.asStateFlow()
    val uiState: StateFlow<EquipmentTemplatesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            allTemplates = repository.templates(Locale.getDefault().language).sortedBy { it.name.lowercase() }
            publish()
        }
    }

    fun setQuery(text: String) {
        _query.value = text
        publish()
    }

    private fun publish() {
        _state.value = EquipmentTemplatesUiState(
            isLoading = false,
            templates = filterTemplates(allTemplates, _query.value),
        )
    }
}

// --------------------------------------------------------------- Gerät bearbeiten

/** Eine Zeile "Schlüssel: Wert" im Formular; [rowId] hält die Zeile in der UI stabil. */
data class SettingRow(
    val rowId: Long,
    /** ID der gespeicherten Einstellung, null bei neuen Zeilen. */
    val id: String? = null,
    val key: String = "",
    val value: String = "",
    /** Platzhalter aus der Vorlage, z. B. "z. B. 5". */
    val hint: String = "",
)

data class EquipmentFormState(
    val isLoading: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val location: String = "",
    val notes: String = "",
    val templateKey: String? = null,
    val settings: List<SettingRow> = emptyList(),
    /** Übungen, die diesem Gerät zugeordnet sind. */
    val linkedExercises: List<Exercise> = emptyList(),
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
    val notFound: Boolean = false,
    /** Wird true nach Speichern oder Löschen; der Screen navigiert dann zurück. */
    val finished: Boolean = false,
) {
    val nameInvalid: Boolean get() = name.isBlank()
}

@HiltViewModel
class EquipmentEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LibraryRepository,
) : ViewModel() {

    private val equipmentId: String? = savedStateHandle.get<String>(EQUIPMENT_ID_KEY)
    private val templateKey: String? = savedStateHandle.get<String>(TEMPLATE_KEY_KEY)
    private val language = Locale.getDefault().language
    private var nextRowId = 0L

    private val _state = MutableStateFlow(EquipmentFormState(isLoading = true, isNew = equipmentId == null))
    val state: StateFlow<EquipmentFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
        if (equipmentId != null) {
            viewModelScope.launch {
                repository.observeExercises(language).collect { all ->
                    val linked = all.map { it.exercise }.filter { it.equipmentId == equipmentId }
                    _state.update { it.copy(linkedExercises = linked) }
                }
            }
        }
    }

    private suspend fun load() {
        if (equipmentId != null) {
            val equipment = repository.observeEquipmentById(equipmentId).first()
            _state.update {
                if (equipment == null) {
                    it.copy(isLoading = false, notFound = true)
                } else {
                    it.copy(
                        isLoading = false,
                        isNew = false,
                        name = equipment.name,
                        location = equipment.location.orEmpty(),
                        notes = equipment.notes.orEmpty(),
                        templateKey = equipment.templateKey,
                        settings = equipment.settings.map { setting ->
                            SettingRow(newRowId(), setting.id, setting.key, setting.value)
                        },
                    )
                }
            }
        } else {
            val template = templateKey?.let { key -> repository.templates(language).firstOrNull { it.key == key } }
            _state.update {
                it.copy(
                    isLoading = false,
                    name = template?.name.orEmpty(),
                    templateKey = template?.key,
                    settings = template?.settingKeys.orEmpty().map { setting ->
                        SettingRow(newRowId(), key = setting.name, hint = setting.valueHint)
                    },
                )
            }
        }
    }

    private fun newRowId(): Long = nextRowId++

    fun setName(value: String) = _state.update { it.copy(name = value) }

    fun setLocation(value: String) = _state.update { it.copy(location = value) }

    fun setNotes(value: String) = _state.update { it.copy(notes = value) }

    fun addSetting() = _state.update { it.copy(settings = it.settings + SettingRow(newRowId())) }

    fun removeSetting(rowId: Long) = _state.update { state ->
        state.copy(settings = state.settings.filterNot { it.rowId == rowId })
    }

    fun setSettingKey(rowId: Long, key: String) = updateRow(rowId) { it.copy(key = key) }

    fun setSettingValue(rowId: Long, value: String) = updateRow(rowId) { it.copy(value = value) }

    private fun updateRow(rowId: Long, transform: (SettingRow) -> SettingRow) = _state.update { state ->
        state.copy(settings = state.settings.map { if (it.rowId == rowId) transform(it) else it })
    }

    fun save() {
        val current = _state.value
        if (current.isSaving || current.isLoading || current.notFound) return
        if (current.nameInvalid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            repository.saveEquipment(
                EquipmentDraft(
                    id = equipmentId,
                    name = current.name,
                    location = current.location,
                    notes = current.notes,
                    templateKey = current.templateKey,
                    settings = current.settings.map { SettingDraft(it.id, it.key, it.value) },
                ),
            )
            _state.update { it.copy(isSaving = false, finished = true) }
        }
    }

    fun delete() {
        val id = equipmentId ?: return
        viewModelScope.launch {
            repository.deleteEquipment(id)
            _state.update { it.copy(finished = true) }
        }
    }

    companion object {
        const val EQUIPMENT_ID_KEY = "equipmentId"
        const val TEMPLATE_KEY_KEY = "templateKey"
    }
}
