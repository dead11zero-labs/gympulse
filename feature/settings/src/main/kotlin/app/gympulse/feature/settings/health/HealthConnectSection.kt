package app.gympulse.feature.settings.health

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.data.health.HealthAvailability
import app.gympulse.core.designsystem.component.StatusChip
import app.gympulse.core.designsystem.icon.ActionIcons
import app.gympulse.core.designsystem.icon.GymPulseIcons
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.health.HealthConnectPermissions
import app.gympulse.feature.settings.R
import app.gympulse.feature.settings.SettingsDivider
import app.gympulse.feature.settings.SettingsRow
import app.gympulse.feature.settings.SettingsSection
import app.gympulse.feature.settings.SettingsSubpage

/**
 * Untermenü "Health Connect": Hauptschalter mit Berechtigungsdialog, Hinweis auf fehlende
 * Berechtigungen und je ein Schalter pro Datentyp (Workouts schreiben, Herzfrequenz, Schritte,
 * Kalorien, Schlaf). Ist der Client nicht verfügbar, bleibt der Hauptschalter deaktiviert und ein
 * Hinweis erklärt den Grund. Apps wie Samsung Health liefern ihre Daten über Health Connect; eine
 * eigene Anbindung gibt es nicht.
 */
@Composable
internal fun HealthConnectScreen(modifier: Modifier = Modifier, viewModel: HealthConnectViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(HealthConnectPermissions.requestContract()) { granted ->
        viewModel.onPermissionResult(granted)
    }
    val requestPermissions = { permissionLauncher.launch(viewModel.permissions) }
    val togglePermissionLauncher = rememberLauncherForActivityResult(HealthConnectPermissions.requestContract()) { granted ->
        viewModel.onTogglePermissionResult(granted)
    }

    // Berechtigungen können in Health Connect jederzeit geändert werden.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val onToggle: (Boolean) -> Unit = { checked ->
        if (checked && !state.permissionsGranted) requestPermissions() else viewModel.setEnabled(checked)
    }
    val installable = state.availability == HealthAvailability.NOT_INSTALLED ||
        state.availability == HealthAvailability.UPDATE_REQUIRED

    val status = stringResource(
        when (state.availability) {
            HealthAvailability.AVAILABLE ->
                if (state.active) R.string.health_connect_summary_on else R.string.settings_health_connect_summary
            HealthAvailability.NOT_INSTALLED -> R.string.health_connect_not_installed
            HealthAvailability.UPDATE_REQUIRED -> R.string.health_connect_update_required
            HealthAvailability.UNSUPPORTED -> R.string.health_connect_unsupported
        },
    )

    SettingsSubpage(modifier = modifier) {
        SettingsSection(stringResource(R.string.settings_section_health)) {
            SettingsRow(
                headline = stringResource(R.string.settings_health_connect),
                supporting = if (state.availability == HealthAvailability.UNSUPPORTED) {
                    status
                } else {
                    status + "\n" + stringResource(R.string.health_connect_sources_hint)
                },
                icon = Icons.Filled.Favorite,
                trailing = {
                    Switch(
                        checked = state.available && state.enabled,
                        onCheckedChange = onToggle,
                        enabled = state.available,
                    )
                },
                onClick = when {
                    state.available -> {
                        { onToggle(!state.enabled) }
                    }
                    installable -> {
                        { openPlayStore(context) }
                    }
                    else -> null
                },
            )

            // Zustand als Chip unter der Zeile, eingerückt auf Höhe des Texts.
            Box(
                modifier = Modifier
                    .padding(start = Spacing.m + Sizing.iconContainer + Spacing.m, bottom = Spacing.s),
            ) {
                StatusChip(
                    text = stringResource(
                        when {
                            state.active -> R.string.health_connect_chip_connected
                            !state.available -> R.string.health_connect_chip_unavailable
                            state.enabled -> R.string.health_connect_chip_permission_missing
                            else -> R.string.health_connect_chip_off
                        },
                    ),
                    active = state.active,
                )
            }

            if (state.available && state.enabled && !state.permissionsGranted) {
                SettingsDivider(withIcon = true)
                SettingsRow(
                    headline = stringResource(R.string.health_connect_permissions),
                    supporting = stringResource(R.string.health_connect_permissions_missing),
                    icon = Icons.Filled.Lock,
                    onClick = requestPermissions,
                )
            }
        }

        if (state.active) {
            SettingsSection(stringResource(R.string.health_connect_section_data)) {
                HealthToggle.entries.forEachIndexed { index, toggle ->
                    if (index > 0) SettingsDivider(withIcon = true)
                    val on = state.isOn(toggle)
                    val onChange: (Boolean) -> Unit = { checked ->
                        viewModel.toggle(toggle, checked)?.let { togglePermissionLauncher.launch(it) }
                    }
                    SettingsRow(
                        headline = stringResource(toggle.title()),
                        supporting = stringResource(toggle.summary()),
                        icon = toggle.icon(),
                        trailing = { Switch(checked = on, onCheckedChange = onChange) },
                        onClick = { onChange(!on) },
                    )
                }
            }
        }
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            text = {
                Text(
                    text = stringResource(
                        when (message) {
                            HealthMessage.PERMISSIONS_DENIED -> R.string.health_connect_permissions_denied
                            HealthMessage.METRIC_DENIED -> R.string.health_connect_metric_denied
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMessage) { Text(stringResource(R.string.health_connect_action_ok)) }
            },
        )
    }
}

private fun HealthToggle.title(): Int = when (this) {
    HealthToggle.WORKOUTS -> R.string.health_connect_workouts
    HealthToggle.HEART_RATE -> R.string.health_connect_heart_rate
    HealthToggle.STEPS -> R.string.health_connect_steps
    HealthToggle.CALORIES -> R.string.health_connect_calories
    HealthToggle.SLEEP -> R.string.health_connect_sleep
}

private fun HealthToggle.summary(): Int = when (this) {
    HealthToggle.WORKOUTS -> R.string.health_connect_workouts_summary
    HealthToggle.HEART_RATE -> R.string.health_connect_heart_rate_summary
    HealthToggle.STEPS -> R.string.health_connect_steps_summary
    HealthToggle.CALORIES -> R.string.health_connect_calories_summary
    HealthToggle.SLEEP -> R.string.health_connect_sleep_summary
}

private fun HealthToggle.icon(): ImageVector = when (this) {
    HealthToggle.WORKOUTS -> GymPulseIcons.FitnessCenterOutlined
    HealthToggle.HEART_RATE -> ActionIcons.MonitorHeart
    HealthToggle.STEPS -> ActionIcons.Footprint
    HealthToggle.CALORIES -> ActionIcons.LocalFireDepartment
    HealthToggle.SLEEP -> ActionIcons.Bedtime
}

/** Öffnet Health Connect im Play Store (Installation bzw. Update auf Android 9 bis 13). */
private fun openPlayStore(context: Context) {
    try {
        context.startActivity(HealthConnectPermissions.playStoreIntent())
    } catch (_: ActivityNotFoundException) {
        // Kein Play Store vorhanden: der Hinweistext bleibt die einzige Information.
    }
}

