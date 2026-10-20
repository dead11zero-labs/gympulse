package app.gympulse.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.gympulse.core.designsystem.R
import app.gympulse.core.designsystem.theme.ExpressiveShapes
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.designsystem.theme.metricLarge
import app.gympulse.core.designsystem.theme.metricMedium
import app.gympulse.core.designsystem.theme.metricUnit

/** Richtung einer Veränderung gegenüber dem Vergleichszeitraum. */
enum class TrendDirection { Up, Down, Flat }

/**
 * Trend unter einer Kennzahl, z. B. "+5 % ggü. Vorwoche".
 *
 * @param label fertig formatierter Text (Wert und Bezug), wird angezeigt und vorgelesen.
 * @param direction Richtung der Veränderung; bestimmt das Pfeil-Icon.
 * @param isPositive ob die Veränderung gut ist. Standard: steigend = gut. Für Werte wie
 *   Körperfett oder Pace auf `direction == Down` setzen. Gut wird in `tertiary`, schlecht in
 *   `error`, [TrendDirection.Flat] in `onSurfaceVariant` dargestellt.
 */
data class StatTrend(
    val label: String,
    val direction: TrendDirection,
    val isPositive: Boolean = direction == TrendDirection.Up,
)

/** Optik einer [StatTile]. */
enum class StatTileStyle {
    /** Neutrale tonale Fläche (`surfaceContainerHigh`/`onSurface`), Standard für Raster. */
    Standard,

    /** Hervorgehoben (`primaryContainer`/`onPrimaryContainer`), für die eine Hauptkennzahl. */
    Highlighted,
}

/**
 * Kachel für eine Kennzahl: große Zahl (optional mit Einheit), Beschriftung und optionaler Trend.
 * Für Raster aus mehreren Kacheln [app.gympulse.core.designsystem.adaptive.WindowWidthClass.statTileColumns]
 * nutzen. Die Kachel wird für TalkBack zu einem Element zusammengefasst
 * ("12 450 kg, Volumen, Trend steigend: +5 %").
 *
 * @param value fertig formatierte Zahl, z. B. "12 450" oder "1:05:12".
 * @param label kurze Beschriftung unter der Zahl, z. B. "Volumen diese Woche".
 * @param unit optionale Einheit neben der Zahl, z. B. "kg".
 * @param icon optionales Icon über der Zahl (dekorativ, wird nicht vorgelesen).
 * @param trend optionaler Trend unter der Beschriftung.
 * @param style [StatTileStyle.Standard] oder [StatTileStyle.Highlighted].
 * @param compact kleinere Zahl (Headline statt Display) für dichte Raster.
 * @param onClick macht die Kachel klickbar (z. B. Sprung in die Statistik).
 */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    icon: ImageVector? = null,
    trend: StatTrend? = null,
    style: StatTileStyle = StatTileStyle.Standard,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = when (style) {
        StatTileStyle.Standard -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        StatTileStyle.Highlighted -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    // Kachel als ein TalkBack-Element: Zahl, Einheit, Beschriftung, Trend.
    val semanticsModifier = modifier.semantics(mergeDescendants = true) {}

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(Sizing.icon))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = value,
                    style = if (compact) MaterialTheme.typography.metricMedium else MaterialTheme.typography.metricLarge,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
                if (unit != null) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.metricUnit,
                        maxLines = 1,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (trend != null) {
                TrendLabel(trend = trend, onHighlighted = style == StatTileStyle.Highlighted)
            }
        }
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = semanticsModifier,
            shape = ExpressiveShapes.largeIncreased,
            colors = colors,
        ) { content() }
    } else {
        Card(
            modifier = semanticsModifier,
            shape = ExpressiveShapes.largeIncreased,
            colors = colors,
        ) { content() }
    }
}

/** Trend-Zeile mit Pfeil und Text; eigenständig nutzbar, z. B. in Listenzeilen. */
@Composable
fun TrendLabel(
    trend: StatTrend,
    modifier: Modifier = Modifier,
    onHighlighted: Boolean = false,
) {
    val color = trendColor(trend, onHighlighted)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        val arrow = when (trend.direction) {
            TrendDirection.Up -> Icons.Filled.KeyboardArrowUp
            TrendDirection.Down -> Icons.Filled.KeyboardArrowDown
            TrendDirection.Flat -> null
        }
        if (arrow != null) {
            Icon(arrow, contentDescription = null, tint = color, modifier = Modifier.size(Sizing.iconSmall))
        }
        val description = trendContentDescription(trend)
        Text(
            text = trend.label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

@Composable
private fun trendColor(trend: StatTrend, onHighlighted: Boolean): Color = when {
    // Auf primaryContainer bleibt nur onPrimaryContainer sicher kontrastreich.
    onHighlighted -> MaterialTheme.colorScheme.onPrimaryContainer
    trend.direction == TrendDirection.Flat -> MaterialTheme.colorScheme.onSurfaceVariant
    trend.isPositive -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun trendContentDescription(trend: StatTrend): String {
    val res = when (trend.direction) {
        TrendDirection.Up -> R.string.designsystem_trend_up
        TrendDirection.Down -> R.string.designsystem_trend_down
        TrendDirection.Flat -> R.string.designsystem_trend_flat
    }
    return stringResource(res, trend.label)
}
