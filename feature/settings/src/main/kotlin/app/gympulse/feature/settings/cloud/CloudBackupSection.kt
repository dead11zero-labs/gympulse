package app.gympulse.feature.settings.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.cloud.GoogleSignIn
import app.gympulse.core.data.cloud.CloudException
import app.gympulse.core.designsystem.icon.ActionIcons
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.feature.settings.R
import app.gympulse.feature.settings.SettingsDivider
import app.gympulse.feature.settings.SettingsRow
import app.gympulse.feature.settings.SettingsSection
import app.gympulse.feature.settings.SettingsSubpage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Untermenü "Cloud-Backup": Opt-in-Schalter, Google-Anmeldung und -Abmeldung, "Sichern",
 * "Wiederherstellen", automatisches Backup beim App-Start und "Löschen". Fehlt die Konfiguration
 * im Build, bleiben Schalter bzw. Anmeldung deaktiviert und ein Hinweis erklärt den Grund.
 */
@Composable
internal fun CloudBackupScreen(modifier: Modifier = Modifier, viewModel: CloudBackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requester = remember(context) { GoogleSignIn.requester(context) }

    SettingsSubpage(modifier = modifier) {
        SettingsSection(stringResource(R.string.settings_section_cloud)) {
            SettingsRow(
                headline = stringResource(R.string.cloud_backup),
                supporting = stringResource(
                    when {
                        !state.available -> R.string.cloud_backup_unavailable
                        state.enabled -> R.string.cloud_backup_summary_on
                        else -> R.string.cloud_backup_summary
                    },
                ),
                icon = Icons.Filled.Refresh,
                trailing = {
                    Switch(
                        checked = state.active,
                        onCheckedChange = viewModel::setEnabled,
                        enabled = state.available,
                    )
                },
                onClick = if (state.available) {
                    { viewModel.setEnabled(!state.enabled) }
                } else {
                    null
                },
            )
            if (state.active) {
                SettingsDivider(withIcon = true)
                val user = state.user
                if (user == null) {
                    SettingsRow(
                        headline = stringResource(R.string.cloud_sign_in),
                        supporting = stringResource(
                            if (state.configuration.googleSignInConfigured) {
                                R.string.cloud_sign_in_summary
                            } else {
                                R.string.cloud_sign_in_not_configured
                            },
                        ),
                        icon = ActionIcons.Login,
                        onClick = if (state.canSignIn) {
                            { viewModel.signIn(requester) }
                        } else {
                            null
                        },
                    )
                } else {
                    SettingsRow(
                        headline = stringResource(R.string.cloud_account),
                        supporting = user.email ?: stringResource(R.string.cloud_account_unknown_email),
                        icon = Icons.Filled.AccountCircle,
                    )
                    SettingsDivider(withIcon = true)
                    SettingsRow(
                        headline = stringResource(R.string.cloud_sign_out_action),
                        icon = ActionIcons.Logout,
                        onClick = if (state.idle) viewModel::signOut else null,
                    )
                }
            }
        }

        if (state.active && state.signedIn) {
            SettingsSection(stringResource(R.string.cloud_section_backup)) {
                val latest = state.latest
                SettingsRow(
                    headline = stringResource(R.string.cloud_backup_now),
                    supporting = latest?.updatedAt?.let { stringResource(R.string.cloud_backup_last, formatDateTime(it)) }
                        ?: stringResource(R.string.cloud_backup_none),
                    icon = ActionIcons.CloudUpload,
                    onClick = if (state.canBackup) viewModel::backupNow else null,
                )
                SettingsDivider(withIcon = true)
                SettingsRow(
                    headline = stringResource(R.string.cloud_restore),
                    supporting = stringResource(R.string.cloud_restore_summary),
                    icon = ActionIcons.CloudDownload,
                    onClick = if (state.canRestore) viewModel::requestRestore else null,
                )
                SettingsDivider(withIcon = true)
                SettingsRow(
                    headline = stringResource(R.string.cloud_auto_backup),
                    supporting = stringResource(R.string.cloud_auto_backup_summary),
                    icon = ActionIcons.CloudSync,
                    trailing = {
                        Switch(checked = state.autoBackup, onCheckedChange = viewModel::setAutoBackup)
                    },
                    onClick = { viewModel.setAutoBackup(!state.autoBackup) },
                )
                SettingsDivider(withIcon = true)
                SettingsRow(
                    headline = stringResource(R.string.cloud_delete),
                    supporting = stringResource(R.string.cloud_delete_summary),
                    icon = Icons.Filled.Delete,
                    onClick = if (state.canRestore) viewModel::requestDelete else null,
                )
            }
        }
    }

    state.working?.let { WorkingDialog(it) }

    when (state.confirmation) {
        null -> Unit
        CloudConfirmation.RESTORE -> ConfirmDialog(
            title = stringResource(R.string.cloud_restore_confirm_title),
            message = stringResource(
                R.string.cloud_restore_confirm_message,
                formatDateTime(state.latest?.exportedAt ?: state.latest?.updatedAt),
            ),
            confirmLabel = stringResource(R.string.cloud_restore_confirm_action),
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismissConfirmation,
        )
        CloudConfirmation.DELETE -> ConfirmDialog(
            title = stringResource(R.string.cloud_delete_confirm_title),
            message = stringResource(R.string.cloud_delete_confirm_message),
            confirmLabel = stringResource(R.string.cloud_delete_confirm_action),
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismissConfirmation,
        )
    }

    when (val message = state.message) {
        null -> Unit
        is CloudMessage.BackupDone -> MessageDialog(
            title = stringResource(R.string.cloud_backup_done_title),
            message = stringResource(R.string.cloud_backup_done_message),
            onDismiss = viewModel::dismissMessage,
        )
        is CloudMessage.RestoreDone -> MessageDialog(
            title = stringResource(R.string.cloud_restore_done_title),
            message = stringResource(
                R.string.cloud_restore_done_message,
                message.summary.inserted,
                message.summary.updated,
                message.summary.skipped,
            ),
            onDismiss = viewModel::dismissMessage,
        )
        CloudMessage.Deleted -> MessageDialog(
            title = stringResource(R.string.cloud_deleted_title),
            message = stringResource(R.string.cloud_deleted_message),
            onDismiss = viewModel::dismissMessage,
        )
        is CloudMessage.Failed -> MessageDialog(
            title = stringResource(R.string.cloud_failed_title),
            message = stringResource(errorMessage(message.reason)),
            onDismiss = viewModel::dismissMessage,
        )
    }
}

