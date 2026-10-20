package app.gympulse.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Fallback-Farbschema von GymPulse, wenn Dynamic Color nicht verfügbar oder abgeschaltet ist.
 *
 * Erzeugt mit den Material Color Utilities (Variante "Vibrant", Spec 2021):
 * - Seed (Primary/Secondary/Neutral): Energie-Orange #FF5A1F
 * - Tertiary: eigene Teal-Palette (Hue 196.6, Chroma 48) als kühler Kontrast, z. B. für
 *   positive Trends und Fortschritt.
 * - Error: Standard-Fehlerpalette von Material 3.
 *
 * Alle Paare (x/onX, xContainer/onXContainer, surface-Varianten mit onSurface) erreichen mindestens 4.5:1,
 * siehe ColorSchemeContrastTest.
 */

/** Seed-Farbe des Fallback-Schemas (ARGB). */
const val GymPulseSeedColor: Long = 0xFFFF5A1F

internal val GymPulseLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFFAE3200),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBD0),
    onPrimaryContainer = Color(0xFF852400),
    inversePrimary = Color(0xFFFFB59E),
    secondary = Color(0xFF7E5538),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC6),
    onSecondaryContainer = Color(0xFF633E23),
    tertiary = Color(0xFF006A6A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF6FF7F6),
    onTertiaryContainer = Color(0xFF004F4F),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF271813),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF271813),
    surfaceVariant = Color(0xFFFDDBD2),
    onSurfaceVariant = Color(0xFF58423B),
    surfaceTint = Color(0xFFAE3200),
    inverseSurface = Color(0xFF3D2C27),
    inverseOnSurface = Color(0xFFFFEDE8),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF8C7169),
    outlineVariant = Color(0xFFDFC0B6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F6),
    surfaceContainer = Color(0xFFFFE9E3),
    surfaceContainerHigh = Color(0xFFFFE2DA),
    surfaceContainerHighest = Color(0xFFF9DCD4),
    surfaceContainerLow = Color(0xFFFFF1ED),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFF0D4CC),
)

internal val GymPulseDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB59E),
    onPrimary = Color(0xFF5E1700),
    primaryContainer = Color(0xFF852400),
    onPrimaryContainer = Color(0xFFFFDBD0),
    inversePrimary = Color(0xFFAE3200),
    secondary = Color(0xFFF1BB98),
    onSecondary = Color(0xFF49280F),
    secondaryContainer = Color(0xFF633E23),
    onSecondaryContainer = Color(0xFFFFDCC6),
    tertiary = Color(0xFF4CDADA),
    onTertiary = Color(0xFF003737),
    tertiaryContainer = Color(0xFF004F4F),
    onTertiaryContainer = Color(0xFF6FF7F6),
    background = Color(0xFF1E100C),
    onBackground = Color(0xFFF9DCD4),
    surface = Color(0xFF1E100C),
    onSurface = Color(0xFFF9DCD4),
    surfaceVariant = Color(0xFF58423B),
    onSurfaceVariant = Color(0xFFDFC0B6),
    surfaceTint = Color(0xFFFFB59E),
    inverseSurface = Color(0xFFF9DCD4),
    inverseOnSurface = Color(0xFF3D2C27),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA78A82),
    outlineVariant = Color(0xFF58423B),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF473530),
    surfaceContainer = Color(0xFF2B1C17),
    surfaceContainerHigh = Color(0xFF362621),
    surfaceContainerHighest = Color(0xFF42312C),
    surfaceContainerLow = Color(0xFF271813),
    surfaceContainerLowest = Color(0xFF180B07),
    surfaceDim = Color(0xFF1E100C),
)
