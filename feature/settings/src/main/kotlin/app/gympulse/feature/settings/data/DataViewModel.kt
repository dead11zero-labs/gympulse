package app.gympulse.feature.settings.data

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.backup.BackupException
import app.gympulse.core.backup.BackupManager
import app.gympulse.core.backup.ConflictStrategy
import app.gympulse.core.backup.ImportPlan
import app.gympulse.core.backup.ImportPreview
import app.gympulse.core.backup.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal enum class DataOperation { EXPORT, READ, IMPORT }

/** Zustand des Abschnitts "Daten"; außer [Idle] zeigt jeder Zustand einen Dialog. */
internal sealed interface DataUiState {
    data object Idle : DataUiState
    data class Working(val operation: DataOperation) : DataUiState
    data class Preview(
        val preview: ImportPreview,
        val strategy: ConflictStrategy,
        val applySettings: Boolean,
        val plan: ImportPlan,
    ) : DataUiState {
        val canImport: Boolean
            get() = plan.missingReferences == 0 && (plan.changes > 0 || (applySettings && preview.hasSettings))
    }
    data class ExportDone(val rows: Int) : DataUiState
    data class ImportDone(val result: ImportResult) : DataUiState
    data class Failed(val operation: DataOperation, val reason: BackupException.Reason) : DataUiState
}

@HiltViewModel
internal class DataViewModel @Inject constructor(
    private val backupManager: BackupManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DataUiState>(DataUiState.Idle)
    val uiState: StateFlow<DataUiState> = _uiState.asStateFlow()

    fun export(uri: Uri) = launchOperation(DataOperation.EXPORT) {
        DataUiState.ExportDone(backupManager.exportTo(uri).rows)
    }

    fun openImport(uri: Uri) = launchOperation(DataOperation.READ) {
        val preview = backupManager.readFrom(uri)
        val strategy = ConflictStrategy.NEWER_WINS
        DataUiState.Preview(preview, strategy, applySettings = false, plan = plan(preview, strategy))
    }

    fun setStrategy(strategy: ConflictStrategy) {
        val current = _uiState.value as? DataUiState.Preview ?: return
        viewModelScope.launch {
            val plan = plan(current.preview, strategy)
            // Nur übernehmen, wenn die Vorschau noch offen ist.
            if (_uiState.value is DataUiState.Preview) {
                _uiState.value = (_uiState.value as DataUiState.Preview).copy(strategy = strategy, plan = plan)
            }
        }
    }

    fun setApplySettings(apply: Boolean) {
        val current = _uiState.value as? DataUiState.Preview ?: return
        _uiState.value = current.copy(applySettings = apply)
    }

    fun confirmImport() {
        val current = _uiState.value as? DataUiState.Preview ?: return
        if (!current.canImport) return
        launchOperation(DataOperation.IMPORT) {
            DataUiState.ImportDone(
                backupManager.import(current.preview.file, current.strategy, current.applySettings),
            )
        }
    }

    fun dismiss() {
        if (_uiState.value !is DataUiState.Working) _uiState.value = DataUiState.Idle
    }

    private suspend fun plan(preview: ImportPreview, strategy: ConflictStrategy): ImportPlan =
        withContext(Dispatchers.Default) { preview.plan(strategy) }

    private fun launchOperation(operation: DataOperation, block: suspend () -> DataUiState) {
        if (_uiState.value is DataUiState.Working) return
        _uiState.value = DataUiState.Working(operation)
        viewModelScope.launch {
            _uiState.value = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                DataUiState.Failed(operation, e.reason)
            } catch (e: Exception) {
                DataUiState.Failed(operation, BackupException.Reason.IO)
            }
        }
    }
}
