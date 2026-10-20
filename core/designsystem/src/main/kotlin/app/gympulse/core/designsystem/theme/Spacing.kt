package app.gympulse.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Abstände nach dem Material-3-Raster (8 dp, mit 4-dp-Feinstufe).
 * Screens verwenden nur diese Tokens für Padding, Margins und Gaps.
 */
object Spacing {
    val none: Dp = 0.dp
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val s: Dp = 12.dp
    val m: Dp = 16.dp
    val l: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
    val xxxl: Dp = 64.dp

    /** Seitenrand von Inhalten auf kompakten Screens. */
    val screenMargin: Dp = m

    /** Seitenrand ab mittlerer Fensterbreite (≥ 600 dp). */
    val screenMarginWide: Dp = l

    /** Abstand zwischen Karten und Kacheln in Rastern und Listen. */
    val gutter: Dp = s

    /** Maximale Inhaltsbreite auf großen Screens, damit Zeilen lesbar bleiben. */
    val maxContentWidth: Dp = 840.dp

    /** Maximale Breite für Formulare und Einzelspalten-Dialoge. */
    val maxFormWidth: Dp = 560.dp

    /** Die Abstandsstufen aufsteigend, z. B. für Konsistenztests. */
    val scale: List<Dp> = listOf(none, xxs, xs, s, m, l, xl, xxl, xxxl)
}

/**
 * Höhenstufen von Material 3. Tiefe entsteht in GymPulse primär über tonale Flächen
 * (`surfaceContainer*`); diese Werte gelten als `tonalElevation` oder, wo nötig, als Schatten.
 */
object Elevation {
    val level0: Dp = 0.dp
    val level1: Dp = 1.dp
    val level2: Dp = 3.dp
    val level3: Dp = 6.dp
    val level4: Dp = 8.dp
    val level5: Dp = 12.dp

    val scale: List<Dp> = listOf(level0, level1, level2, level3, level4, level5)
}

/** Feste Größen von Icons, Touch-Zielen und Indikatoren. */
object Sizing {
    /** Mindestgröße eines Touch-Ziels. */
    val minTouchTarget: Dp = 48.dp
    val iconSmall: Dp = 18.dp
    val icon: Dp = 24.dp
    val iconLarge: Dp = 32.dp

    /** Tonaler Kreis hinter einem führenden Listen-Icon. */
    val iconContainer: Dp = 40.dp

    /** Tonaler Kreis hinter dem Icon eines Leerzustands. */
    val emptyStateIconContainer: Dp = 96.dp
    val emptyStateIcon: Dp = 48.dp

    /** Runde Illustration auf den Seiten des Onboardings. */
    val onboardingHero: Dp = 160.dp

    /** Fortschrittsring: Standardgröße und Strichstärke. */
    val progressRing: Dp = 120.dp
    val progressRingStroke: Dp = 10.dp

    /** Höhe des dicken Fortschrittsbalkens. */
    val progressBar: Dp = 8.dp

    /** Maximale Breite eines Bottom Sheets auf großen Screens. */
    val sheetMaxWidth: Dp = 640.dp
}
