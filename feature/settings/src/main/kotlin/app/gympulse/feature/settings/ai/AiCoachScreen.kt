package app.gympulse.feature.settings.ai

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.data.ai.ModelDownloadFailure
import app.gympulse.core.data.ai.ModelDownloadState
import app.gympulse.core.designsystem.icon.ActionIcons
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.feature.settings.R
import app.gympulse.feature.settings.SettingsDivider
import app.gympulse.feature.settings.SettingsLinkRow
import app.gympulse.feature.settings.SettingsRow
import app.gympulse.feature.settings.SettingsSection
import app.gympulse.feature.settings.SettingsSubpage

/** Eintrag in den Einstellungen; die Unterseite regelt Opt-in, Download und Löschen. */
@Composable
internal fun AiCoachSettingsEntry(onClick: () -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_ai_coach)) {
        SettingsLinkRow(
            headline = stringResource(R.string.ai_coach),
            supporting = stringResource(R.string.ai_coach_entry_summary),
            icon = Icons.Filled.Star,
            onClick = onClick,
        )
    }
}

/**
 * Untermenü "KI-Coach": Opt-in-Schalter mit Dialog (Größe, nur WLAN, Speicherbedarf, Datenschutz),
 * Download mit Fortschritt und Abbrechen, Fehlerzustände und Löschen des Modells. Ohne Modell oder
 * bei Fehlern nutzt der Coach auf dem Dashboard Textbausteine.
 */
@Composable
internal fun AiCoachScreen(modifier: Modifier = Modifier, viewModel: AiCoachViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun bytes(value: Long): String = Formatter.formatShortFileSize(context, value)

    SettingsSubpage(modifier = modifier) {
        SettingsSection(stringResource(R.string.settings_section_ai_coach)) {
            SettingsRow(
                headline = stringResource(R.string.ai_coach),
                supporting = stringResource(if (state.enabled) R.string.ai_coach_summary_on else R.string.ai_coach_summary_off),
                icon = Icons.Filled.Star,
                trailing = { Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled) },
                onClick = { viewModel.setEnabled(!state.enabled) },
            )
        }
        Text(
            text = stringResource(R.string.ai_coach_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.m),
        )

        if (state.showModelSection) {
            SettingsSection(stringResource(R.string.ai_coach_section_model)) {
                ModelRows(state = state, viewModel = viewModel, bytes = ::bytes)
            }
            state.notice?.let { notice ->
                Text(
                    text = when (notice) {
                        AiCoachNotice.NotConfigured -> stringResource(R.string.ai_coach_notice_not_configured)
                        is AiCoachNotice.InsufficientStorage -> stringResource(
                            R.string.ai_coach_notice_storage,
                            bytes(notice.requiredBytes),
                            bytes(notice.availableBytes),
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.m),
                )
            }
        }
    }

    when (state.dialog) {
        null -> Unit
        AiCoachDialog.OPT_IN -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(R.string.ai_coach_optin_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.ai_coach_optin_local), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.ai_coach_optin_download, bytes(state.downloadSizeBytes)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.ai_coach_optin_storage, bytes(state.requiredFreeBytes)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(stringResource(R.string.ai_coach_optin_wifi), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.ai_coach_optin_medical), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmOptIn) { Text(stringResource(R.string.ai_coach_optin_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.settings_action_cancel)) }
            },
        )
        AiCoachDialog.DELETE_MODEL -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(R.string.ai_coach_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.ai_coach_delete_message, bytes((state.model as? ModelDownloadState.Ready)?.sizeBytes ?: 0L)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteModel) { Text(stringResource(R.string.ai_coach_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.settings_action_cancel)) }
            },
        )
    }
}

@Composable
private fun ModelRows(state: AiCoachUiState, viewModel: AiCoachViewModel, bytes: (Long) -> String) {
    when (val model = state.model) {
        ModelDownloadState.NotDownloaded -> SettingsRow(
            headline = stringResource(R.string.ai_coach_model_download),
            supporting = stringResource(R.string.ai_coach_model_not_downloaded, bytes(state.downloadSizeBytes)),
            icon = ActionIcons.CloudDownload,
            onClick = if (state.canStartDownload) viewModel::startDownload else null,
        )
        is ModelDownloadState.Downloading -> {
            SettingsRow(
                headline = stringResource(R.string.ai_coach_model_downloading),
                supporting = if (model.waitingForNetwork) {
                    stringResource(R.string.ai_coach_model_waiting_wifi)
                } else {
                    stringResource(
                        R.string.ai_coach_model_progress,
                        bytes(model.downloadedBytes),
                        bytes(model.totalBytes),
                    )
                },
                icon = ActionIcons.CloudDownload,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.m, vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                val progress = model.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                OutlinedButton(onClick = viewModel::cancelDownload) {
                    Text(stringResource(R.string.ai_coach_model_cancel))
                }
            }
        }
        is ModelDownloadState.Ready -> {
            SettingsRow(
                headline = stringResource(R.string.ai_coach_model_ready),
                supporting = stringResource(R.string.ai_coach_model_ready_size, bytes(model.sizeBytes)),
                icon = ActionIcons.CloudDownload,
            )
            SettingsDivider(withIcon = true)
            SettingsRow(
                headline = stringResource(R.string.ai_coach_model_delete),
                supporting = stringResource(R.string.ai_coach_model_delete_summary),
                icon = Icons.Filled.Delete,
                onClick = viewModel::requestDeleteModel,
            )
        }
        is ModelDownloadState.Failed -> SettingsRow(
            headline = stringResource(R.string.ai_coach_model_failed),
            supporting = stringResource(failureMessage(model.reason)),
            icon = ActionIcons.CloudDownload,
            onClick = if (state.canStartDownload) viewModel::startDownload else null,
        )
    }
}

private fun failureMessage(reason: ModelDownloadFailure): Int = when (reason) {
    ModelDownloadFailure.INSUFFICIENT_STORAGE -> R.string.ai_coach_error_storage
    ModelDownloadFailure.HASH_MISMATCH,
    ModelDownloadFailure.SIZE_MISMATCH -> R.string.ai_coach_error_corrupt
    ModelDownloadFailure.HTTP_ERROR,
    ModelDownloadFailure.NETWORK -> R.string.ai_coach_error_network
    ModelDownloadFailure.NOT_CONFIGURED -> R.string.ai_coach_notice_not_configured
    ModelDownloadFailure.UNKNOWN -> R.string.ai_coach_error_unknown
}
