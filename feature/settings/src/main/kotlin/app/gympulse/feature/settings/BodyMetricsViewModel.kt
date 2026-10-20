package app.gympulse.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.data.repository.BodyMeasurementRepository
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.model.BodyMeasurement
import app.gympulse.core.model.MeasurementType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class BodyMetricsUiState(
    val selectedType: MeasurementType = MeasurementType.WEIGHT,
    /** Messwerte des gewählten Typs, neueste zuerst (Verlaufsliste). */
    val history: List<BodyMeasurement> = emptyList(),
    /** Dieselben Messwerte, älteste zuerst (Diagramm). */
    val chart: List<BodyMeasurement> = emptyList(),
    /** Differenz des neuesten zum vorherigen Messwert; null, wenn es nur einen gibt. */
    val change: Double? = null,
    /** Einstiegspunkt zur Foto-Galerie; hängt am Feature [Feature.PROGRESS_PHOTOS]. */
    val photosEnabled: Boolean = false,
)

@HiltViewModel
internal class BodyMetricsViewModel @Inject constructor(
    private val repository: BodyMeasurementRepository,
    featureGate: FeatureGate,
) : ViewModel() {

    private val selectedType = MutableStateFlow(MeasurementType.WEIGHT)

    val uiState: StateFlow<BodyMetricsUiState> = combine(
        repository.observeAll(),
        selectedType,
        featureGate.observe(Feature.PROGRESS_PHOTOS),
    ) { all, type, photosEnabled ->
        val history = all.filter { it.type == type }.sortedByDescending { it.measuredAt }
        BodyMetricsUiState(
            selectedType = type,
            history = history,
            chart = history.asReversed(),
            change = if (history.size >= 2) history[0].value - history[1].value else null,
            photosEnabled = photosEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BodyMetricsUiState())

    fun selectType(type: MeasurementType) {
        selectedType.value = type
    }

    /** Speichert alle ausgefüllten Werte als gemeinsame Messung. */
    fun add(values: Map<MeasurementType, Double>) = viewModelScope.launch {
        if (values.isNotEmpty()) repository.record(values)
    }

    fun updateValue(id: String, value: Double) = viewModelScope.launch { repository.updateValue(id, value) }

    fun delete(id: String) = viewModelScope.launch { repository.delete(id) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
