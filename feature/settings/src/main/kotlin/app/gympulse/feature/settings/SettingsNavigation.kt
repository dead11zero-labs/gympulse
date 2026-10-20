package app.gympulse.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

fun NavGraphBuilder.settingsScreen(onOpenAbout: () -> Unit) {
    composable<SettingsRoute> {
        SettingsScreen(onOpenAbout = onOpenAbout)
    }
}
