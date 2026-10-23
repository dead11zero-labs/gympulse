package app.gympulse.feature.dashboard.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.designsystem.component.GymPulseCard
import app.gympulse.core.designsystem.component.GymPulseCardVariant
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.insights.narration.NarrationSource
import app.gympulse.feature.dashboard.R

/** Coach-Karte im Dashboard; erscheint nur bei aktivem KI-Coach. */
@Composable
internal fun CoachSection(modifier: Modifier = Modifier, viewModel: CoachViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CoachCard(state = state, onRegenerate = viewModel::regenerate, modifier = modifier)
}

@Composable
internal fun CoachCard(state: CoachUiState, onRegenerate: () -> Unit, modifier: Modifier = Modifier) {
    if (state is CoachUiState.Hidden) return
    GymPulseCard(
        modifier = modifier.fillMaxWidth(),
        variant = GymPulseCardVariant.Filled,
        verticalSpacing = Spacing.xs,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                modifier = Modifier.size(Sizing.icon),
            )
            Text(
                text = stringResource(R.string.feature_dashboard_coach_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        when (state) {
            CoachUiState.Hidden -> Unit
            CoachUiState.Loading -> {
                Text(
                    text = stringResource(R.string.feature_dashboard_coach_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is CoachUiState.Content -> {
                Text(text = state.text, style = MaterialTheme.typography.bodyMedium)
                if (state.streaming) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                val ai = state.source == NarrationSource.AI
                Text(
                    text = stringResource(if (ai) R.string.feature_dashboard_coach_ai_disclaimer else R.string.feature_dashboard_coach_template_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ai && !state.streaming) {
                    TextButton(onClick = onRegenerate) { Text(stringResource(R.string.feature_dashboard_coach_regenerate)) }
                }
            }
        }
    }
}
