package app.gympulse.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import app.gympulse.core.designsystem.R
import app.gympulse.core.designsystem.theme.ExpressiveShapes
import app.gympulse.core.designsystem.theme.STATUS_CHIP_BORDER_ALPHA
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.designsystem.theme.prBadgeContainer
import app.gympulse.core.designsystem.theme.statusChipContainer
import app.gympulse.core.designsystem.theme.tabularNumbers

/**
 * Status-Chip für Telemetrie und Integrationen (Kinetic Material, "Status Chips"), z. B.
 * "Health Connect · Synchronisiert · 134 BPM" oder "Live Sync". 32 dp hohe Pille.
 *
 * - Aktiv: Tertiary-Tönung über `surfaceContainer`, 1 dp Rand in `tertiary` (30 %),
 *   8-dp-Punkt bzw. Icon in `tertiary`, Text in `onSurface`, Metrik in `tertiary`.
 * - Inaktiv (z. B. "Kommt bald", "Nicht verbunden"): `surfaceContainerHighest`, Punkt in
 *   `outline`, Text in `onSurfaceVariant`, ohne Rand.
 *
 * Der Chip ist eine reine Anzeige und wird als ein TalkBack-Element vorgelesen. Für eine
 * Aktion (z. B. "Verbinden") einen Button oder eine [ListRow] daneben setzen.
 *
 * @param text Status, z. B. "Synchronisiert".
 * @param active aktiver (getönter) oder inaktiver (neutraler) Zustand.
 * @param icon optionales Sensor-/Anbieter-Icon statt des Punkts (dekorativ).
 * @param metric optionale Kennzahl rechts, z. B. "134 BPM" (Tabellenziffern).
 */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    icon: ImageVector? = null,
    metric: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (active) scheme.statusChipContainer() else scheme.surfaceContainerHighest
    val accent = if (active) scheme.tertiary else scheme.outline
    val textColor = if (active) scheme.onSurface else scheme.onSurfaceVariant
    Surface(
        modifier = modifier
            .heightIn(min = Sizing.statusChipHeight)
            .semantics(mergeDescendants = true) {},
        shape = ExpressiveShapes.full,
        color = container,
        contentColor = textColor,
        border = if (active) BorderStroke(Sizing.hairline, scheme.tertiary.copy(alpha = STATUS_CHIP_BORDER_ALPHA)) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(Sizing.iconSmall))
            } else {
                Box(
                    modifier = Modifier
                        .size(Sizing.statusDot)
                        .background(color = accent, shape = ExpressiveShapes.full),
                )
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            if (metric != null) {
                Text(
                    text = metric,
                    style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                    color = if (active) scheme.tertiary else scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Badge für eine persönliche Bestleistung (Kinetic Material, "PR Badge"): Pille mit
 * Secondary-Tönung (20 %) über der Fläche darunter, Text in `secondary` (label-sm, fett,
 * Großbuchstaben), optional mit Icon davor (z. B. Pokal oder Flamme).
 *
 * @param text Standard "PR" (bzw. übersetzt); z. B. "PR +2,5 kg".
 * @param icon optionales Icon vor dem Text (dekorativ).
 * @param onSurface Fläche, auf der das Badge liegt; bestimmt die deckende Tönung.
 *   Standard `surfaceContainer` (Karten); auf Ebene-1-Karten `surfaceContainerLow`.
 */
@Composable
fun PrBadge(
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.designsystem_pr_badge),
    icon: ImageVector? = null,
    onSurface: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = ExpressiveShapes.full,
        color = scheme.prBadgeContainer(onSurface),
        contentColor = scheme.secondary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(Sizing.iconSmall))
            }
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}
