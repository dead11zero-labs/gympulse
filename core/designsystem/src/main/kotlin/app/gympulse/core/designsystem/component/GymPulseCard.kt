package app.gympulse.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing

/**
 * Varianten von [GymPulseCard]. Jede nutzt ein festes Tonpaar aus dem ColorScheme, damit der
 * Kontrast auch mit Dynamic Color stimmt.
 */
enum class GymPulseCardVariant {
    /** Gefüllt: `surfaceContainer`/`onSurface` ohne Rand. Standard für gruppierte Inhalte (Dashboard-Karten). */
    Filled,

    /**
     * Ebene 1 (Kinetic Material): `surfaceContainerLow` mit 1 dp `outlineVariant`-Rand.
     * Für inaktive, erledigte oder kommende Übungsblöcke und ruhige Listen.
     */
    Level1,

    /**
     * Ebene 2 (Kinetic Material): `surfaceContainer` mit 1 dp `outlineVariant`-Rand.
     * Für die aktive Übungskarte und aktive Eingabegruppen.
     */
    Level2,

    /** Umrandet: `surface` mit `outlineVariant`-Rand. Für ruhige, gleichrangige Elemente. */
    Outlined,

    /** Erhöht: `surfaceContainerHigh` mit Level-1-Elevation, z. B. über Bildern oder Verläufen. */
    Elevated,

    /** Akzent primär: `primaryContainer`/`onPrimaryContainer`, für die wichtigste Karte eines Screens. */
    Primary,

    /** Akzent sekundär: `secondaryContainer`/`onSecondaryContainer`, für Hinweise und Tipps. */
    Secondary,

    /** Akzent tertiär: `tertiaryContainer`/`onTertiaryContainer`, für Erfolge und Fortschritt. */
    Tertiary,
}

/**
 * Material-3-Karte mit GymPulse-Varianten und einheitlichem Innenabstand.
 *
 * @param variant Optik, siehe [GymPulseCardVariant].
 * @param onClick macht die ganze Karte klickbar (Ripple, Touch-Ziel, TalkBack-Aktion).
 * @param enabled nur mit [onClick] relevant.
 * @param shape Standard `MaterialTheme.shapes.large` (24 dp, DESIGN.md `md`); für Hero-Karten
 *   `MaterialTheme.shapes.extraLarge` (32 dp).
 * @param contentPadding Innenabstand des Inhalts, Standard 16 dp.
 * @param verticalSpacing Abstand zwischen den Kindern der Inhaltsspalte.
 * @param content Inhalt in einer [Column].
 */
@Composable
fun GymPulseCard(
    modifier: Modifier = Modifier,
    variant: GymPulseCardVariant = GymPulseCardVariant.Filled,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(Spacing.m),
    verticalSpacing: Dp = Spacing.xs,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = cardColorsFor(variant)
    val elevation = cardElevationFor(variant)
    val border: BorderStroke? = when (variant) {
        GymPulseCardVariant.Outlined -> CardDefaults.outlinedCardBorder(enabled)
        GymPulseCardVariant.Level1, GymPulseCardVariant.Level2 ->
            BorderStroke(Sizing.hairline, MaterialTheme.colorScheme.outlineVariant)
        else -> null
    }
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
    }
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            content = inner,
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            content = inner,
        )
    }
}

@Composable
private fun cardColorsFor(variant: GymPulseCardVariant): CardColors {
    val scheme = MaterialTheme.colorScheme
    return when (variant) {
        GymPulseCardVariant.Filled -> CardDefaults.cardColors(
            containerColor = scheme.surfaceContainer,
            contentColor = scheme.onSurface,
        )
        GymPulseCardVariant.Level1 -> CardDefaults.cardColors(
            containerColor = scheme.surfaceContainerLow,
            contentColor = scheme.onSurface,
        )
        GymPulseCardVariant.Level2 -> CardDefaults.cardColors(
            containerColor = scheme.surfaceContainer,
            contentColor = scheme.onSurface,
        )
        GymPulseCardVariant.Outlined -> CardDefaults.outlinedCardColors(
            containerColor = scheme.surface,
            contentColor = scheme.onSurface,
        )
        GymPulseCardVariant.Elevated -> CardDefaults.elevatedCardColors(
            containerColor = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
        )
        GymPulseCardVariant.Primary -> CardDefaults.cardColors(
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        )
        GymPulseCardVariant.Secondary -> CardDefaults.cardColors(
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
        )
        GymPulseCardVariant.Tertiary -> CardDefaults.cardColors(
            containerColor = scheme.tertiaryContainer,
            contentColor = scheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun cardElevationFor(variant: GymPulseCardVariant): CardElevation = when (variant) {
    GymPulseCardVariant.Elevated -> CardDefaults.elevatedCardElevation()
    GymPulseCardVariant.Outlined -> CardDefaults.outlinedCardElevation()
    else -> CardDefaults.cardElevation()
}
