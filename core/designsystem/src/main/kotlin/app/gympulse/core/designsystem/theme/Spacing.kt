package app.gympulse.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * Abstände nach dem Material-3-Raster (8 dp, mit 4-dp-Feinstufe).
 * Screens verwenden nur diese Tokens für Padding, Margins und Gaps.
 */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** Seitenrand von Inhalten auf kompakten Screens. */
    val screenMargin = m

    /** Maximale Inhaltsbreite auf großen Screens, damit Zeilen lesbar bleiben. */
    val maxContentWidth = 840.dp
}
