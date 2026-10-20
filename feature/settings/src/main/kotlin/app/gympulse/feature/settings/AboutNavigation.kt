package app.gympulse.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object AboutRoute

fun NavGraphBuilder.aboutScreen() {
    composable<AboutRoute> {
        AboutScreen()
    }
}
