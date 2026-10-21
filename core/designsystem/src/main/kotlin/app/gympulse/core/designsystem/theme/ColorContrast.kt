package app.gympulse.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG-2.x-Kontrastberechnung für Farbpaare des Schemas. */
object ColorContrast {
    /** Mindestkontrast für normalen Text (WCAG AA). */
    const val MIN_TEXT = 4.5

    /** Mindestkontrast für große Schrift und Bedienelement-Grenzen (WCAG AA). */
    const val MIN_NON_TEXT = 3.0

    /** Relative Leuchtdichte einer sRGB-Farbe (0 = schwarz, 1 = weiß). */
    fun relativeLuminance(color: Color): Double {
        val argb = color.toArgb()
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** Kontrastverhältnis zwischen 1.0 und 21.0, unabhängig von der Reihenfolge. */
    fun ratio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}

/** Ein Vorder-/Hintergrund-Paar eines [ColorScheme], das ein Mindestkontrast erfüllen muss. */
data class ContrastPair(
    val name: String,
    val foreground: Color,
    val background: Color,
    val minimum: Double,
) {
    val ratio: Double get() = ColorContrast.ratio(foreground, background)
    val passes: Boolean get() = ratio >= minimum
}

/**
 * Alle Tonpaare, die laut Material 3 kombiniert werden dürfen, mit ihrem Mindestkontrast.
 * Text-Paare brauchen 4.5:1, `outline` als Grenze von Bedienelementen 3:1. Dazu kommen die
 * getönten Flächen von PR-Badge und Status-Chip.
 */
fun ColorScheme.contrastPairs(): List<ContrastPair> {
    val text = ColorContrast.MIN_TEXT
    val surfaces = listOf(
        "surface" to surface,
        "surfaceDim" to surfaceDim,
        "surfaceBright" to surfaceBright,
        "surfaceContainerLowest" to surfaceContainerLowest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
    )
    return buildList {
        add(ContrastPair("onPrimary/primary", onPrimary, primary, text))
        add(ContrastPair("onPrimaryContainer/primaryContainer", onPrimaryContainer, primaryContainer, text))
        add(ContrastPair("onSecondary/secondary", onSecondary, secondary, text))
        add(ContrastPair("onSecondaryContainer/secondaryContainer", onSecondaryContainer, secondaryContainer, text))
        add(ContrastPair("onTertiary/tertiary", onTertiary, tertiary, text))
        add(ContrastPair("onTertiaryContainer/tertiaryContainer", onTertiaryContainer, tertiaryContainer, text))
        add(ContrastPair("onError/error", onError, error, text))
        add(ContrastPair("onErrorContainer/errorContainer", onErrorContainer, errorContainer, text))
        add(ContrastPair("onBackground/background", onBackground, background, text))
        add(ContrastPair("inverseOnSurface/inverseSurface", inverseOnSurface, inverseSurface, text))
        add(ContrastPair("inversePrimary/inverseSurface", inversePrimary, inverseSurface, text))
        add(ContrastPair("onSurfaceVariant/surfaceVariant", onSurfaceVariant, surfaceVariant, text))
        surfaces.forEach { (name, color) ->
            add(ContrastPair("onSurface/$name", onSurface, color, text))
            add(ContrastPair("onSurfaceVariant/$name", onSurfaceVariant, color, text))
            // Akzentfarben als Text/Icon direkt auf Flächen (z. B. Trends, Links).
            add(ContrastPair("primary/$name", primary, color, text))
            add(ContrastPair("secondary/$name", secondary, color, text))
            add(ContrastPair("tertiary/$name", tertiary, color, text))
            add(ContrastPair("error/$name", error, color, text))
            add(ContrastPair("outline/$name", outline, color, ColorContrast.MIN_NON_TEXT))
        }
        // PR-Badge: Secondary-Text auf 20 % Secondary-Tönung über den Karten- und Seitenflächen.
        listOf(
            "surface" to surface,
            "surfaceContainerLow" to surfaceContainerLow,
            "surfaceContainer" to surfaceContainer,
        ).forEach { (name, color) ->
            add(ContrastPair("secondary/prBadge($name)", secondary, prBadgeContainer(color), text))
        }
        // Status-Chip: Text und Metrik auf Tertiary-Tönung über surfaceContainer.
        add(ContrastPair("onSurface/statusChip", onSurface, statusChipContainer(), text))
        add(ContrastPair("tertiary/statusChip", tertiary, statusChipContainer(), text))
    }
}

/** Hintergrund eines PR-Badges: Secondary mit [PR_BADGE_TINT_ALPHA] über [base], deckend. */
fun ColorScheme.prBadgeContainer(base: Color = surfaceContainer): Color =
    secondary.copy(alpha = PR_BADGE_TINT_ALPHA).compositeOver(base)

/** Hintergrund eines aktiven Status-Chips: Tertiary mit [STATUS_CHIP_TINT_ALPHA] über [base], deckend. */
fun ColorScheme.statusChipContainer(base: Color = surfaceContainer): Color =
    tertiary.copy(alpha = STATUS_CHIP_TINT_ALPHA).compositeOver(base)
