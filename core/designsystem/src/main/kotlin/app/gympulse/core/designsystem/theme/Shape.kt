package app.gympulse.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Eckenradien der Material-3-Shape-Skala inklusive der Expressive-Zwischenstufen. */
object ShapeTokens {
    val none: Dp = 0.dp
    val extraSmall: Dp = 4.dp
    val small: Dp = 8.dp
    val medium: Dp = 12.dp
    val large: Dp = 16.dp
    val largeIncreased: Dp = 20.dp
    val extraLarge: Dp = 28.dp
    val extraLargeIncreased: Dp = 32.dp
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
 * Expressive-Formen, die [Shapes] (noch) nicht stabil anbietet.
 * Einsatz: [largeIncreased] für hervorgehobene Karten und Kacheln, [extraLargeIncreased] für
 * Hero-Flächen, [full] für Pillen, Avatare und Icon-Container.
 */
object ExpressiveShapes {
    val largeIncreased: Shape = RoundedCornerShape(ShapeTokens.largeIncreased)
    val extraLargeIncreased: Shape = RoundedCornerShape(ShapeTokens.extraLargeIncreased)
    val extraExtraLarge: Shape = RoundedCornerShape(ShapeTokens.extraExtraLarge)
    val full: Shape = CircleShape
}
