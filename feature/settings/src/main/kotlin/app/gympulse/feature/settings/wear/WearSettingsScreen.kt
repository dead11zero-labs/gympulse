package app.gympulse.feature.settings.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.designsystem.component.StatusChip
import app.gympulse.core.designsystem.icon.ActionIcons
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.feature.settings.R
import app.gympulse.feature.settings.SettingsLinkRow
import app.gympulse.feature.settings.SettingsRow
import app.gympulse.feature.settings.SettingsSection
import app.gympulse.feature.settings.SettingsSubpage

/** Eintrag in den Einstellungen; erscheint nur, wenn [app.gympulse.core.featuregate.Feature.WEAR_APP] aktiv ist. */
@Composable
internal fun WearSettingsEntry(onClick: () -> Unit, viewModel: WearSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.featureEnabled) return
    SettingsSection(stringResource(R.string.settings_section_wear)) {
        SettingsLinkRow(
            headline = stringResource(R.string.settings_wear),
            supporting = stringResource(R.string.settings_wear_entry_summary),
            icon = ActionIcons.Watch,
            onClick = onClick,
        )
    }
}

/**
 * Untermenü "Smartwatch (Wear OS)": Status der Uhr (verbunden, App installiert), Install-Hinweis mit
 * Button (Play Store auf der Uhr, Sideload-Hinweis solange es kein Play-Listing gibt) und
 * "Auf der Uhr öffnen".
 */
@Composable
internal fun WearSettingsScreen(modifier: Modifier = Modifier, viewModel: WearSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val chipIndent = Spacing.m + Sizing.iconContainer + Spacing.m

    SettingsSubpage(modifier = modifier) {
        SettingsSection(stringResource(R.string.settings_section_wear)) {
            SettingsRow(
                headline = stringResource(R.string.settings_wear),
                supporting = stringResource(
                    when (state.connection) {
                        WatchConnection.CONNECTED -> R.string.wear_status_connected
                        WatchConnection.INSTALLED_NOT_CONNECTED -> R.string.wear_status_not_connected
                        WatchConnection.NOT_DETECTED -> R.string.wear_status_not_detected
                    },
                ),
                icon = ActionIcons.Watch,
            )
            Column(
                modifier = Modifier.padding(start = chipIndent, bottom = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                StatusChip(
                    text = stringResource(
                        if (state.watch.connected) R.string.wear_chip_watch_connected else R.string.wear_chip_watch_missing,
                    ),
                    active = state.watch.connected,
                )
                StatusChip(
                    text = stringResource(
                        if (state.watch.installed) R.string.wear_chip_app_installed else R.string.wear_chip_app_missing,
                    ),
                    active = state.watch.installed,
                )
            }
        }

        if (!state.watch.installed) {
            SettingsSection(stringResource(R.string.wear_section_install)) {
                SettingsRow(
                    headline = stringResource(R.string.wear_install_title),
                    supporting = stringResource(R.string.wear_install_description),
                    icon = ActionIcons.CloudDownload,
                )
                Button(
                    onClick = viewModel::installOnWatch,
                    enabled = state.canInstall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
                ) { Text(stringResource(R.string.wear_action_install)) }
            }
        }

        SettingsSection(stringResource(R.string.wear_section_actions)) {
            SettingsRow(
                headline = stringResource(R.string.wear_open_title),
                supporting = stringResource(R.string.wear_open_summary),
                icon = ActionIcons.OpenInNew,
            )
            OutlinedButton(
                onClick = viewModel::openOnWatch,
                enabled = state.canOpen,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
            ) { Text(stringResource(R.string.wear_action_open)) }
        }
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            text = {
                Text(
                    text = stringResource(
                        when (message) {
                            WearMessage.INSTALL_STARTED -> R.string.wear_message_install_started
                            WearMessage.OPEN_STARTED -> R.string.wear_message_open_started
                            WearMessage.NO_WATCH -> R.string.wear_message_no_watch
                            WearMessage.FAILED -> R.string.wear_message_failed
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMessage) { Text(stringResource(R.string.wear_action_ok)) }
            },
        )
    }
}
