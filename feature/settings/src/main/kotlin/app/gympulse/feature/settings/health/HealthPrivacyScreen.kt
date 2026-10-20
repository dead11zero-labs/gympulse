package app.gympulse.feature.settings.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.feature.settings.R

/**
 * Datenschutzhinweis zu Health Connect. Health Connect öffnet ihn über die Rationale-Activity der
 * App, wenn der Nutzer im Berechtigungsdialog auf den Datenschutzlink tippt.
 */
@Composable
fun HealthPrivacyScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = Spacing.maxFormWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenMargin, vertical = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(
                    text = stringResource(R.string.health_privacy_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                listOf(
                    R.string.health_privacy_write,
                    R.string.health_privacy_read,
                    R.string.health_privacy_storage,
                    R.string.health_privacy_revoke,
                ).forEach { paragraph ->
                    Text(
                        text = stringResource(paragraph),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.health_privacy_close))
                }
            }
        }
    }
}
