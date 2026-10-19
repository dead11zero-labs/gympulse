package app.gympulse.feature.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gympulse.core.designsystem.component.PlaceholderScreen

@Composable
internal fun DashboardScreen(
    onStartWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        PlaceholderScreen(
            title = stringResource(R.string.feature_dashboard_title),
            description = stringResource(R.string.feature_dashboard_placeholder),
        )
        ExtendedFloatingActionButton(
            onClick = onStartWorkout,
            icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
            text = { Text(stringResource(R.string.feature_dashboard_start_workout)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
}
