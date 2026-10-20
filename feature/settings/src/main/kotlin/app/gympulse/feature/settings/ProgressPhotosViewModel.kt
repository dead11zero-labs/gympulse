package app.gympulse.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.data.repository.ProgressPhotoRepository
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.model.ProgressPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

internal data class ProgressPhotosUiState(
    val enabled: Boolean = true,
    /** Neueste zuerst. */
    val photos: List<ProgressPhoto> = emptyList(),
)

@HiltViewModel
internal class ProgressPhotosViewModel @Inject constructor(
    private val repository: ProgressPhotoRepository,
    private val store: ProgressPhotoStore,
    featureGate: FeatureGate,
) : ViewModel() {

    val uiState: StateFlow<ProgressPhotosUiState> = combine(
        featureGate.observe(Feature.PROGRESS_PHOTOS),
        repository.observeAll(),
    ) { enabled, photos ->
        ProgressPhotosUiState(enabled = enabled, photos = photos.sortedByDescending { it.takenAt })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProgressPhotosUiState())

    fun fileOf(photo: ProgressPhoto): File = store.file(photo.fileName)

    fun fileNamed(fileName: String): File = store.file(fileName)

    fun newCapture(): PendingCapture = store.newCapture()

    suspend fun import(source: Uri): String? = store.import(source)

    /** Legt den Datenbankeintrag zu einer bereits gespeicherten Bilddatei an. */
    fun save(fileName: String, note: String) = viewModelScope.launch { repository.add(fileName, note) }

    /** Verwirft eine Aufnahme, die nicht gespeichert wird. */
    fun discard(fileName: String) = viewModelScope.launch { store.delete(fileName) }

    fun delete(photo: ProgressPhoto) = viewModelScope.launch {
        repository.delete(photo.id)
        store.delete(photo.fileName)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
