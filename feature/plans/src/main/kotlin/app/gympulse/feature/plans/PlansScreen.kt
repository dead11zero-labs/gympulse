package app.gympulse.feature.plans

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.gympulse.core.designsystem.component.PlaceholderScreen

@Composable
internal fun PlansScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.feature_plans_title),
        description = stringResource(R.string.feature_plans_placeholder),
        modifier = modifier,
    )
}
