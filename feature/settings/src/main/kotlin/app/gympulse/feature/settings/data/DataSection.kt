package app.gympulse.feature.settings.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.backup.BackupException
import app.gympulse.core.backup.BackupFormat
import app.gympulse.core.backup.BackupManager
import app.gympulse.core.backup.BackupTables
import app.gympulse.core.backup.ConflictStrategy
import app.gympulse.core.backup.TablePlan
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.feature.settings.R
import app.gympulse.feature.settings.SettingsDivider
import app.gympulse.feature.settings.SettingsRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

/** MIME-Typen, unter denen Dateimanager und Cloud-Anbieter JSON-Dateien anbieten. */
private val IMPORT_MIME_TYPES = arrayOf(BackupFormat.MIME_TYPE, "text/*", "application/octet-stream")

/**
 * Zeilen "Exportieren" und "Importieren" im Abschnitt "Daten" samt Dialogen. Dateien laufen über
 * das Storage Access Framework, daher braucht die App keine Speicherberechtigung.
 */
@Composable
internal fun DataSettingsRows(viewModel: DataViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFormat.MIME_TYPE),
    ) { uri -> uri?.let(viewModel::export) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::openImport)
    }
    val idle = state !is DataUiState.Working

    SettingsRow(
        headline = stringResource(R.string.data_export),
        supporting = stringResource(R.string.data_export_summary),
        onClick = if (idle) {
            { exportLauncher.launch(BackupManager.suggestedFileName()) }
        } else {
            null
        },
    )
    SettingsDivider()
    SettingsRow(
        headline = stringResource(R.string.data_import),
        supporting = stringResource(R.string.data_import_summary),
        onClick = if (idle) {
            { importLauncher.launch(IMPORT_MIME_TYPES) }
        } else {
            null
        },
    )

    when (val current = state) {
        DataUiState.Idle -> Unit
        is DataUiState.Working -> WorkingDialog(current.operation)
        is DataUiState.Preview -> ImportPreviewDialog(
            state = current,
            onStrategyChange = viewModel::setStrategy,
            onApplySettingsChange = viewModel::setApplySettings,
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::dismiss,
        )
        is DataUiState.ExportDone -> MessageDialog(
            title = stringResource(R.string.data_export_done_title),
            message = pluralStringResource(R.plurals.data_export_done_message, current.rows, current.rows),
            onDismiss = viewModel::dismiss,
        )
        is DataUiState.ImportDone -> MessageDialog(
            title = stringResource(R.string.data_import_done_title),
            message = buildString {
                append(
                    stringResource(
                        R.string.data_import_done_message,
                        current.result.inserted,
                        current.result.updated,
                        current.result.skipped,
                    ),
                )
                if (current.result.settingsApplied) {
                    append("\n")
                    append(stringResource(R.string.data_import_done_settings))
                }
            },
            onDismiss = viewModel::dismiss,
        )
        is DataUiState.Failed -> MessageDialog(
            title = stringResource(
                if (current.operation == DataOperation.EXPORT) R.string.data_export_failed_title else R.string.data_import_failed_title,
            ),
            message = stringResource(errorMessage(current.reason)),
            onDismiss = viewModel::dismiss,
        )
    }
}

@Composable
private fun WorkingDialog(operation: DataOperation) {
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
                            DataOperation.EXPORT -> R.string.data_working_export
                            DataOperation.READ -> R.string.data_working_read
                            DataOperation.IMPORT -> R.string.data_working_import
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.data_action_ok)) }
        },
    )
}

