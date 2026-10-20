package app.gympulse.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import app.gympulse.core.designsystem.theme.ExpressiveShapes
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing

/**
 * Leerzustand für Listen und Screens ohne Daten: großes Icon in einem tonalen Kreis
 * (`secondaryContainer`), Titel (Headline Small), erklärender Text und optional eine
 * Hauptaktion (gefüllter Button) sowie eine Nebenaktion (Text-Button).
 *
 * Füllt nur die Breite; zum vertikalen Zentrieren den Aufrufer `Modifier.fillMaxSize()` und
 * ein zentrierendes Layout geben lassen. Inhalt ist auf [Spacing.maxFormWidth] begrenzt.
 *
 * @param icon dekoratives Icon (wird nicht vorgelesen, der Titel trägt die Aussage).
 * @param title kurzer Titel, z. B. "Noch keine Trainingspläne".
 * @param message ein bis zwei Sätze, was der Nutzer tun kann.
 * @param actionLabel Beschriftung der Hauptaktion; nur mit [onAction] sichtbar.
 * @param onAction Hauptaktion, z. B. "Plan erstellen".
 * @param secondaryActionLabel Beschriftung der Nebenaktion; nur mit [onSecondaryAction] sichtbar.
 * @param onSecondaryAction Nebenaktion, z. B. "Vorlage importieren".
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = Spacing.maxFormWidth)
                .padding(horizontal = Spacing.l, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Surface(
                shape = ExpressiveShapes.full,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(Sizing.emptyStateIconContainer),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(Sizing.emptyStateIcon))
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(Spacing.xs))
                Button(onClick = onAction) { Text(actionLabel) }
            }
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                TextButton(onClick = onSecondaryAction) { Text(secondaryActionLabel) }
            }
        }
    }
}
