package app.gympulse.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.gympulse.wear.R
import app.gympulse.wear.theme.WearSpacing
import app.gympulse.wear.workout.HeartRateMonitor
import app.gympulse.wear.workout.HeartRateReading
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Reicht den Live-Puls an die UI durch; er bleibt auf der Uhr. */
@HiltViewModel
class HeartRateViewModel @Inject constructor(
    monitor: HeartRateMonitor,
) : ViewModel() {
    val reading: StateFlow<HeartRateReading> = monitor.reading
}

/**
 * Eine Zeile mit dem Live-Puls. Ohne laufende Messung ([HeartRateReading.Off]) entfällt sie; bei
 * fehlender Berechtigung oder Sensor-Problem steht dort ein kurzer Hinweis statt eines Werts.
 */
@Composable
fun HeartRateBadge(
    reading: HeartRateReading,
    modifier: Modifier = Modifier,
) {
    if (reading == HeartRateReading.Off) return
    val description = when (reading) {
        is HeartRateReading.Bpm -> stringResource(R.string.cd_heart_rate_value, reading.value)
        HeartRateReading.Searching -> stringResource(R.string.cd_heart_rate_searching)
        HeartRateReading.NoPermission -> stringResource(R.string.heart_rate_no_permission)
        else -> stringResource(R.string.heart_rate_unavailable)
    }
    Row(
        modifier = modifier
            .padding(bottom = WearSpacing.xs)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WearSpacing.xs, Alignment.CenterHorizontally),
    ) {
        when (reading) {
            is HeartRateReading.Bpm, HeartRateReading.Searching -> {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(WearSpacing.iconSmall),
                )
                Text(
                    text = (reading as? HeartRateReading.Bpm)?.value?.toString() ?: stringResource(R.string.heart_rate_placeholder),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.heart_rate_unit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            else -> Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
