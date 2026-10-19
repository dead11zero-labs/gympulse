package app.gympulse.feature.plans

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object PlansRoute

fun NavGraphBuilder.plansScreen() {
    composable<PlansRoute> {
        PlansScreen()
    }
}
