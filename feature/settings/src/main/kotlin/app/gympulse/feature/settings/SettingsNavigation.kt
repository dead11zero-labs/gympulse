package app.gympulse.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.gympulse.feature.settings.ai.AiCoachScreen
import app.gympulse.feature.settings.cloud.CloudBackupScreen
import app.gympulse.feature.settings.health.HealthConnectScreen
import app.gympulse.feature.settings.wear.WearSettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

fun NavGraphBuilder.settingsScreen(onOpenAbout: () -> Unit) {
    composable<SettingsRoute> {
        SettingsHost(onOpenAbout = onOpenAbout)
    }
}

private enum class SettingsPage { ROOT, BODY_METRICS, PHOTOS, HEALTH_CONNECT, CLOUD_BACKUP, WEAR, AI_COACH }

/**
 * Einstellungen mit den Unterseiten Körpermaße, Fortschrittsfotos, Health Connect, Cloud-Backup und Smartwatch. Die Unterseiten hängen an der
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
            onOpenHealthConnect = { page = SettingsPage.HEALTH_CONNECT },
            onOpenCloudBackup = { page = SettingsPage.CLOUD_BACKUP },
            onOpenWear = { page = SettingsPage.WEAR },
            onOpenAiCoach = { page = SettingsPage.AI_COACH },
            onOpenAbout = onOpenAbout,
        )
        SettingsPage.BODY_METRICS -> BodyMetricsScreen(onOpenPhotos = { page = SettingsPage.PHOTOS })
        SettingsPage.PHOTOS -> ProgressPhotosScreen()
        SettingsPage.HEALTH_CONNECT -> HealthConnectScreen()
        SettingsPage.CLOUD_BACKUP -> CloudBackupScreen()
        SettingsPage.WEAR -> WearSettingsScreen()
        SettingsPage.AI_COACH -> AiCoachScreen()
    }
}
