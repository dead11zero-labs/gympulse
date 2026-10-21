package app.gympulse.core.designsystem.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gympulse.core.designsystem.theme.Spacing

/**
 * Fensterbreiten-Klassen nach Material 3 (m3.material.io/foundations/layout).
 *
 * | Klasse      | Breite         | Navigation | Rand  | Gutter | Spalten |
 * |-------------|----------------|------------|-------|--------|---------|
 * | Compact     | < 600 dp       | Bar        | 16 dp | 12 dp  | 4       |
 * | Medium      | 600–839 dp     | Rail       | 32 dp | 20 dp  | 8       |
 * | Expanded    | 840–1199 dp    | Rail       | 32 dp | 20 dp  | 12      |
 * | Large       | 1200–1599 dp   | Rail       | 32 dp | 20 dp  | 12      |
 * | ExtraLarge  | ≥ 1600 dp      | Rail       | 32 dp | 20 dp  | 12      |
 *
 * Rand und Gutter folgen Kinetic Material (DESIGN.md `margin-*`, `gutter-*`).
 */
enum class WindowWidthClass(val minWidth: Dp) {
    Compact(0.dp),
    Medium(600.dp),
    Expanded(840.dp),
    Large(1200.dp),
    ExtraLarge(1600.dp),
    ;

    /** `true` ab 600 dp: Navigation Rail statt Navigation Bar, mehrspaltige Layouts möglich. */
    val isAtLeastMedium: Boolean get() = this >= Medium

    /** `true` ab 840 dp: zwei Panes (Liste/Detail) nebeneinander sinnvoll. */
    val isAtLeastExpanded: Boolean get() = this >= Expanded

    /** Horizontaler Seitenrand für Inhalte. */
    val horizontalMargin: Dp get() = if (this == Compact) Spacing.marginCompact else Spacing.marginMedium

    /** Abstand zwischen Spalten, Karten und Kacheln. */
    val gutter: Dp get() = if (this == Compact) Spacing.gutterCompact else Spacing.gutterMedium

    /** Spalten des Layout-Rasters. */
    val gridColumns: Int
        get() = when (this) {
            Compact -> 4
            Medium -> 8
            else -> 12
        }

    /** Empfohlene Anzahl [app.gympulse.core.designsystem.component.StatTile]s pro Reihe. */
    val statTileColumns: Int
        get() = when (this) {
            Compact -> 2
            Medium -> 3
            else -> 4
        }

    companion object {
        /** Ordnet eine Fensterbreite ihrer Klasse zu. Negative Werte gelten als [Compact]. */
        fun fromWidth(width: Dp): WindowWidthClass =
            entries.last { width >= it.minWidth || it == Compact }
    }
}

/** Breite des aktuellen Fensters in dp. */
@Composable
fun currentWindowWidth(): Dp =
    with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }

/** Fensterbreiten-Klasse des aktuellen Fensters; ändert sich bei Rotation, Split-Screen und Falten. */
@Composable
fun currentWindowWidthClass(): WindowWidthClass = WindowWidthClass.fromWidth(currentWindowWidth())

/**
 * Begrenzt Inhalte auf [maxWidth] und zentriert sie horizontal; auf schmalen Screens füllt der
 * Inhalt die volle Breite.
 */
fun Modifier.maxContentWidth(maxWidth: Dp = Spacing.maxContentWidth): Modifier =
    this
        .fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = maxWidth)

/**
 * Container, der seinen Inhalt auf [maxWidth] begrenzt und zentriert. Für ganze Screen-Inhalte
 * (z. B. um eine `LazyColumn`) auf Tablets und Foldables.
 */
@Composable
fun MaxWidthContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = Spacing.maxContentWidth,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.fillMaxWidth().widthIn(max = maxWidth), content = content)
    }
}
