package app.gympulse.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import app.gympulse.core.designsystem.theme.labelCaps

/**
 * Großbuchstaben-Label (label-sm, 0.06 em Laufweite) für Metrik-Beschriftungen wie
 * "GEWICHT (KG)", "WDH.", "PAUSE", "DAUER" oder Abschnittstitel in Einstellungen. Trennt
 * Beschriftungen klar von eingegebenen Werten.
 *
 * Den Text normal geschrieben aus `strings.xml` übergeben ("Gewicht (kg)"); die Umwandlung in
 * Großbuchstaben passiert hier, damit TalkBack- und Übersetzungstexte lesbar bleiben.
 *
 * @param color Standard `onSurfaceVariant`; für Abschnittstitel z. B. `tertiary`.
 * @param style Standard [labelCaps] (label-sm); für größere Labels `labelMedium`.
 */
@Composable
fun LabelCaps(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    style: TextStyle = MaterialTheme.typography.labelCaps,
    maxLines: Int = 1,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
