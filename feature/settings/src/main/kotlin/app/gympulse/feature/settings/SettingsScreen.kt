package app.gympulse.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.gympulse.core.designsystem.component.PlaceholderScreen

@Composable
internal fun SettingsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.feature_settings_title),
        description = stringResource(R.string.feature_settings_placeholder),
        modifier = modifier,
    )
}