@Composable
private fun ImportPreviewDialog(
    state: DataUiState.Preview,
    onStrategyChange: (ConflictStrategy) -> Unit,
    onApplySettingsChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val file = state.preview.file
    val plan = state.plan
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.data_preview_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(
                    text = stringResource(R.string.data_preview_source, formatExportDate(file.exportedAt), file.appVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.preview.isNewerSchema) {
                    NoticeCard(stringResource(R.string.data_preview_newer_version))
                }
                if (plan.missingReferences > 0) {
                    NoticeCard(
                        pluralStringResource(
                            R.plurals.data_preview_missing_references,
                            plan.missingReferences,
                            plan.missingReferences,
                        ),
                    )
                }

                val tables = plan.tables.filter { it.total > 0 }
                if (tables.isEmpty()) {
                    Text(stringResource(R.string.data_preview_empty), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column {
                        tables.forEachIndexed { index, table ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            TableCountRow(table)
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.data_preview_conflicts),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.selectableGroup()) {
                    ConflictStrategy.entries.forEach { strategy ->
                        StrategyRow(
                            strategy = strategy,
                            selected = strategy == state.strategy,
                            onSelect = { onStrategyChange(strategy) },
                        )
                    }
                }

                if (state.preview.hasSettings) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Spacing.xxl)
                            .toggleable(
                                value = state.applySettings,
                                role = Role.Checkbox,
                                onValueChange = onApplySettingsChange,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = state.applySettings, onCheckedChange = null)
                        Text(
                            text = stringResource(R.string.data_preview_apply_settings),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = Spacing.m),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = state.canImport) {
                Text(stringResource(R.string.data_action_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_action_cancel)) }
        },
    )
}

@Composable
private fun TableCountRow(table: TablePlan) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tableLabel(table.spec.key), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.data_preview_counts, table.inserts, table.updates, table.skipped),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(table.total.toString(), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun StrategyRow(strategy: ConflictStrategy, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.xxl)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = Spacing.m)) {
            Text(
                text = stringResource(
                    when (strategy) {
                        ConflictStrategy.NEWER_WINS -> R.string.data_strategy_newer
                        ConflictStrategy.KEEP_EXISTING -> R.string.data_strategy_keep
                        ConflictStrategy.OVERWRITE -> R.string.data_strategy_overwrite
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(
                    when (strategy) {
                        ConflictStrategy.NEWER_WINS -> R.string.data_strategy_newer_summary
                        ConflictStrategy.KEEP_EXISTING -> R.string.data_strategy_keep_summary
                        ConflictStrategy.OVERWRITE -> R.string.data_strategy_overwrite_summary
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Hinweis in tonaler Fehlerfläche (Paar errorContainer/onErrorContainer). */
@Composable
private fun NoticeCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            // Dekorativ: der Text daneben trägt die Information.
            Icon(Icons.Outlined.Warning, contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun tableLabel(key: String): String = when (key) {
    BackupTables.USER_PROFILE -> stringResource(R.string.data_table_profile)
    BackupTables.BODY_MEASUREMENT -> stringResource(R.string.data_table_body_measurements)
    BackupTables.EQUIPMENT -> stringResource(R.string.data_table_equipment)
    BackupTables.EQUIPMENT_SETTING -> stringResource(R.string.data_table_equipment_settings)
    BackupTables.EXERCISE -> stringResource(R.string.data_table_custom_exercises)
    BackupTables.EXERCISE_SEED_STATE -> stringResource(R.string.data_table_library_changes)
    BackupTables.EXERCISE_TRANSLATION -> stringResource(R.string.data_table_exercise_names)
    BackupTables.EXERCISE_MUSCLE -> stringResource(R.string.data_table_exercise_muscles)
    BackupTables.WORKOUT_PLAN -> stringResource(R.string.data_table_plans)
    BackupTables.PLAN_EXERCISE -> stringResource(R.string.data_table_plan_exercises)
    BackupTables.SCHEDULE_RULE -> stringResource(R.string.data_table_schedule)
    BackupTables.SCHEDULE_OVERRIDE -> stringResource(R.string.data_table_schedule_changes)
    BackupTables.WORKOUT_SESSION -> stringResource(R.string.data_table_workouts)
    BackupTables.SESSION_EXERCISE -> stringResource(R.string.data_table_workout_exercises)
    BackupTables.SET_ENTRY -> stringResource(R.string.data_table_sets)
    BackupTables.PERSONAL_RECORD -> stringResource(R.string.data_table_records)
    else -> key
}

private fun errorMessage(reason: BackupException.Reason): Int = when (reason) {
    BackupException.Reason.IO -> R.string.data_error_io
    BackupException.Reason.INVALID_FILE -> R.string.data_error_invalid
    BackupException.Reason.MISSING_REFERENCES -> R.string.data_error_missing_references
    BackupException.Reason.WRITE_FAILED -> R.string.data_error_write
}

/** Exportzeitpunkt in der Sprache und Zeitzone des Geräts; unlesbare Werte unverändert. */
private fun formatExportDate(value: String): String = try {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(value))
} catch (_: DateTimeParseException) {
    value
}
