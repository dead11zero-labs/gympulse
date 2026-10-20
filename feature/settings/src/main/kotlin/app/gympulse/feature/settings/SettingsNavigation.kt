package app.gympulse.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

fun NavGraphBuilder.settingsScreen() {
    composable<SettingsRoute> {
        SettingsHost()
    }
}

/**
 * Einstellungen mit der Unterseite Körpermaße. Die Unterseite hängt an der Settings-Route,
 * damit NavHost und App-Scaffold unverändert bleiben; Zurück führt von ihr in die Einstellungen.
 */
@Composable
private fun SettingsHost() {
    var showBodyMetrics by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showBodyMetrics) { showBodyMetrics = false }
    if (showBodyMetrics) {
        BodyMetricsScreen()
    } else {
        SettingsScreen(onOpenBodyMetrics = { showBodyMetrics = true })
    }
}
