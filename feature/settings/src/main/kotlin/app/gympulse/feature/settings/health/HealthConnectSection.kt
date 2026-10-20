package app.gympulse.feature.settings.health

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.data.health.HealthAvailability
import app.gympulse.core.data.health.HealthWeight
import app.gympulse.core.health.HealthConnectPermissions
import app.gympulse.feature.settings.R
import app.gympulse.feature.settings.SettingsDivider
import app.gympulse.feature.settings.SettingsRow
import app.gympulse.feature.settings.formatNumber
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Zeilen für Health Connect im Abschnitt "Gesundheitsdaten": Schalter mit Berechtigungsdialog,
 * Hinweis auf fehlende Berechtigungen und Übernahme des Körpergewichts. Ist der Client nicht
 * verfügbar, bleibt der Schalter deaktiviert und ein Hinweis erklärt den Grund.
 */
@Composable
internal fun HealthConnectSettingsRows(viewModel: HealthConnectViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(HealthConnectPermissions.requestContract()) { granted ->
        viewModel.onPermissionResult(granted)
    }
    val requestPermissions = { permissionLauncher.launch(viewModel.permissions) }

    // Berechtigungen können in Health Connect jederzeit geändert werden.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val onToggle: (Boolean) -> Unit = { checked ->
        if (checked && !state.permissionsGranted) requestPermissions() else viewModel.setEnabled(checked)
    }
    val installable = state.availability == HealthAvailability.NOT_INSTALLED ||
        state.availability == HealthAvailability.UPDATE_REQUIRED

    SettingsRow(
        headline = stringResource(R.string.settings_health_connect),
        supporting = stringResource(
            when (state.availability) {
                HealthAvailability.AVAILABLE ->
                    if (state.active) R.string.health_connect_summary_on else R.string.settings_health_connect_summary
                HealthAvailability.NOT_INSTALLED -> R.string.health_connect_not_installed
                HealthAvailability.UPDATE_REQUIRED -> R.string.health_connect_update_required
                HealthAvailability.UNSUPPORTED -> R.string.health_connect_unsupported
            },
        ),
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

    if (state.available && state.enabled && !state.permissionsGranted) {
        SettingsDivider(withIcon = true)
        SettingsRow(
            headline = stringResource(R.string.health_connect_permissions),
            supporting = stringResource(R.string.health_connect_permissions_missing),
            icon = Icons.Filled.Lock,
            onClick = requestPermissions,
        )
    }

    if (state.active) {
        SettingsDivider(withIcon = true)
        val weight = state.latestWeight
        SettingsRow(
            headline = stringResource(R.string.health_connect_weight),
            supporting = if (weight != null) {
                stringResource(R.string.health_connect_weight_value, formatNumber(weight.weightKg), formatDate(weight))
            } else {
                stringResource(R.string.health_connect_weight_none)
            },
            icon = Icons.Filled.Face,
            trailing = if (weight != null) {
                {
                    TextButton(onClick = viewModel::importWeight) {
                        Text(stringResource(R.string.health_connect_weight_import))
                    }
                }
            } else {
                null
            },
        )
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            text = {
                Text(
                    text = stringResource(
                        when (message) {
                            HealthMessage.WEIGHT_IMPORTED -> R.string.health_connect_weight_imported
                            HealthMessage.WEIGHT_ALREADY_IMPORTED -> R.string.health_connect_weight_already_imported
                            HealthMessage.PERMISSIONS_DENIED -> R.string.health_connect_permissions_denied
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

private fun formatDate(weight: HealthWeight): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
        .format(weight.measuredAt)

/** Öffnet Health Connect im Play Store (Installation bzw. Update auf Android 9 bis 13). */
private fun openPlayStore(context: Context) {
    try {
        context.startActivity(HealthConnectPermissions.playStoreIntent())
    } catch (_: ActivityNotFoundException) {
        // Kein Play Store vorhanden: der Hinweistext bleibt die einzige Information.
    }
}

