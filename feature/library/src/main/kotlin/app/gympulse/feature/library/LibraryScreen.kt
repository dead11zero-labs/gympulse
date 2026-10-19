package app.gympulse.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.gympulse.core.designsystem.component.PlaceholderScreen

@Composable
internal fun LibraryScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.feature_library_title),
        description = stringResource(R.string.feature_library_placeholder),
        modifier = modifier,
    )
}
