package app.gympulse.feature.plans

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object PlansRoute

/** Plan-Editor; ohne [planId] wird ein neuer Plan angelegt. */
@Serializable
data class PlanEditorRoute(val planId: String? = null)

fun NavGraphBuilder.plansScreen(navController: NavController) {
    composable<PlansRoute> {
        PlansScreen(
            onCreatePlan = { navController.navigate(PlanEditorRoute()) },
            onEditPlan = { planId -> navController.navigate(PlanEditorRoute(planId)) },
        )
    }
    composable<PlanEditorRoute> {
        PlanEditorScreen(onClose = { navController.popBackStack() })
    }
}
