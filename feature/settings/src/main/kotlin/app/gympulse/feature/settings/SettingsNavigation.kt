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

fun NavGraphBuilder.settingsScreen(onOpenAbout: () -> Unit) {
    composable<SettingsRoute> {
        SettingsHost(onOpenAbout = onOpenAbout)
    }
}

private enum class SettingsPage { ROOT, BODY_METRICS, PHOTOS }

/**
 * Einstellungen mit den Unterseiten Körpermaße und Fortschrittsfotos. Die Unterseiten hängen an der
 * Settings-Route, damit NavHost und App-Scaffold unverändert bleiben; Zurück führt eine Ebene hoch.
 */
@Composable
private fun SettingsHost(onOpenAbout: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.ROOT) }
    BackHandler(enabled = page != SettingsPage.ROOT) {
        page = if (page == SettingsPage.PHOTOS) SettingsPage.BODY_METRICS else SettingsPage.ROOT
    }
    when (page) {
        SettingsPage.ROOT -> SettingsScreen(
            onOpenBodyMetrics = { page = SettingsPage.BODY_METRICS },
            onOpenAbout = onOpenAbout,
        )
        SettingsPage.BODY_METRICS -> BodyMetricsScreen(onOpenPhotos = { page = SettingsPage.PHOTOS })
        SettingsPage.PHOTOS -> ProgressPhotosScreen()
    }
}
