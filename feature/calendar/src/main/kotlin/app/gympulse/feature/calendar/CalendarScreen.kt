package app.gympulse.feature.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.gympulse.core.designsystem.component.PlaceholderScreen

@Composable
internal fun CalendarScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.feature_calendar_title),
        description = stringResource(R.string.feature_calendar_placeholder),
        modifier = modifier,
    )
}
