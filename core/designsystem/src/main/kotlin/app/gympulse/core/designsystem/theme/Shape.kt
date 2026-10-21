package app.gympulse.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Eckenradien von Kinetic Material (DESIGN.md, `rounded`):
 *
 * | DESIGN.md | Radius | Token / MaterialTheme.shapes        | Einsatz                                  |
 * |-----------|--------|-------------------------------------|------------------------------------------|
 * | sm        | 8 dp   | [small] / `shapes.small`            | Mini-Chips, Scheiben-Buttons             |
 * | DEFAULT   | 16 dp  | [medium] / `shapes.medium`          | Satzzeilen, Eingabefelder, Listengruppen |
 * | md        | 24 dp  | [large] / `shapes.large`            | Übungskarten, Karten, Panels             |
 * | lg        | 32 dp  | [extraLarge] / `shapes.extraLarge`  | Hero-Karten, Bottom Sheets, Dialoge      |
 * | xl        | 48 dp  | [extraExtraLarge]                   | große Hero-Flächen                       |
 * | full      | Pille  | [ExpressiveShapes.full]             | Buttons, Chips, Stepper, FAB, Avatare    |
 */
object ShapeTokens {
    val none: Dp = 0.dp
    val extraSmall: Dp = 4.dp
    val small: Dp = 8.dp
    val medium: Dp = 16.dp
    val large: Dp = 24.dp
    val largeIncreased: Dp = 28.dp
    val extraLarge: Dp = 32.dp
    val extraLargeIncreased: Dp = 40.dp
    val extraExtraLarge: Dp = 48.dp

    /** Die Skala aufsteigend, z. B. für Konsistenztests. */
    val scale: List<Dp> = listOf(
        none, extraSmall, small, medium, large, largeIncreased, extraLarge, extraLargeIncreased, extraExtraLarge,
    )
}

/** Standard-Skala für [androidx.compose.material3.MaterialTheme.shapes]. */
internal val GymPulseShapes = Shapes(
    extraSmall = RoundedCornerShape(ShapeTokens.extraSmall),
    small = RoundedCornerShape(ShapeTokens.small),
    medium = RoundedCornerShape(ShapeTokens.medium),
    large = RoundedCornerShape(ShapeTokens.large),
    extraLarge = RoundedCornerShape(ShapeTokens.extraLarge),
)

/**
 * Formen jenseits von [Shapes].
 * Einsatz: [largeIncreased] (28 dp) für hervorgehobene Karten und Kacheln,
 * [extraLargeIncreased] (40 dp) und [extraExtraLarge] (48 dp, DESIGN.md `xl`) für Hero-Flächen,
 * [full] für Pillen, Buttons, Stepper, Avatare und Icon-Container.
 */
object ExpressiveShapes {
    val largeIncreased: Shape = RoundedCornerShape(ShapeTokens.largeIncreased)
    val extraLargeIncreased: Shape = RoundedCornerShape(ShapeTokens.extraLargeIncreased)
    val extraExtraLarge: Shape = RoundedCornerShape(ShapeTokens.extraExtraLarge)
    val full: Shape = CircleShape
}