@Composable
private fun WorkingDialog(operation: CloudOperation) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        confirmButton = {},
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(
                        when (operation) {
                            CloudOperation.SIGN_IN -> R.string.cloud_working_sign_in
                            CloudOperation.SIGN_OUT -> R.string.cloud_working_sign_out
                            CloudOperation.BACKUP -> R.string.cloud_working_backup
                            CloudOperation.RESTORE -> R.string.cloud_working_restore
                            CloudOperation.DELETE -> R.string.cloud_working_delete
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_action_cancel)) }
        },
    )
}

@Composable
private fun MessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cloud_action_ok)) }
        },
    )
}

private fun errorMessage(reason: CloudException.Reason): Int = when (reason) {
    CloudException.Reason.NOT_CONFIGURED -> R.string.cloud_error_not_configured
    CloudException.Reason.NO_GOOGLE_ACCOUNT -> R.string.cloud_error_no_account
    CloudException.Reason.CANCELLED,
    CloudException.Reason.SIGN_IN_FAILED -> R.string.cloud_error_sign_in
    CloudException.Reason.NOT_SIGNED_IN -> R.string.cloud_error_not_signed_in
    CloudException.Reason.NETWORK -> R.string.cloud_error_network
    CloudException.Reason.NO_BACKUP -> R.string.cloud_error_no_backup
    CloudException.Reason.INVALID_BACKUP -> R.string.cloud_error_invalid
    CloudException.Reason.RESTORE_FAILED -> R.string.cloud_error_restore
    CloudException.Reason.UNKNOWN -> R.string.cloud_error_unknown
}

/** Zeitpunkt in Sprache und Zeitzone des Geräts; ohne Wert ein Gedankenstrich. */
private fun formatDateTime(instant: Instant?): String =
    instant?.let {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
            .format(it)
    } ?: "–"
