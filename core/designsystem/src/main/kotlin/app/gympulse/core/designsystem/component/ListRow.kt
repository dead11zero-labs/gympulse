package app.gympulse.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.gympulse.core.designsystem.theme.ExpressiveShapes
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing

/**
 * Listenzeile auf Basis von Material-3-[ListItem] mit den häufigsten GymPulse-Slots.
 *
 * @param headline Haupttext (Body Large).
 * @param supportingText optionale zweite/dritte Zeile (Body Medium, `onSurfaceVariant`).
 * @param overlineText optionale Zeile über dem Haupttext (Label Small).
 * @param leadingIcon optionales Icon in einem tonalen Kreis, siehe [ListRowIcon].
 * @param leadingContent freier Slot links; hat Vorrang vor [leadingIcon].
 * @param trailingText optionaler kurzer Wert rechts, z. B. "80 kg" (Label Large).
 * @param trailingContent freier Slot rechts (Switch, Checkbox, Icon); hat Vorrang vor [trailingText].
 * @param onClick macht die Zeile klickbar.
 * @param containerColor Hintergrund; Standard transparent, damit die Zeile die Fläche des
 *   umgebenden Containers (z. B. [ListGroup]) übernimmt.
 */
@Composable
fun ListRow(
    headline: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    overlineText: String? = null,
    leadingIcon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingText: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    containerColor: Color = Color.Transparent,
) {
    val leading: (@Composable () -> Unit)? = if (leadingContent != null) {
        leadingContent
    } else if (leadingIcon != null) {
        { ListRowIcon(leadingIcon) }
    } else {
        null
    }
    val trailing: (@Composable () -> Unit)? = if (trailingContent != null) {
        trailingContent
    } else if (trailingText != null) {
        {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        null
    }
    val overline: (@Composable () -> Unit)? = if (overlineText != null) {
        { Text(overlineText) }
    } else {
        null
    }
    val supporting: (@Composable () -> Unit)? = if (supportingText != null) {
        { Text(supportingText, maxLines = 3, overflow = TextOverflow.Ellipsis) }
    } else {
        null
    }
    ListItem(
        headlineContent = { Text(headline, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        overlineContent = overline,
        supportingContent = supporting,
        leadingContent = leading,
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = containerColor),
    )
}

/**
 * Icon in einem tonalen Kreis (`surfaceContainerHighest`/`onSurface`) für den
 * führenden Slot von [ListRow]. Dekorativ, die Zeile trägt die Aussage.
 */
@Composable
fun ListRowIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = Sizing.iconContainer,
) {
    Surface(
        modifier = modifier.size(size),
        shape = ExpressiveShapes.full,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Sizing.icon))
        }
    }
}

/**
 * Gruppiert [ListRow]s auf einer gemeinsamen tonalen Fläche (`surfaceContainer`) mit
 * 16-dp-Ecken und 1 dp `outlineVariant`-Rand, wie die Einstellungs- und Übungslisten in
 * Kinetic Material. Trenner zwischen den Zeilen mit [ListRowDivider] setzen.
 *
 * @param bordered 1-dp-Rand in `outlineVariant` (Standard an).
 */
@Composable
fun ListGroup(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    bordered: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (bordered) BorderStroke(Sizing.hairline, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Column(content = content)
    }
}

/**
 * Trenner zwischen Listenzeilen in `outlineVariant`.
 *
 * @param inset Einzug links, Standard so, dass der Trenner am Text nach einem [ListRowIcon] beginnt.
 */
@Composable
fun ListRowDivider(
    modifier: Modifier = Modifier,
    inset: Dp = Spacing.m + Sizing.iconContainer + Spacing.m,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = inset),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
