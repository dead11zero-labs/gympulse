package app.gympulse.wear.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Abstände und Mindestgrößen der Uhr-UI (4-dp-Raster). Wie die Farben Teil des kleinen Wear-Designsystems. */
object WearSpacing {
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp

    /** Mindestgröße eines Touch-Ziels auf der Uhr. */
    val minTouchTarget: Dp = 48.dp

    /** Randabstand für nicht scrollende Inhalte auf runden Displays. */
    val roundScreenHorizontal: Dp = 20.dp
    val roundScreenVertical: Dp = 24.dp
}
