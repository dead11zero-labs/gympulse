package app.gympulse.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing

/**
 * Überschrift eines Abschnitts (Title Medium, `onSurface`) mit optionalem Untertitel und
 * Text-Aktion rechts ("Alle anzeigen"). Für TalkBack als Überschrift markiert.
 *
 * @param title Abschnittstitel.
 * @param supportingText optionaler Untertitel in `onSurfaceVariant`.
 * @param actionLabel Beschriftung der Aktion; nur zusammen mit [onActionClick] sichtbar.
 * @param onActionClick Klick auf die Aktion.
 * @param contentPadding Standard: Seitenrand horizontal, kleiner Abstand vertikal.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.xs),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizing.minTouchTarget)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actionLabel != null && onActionClick != null) {
            TextButton(onClick = onActionClick) { Text(actionLabel) }
        }
    }
}
