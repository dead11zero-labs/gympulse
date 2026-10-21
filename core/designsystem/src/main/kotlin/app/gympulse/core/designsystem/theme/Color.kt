package app.gympulse.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Kinetic Material: das eigene Farbschema von GymPulse (Design-Paket von Jörg,
 * design/kinetic-material/kinetic_material/DESIGN.md). Es gilt, wenn Dynamic Color aus ist
 * (Standard) oder das Gerät es nicht kann.
 *
 * Seeds:
 * - Primary "Neon Kinetic Mint" #0DF5A4: Hauptaktionen, aktive Timer, abgehakte Sätze, FAB.
 * - Secondary "Caloric Ember" #FF7A00: hohe Belastung, Countdown, PR-Highlights.
 * - Tertiary "Metric Cyan" #00D2FF: technische Daten, Health-Connect-/Samsung-Health-Status, Pausen.
 *
 * Dunkles Schema (AMOLED-first): alle Werte stammen aus dem YAML-Frontmatter von DESIGN.md.
 * Eine bewusste Umordnung: Das Frontmatter führt die leuchtenden Akzente als `*-container`
 * (primary-container #0DF5A4, secondary-container #FF7F1C) und blasse Töne als `primary`/`secondary`.
 * Die Screens des Pakets zeigen Buttons, FAB, Navigationspille und Häkchen aber in den leuchtenden
 * Tönen. Damit Material3-Komponenten (Button, FAB, NavigationBar, Switch, Checkbox), die `primary`
 * nutzen, genau so aussehen, liegen die leuchtenden Töne hier auf `primary`/`secondary`/`tertiary`
 * (Tertiary: tertiary-fixed-dim #47D6FF als nächster Frontmatter-Ton zu #00D2FF) und die dunklen
 * Fixed-Varianten des Frontmatters auf den `*Container`-Rollen (Tonal-Buttons, hervorgehobene Karten).
 *
 * Helles Schema: aus denselben Seeds abgeleitet (Ton ~40 der Akzente auf hellen, leicht kühlen
 * Neutraltönen), damit "Hell" in den Einstellungen stimmig bleibt.
 *
 * Alle Paare (x/onX, xContainer/onXContainer, Akzente auf allen Flächen, PR-Badge- und
 * Status-Chip-Tönung) erreichen mindestens 4.5:1, siehe ColorSchemeContrastTest.
 */

/** Seed-Farbe des Kinetic-Schemas (ARGB), Neon Kinetic Mint. */
const val GymPulseSeedColor: Long = 0xFF0DF5A4

/** Secondary-Seed (ARGB), Caloric Ember. */
const val GymPulseSecondarySeedColor: Long = 0xFFFF7A00

/** Tertiary-Seed (ARGB), Metric Cyan. */
const val GymPulseTertiarySeedColor: Long = 0xFF00D2FF

/** Deckkraft der Secondary-Tönung hinter einem PR-Badge (DESIGN.md: 20 Prozent). */
const val PR_BADGE_TINT_ALPHA: Float = 0.20f

/** Deckkraft der Tertiary-Tönung eines Status-Chips über seiner Fläche. */
const val STATUS_CHIP_TINT_ALPHA: Float = 0.12f

/** Deckkraft des Tertiary-Rands eines Status-Chips (DESIGN.md: 30 Prozent). */
const val STATUS_CHIP_BORDER_ALPHA: Float = 0.30f

internal val GymPulseDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF0DF5A4),
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF005234),
    onPrimaryContainer = Color(0xFF4DFFB2),
    inversePrimary = Color(0xFF006C46),
    secondary = Color(0xFFFF7F1C),
    onSecondary = Color(0xFF522300),
    secondaryContainer = Color(0xFF753400),
    onSecondaryContainer = Color(0xFFFFDBC8),
    tertiary = Color(0xFF47D6FF),
    onTertiary = Color(0xFF003543),
    tertiaryContainer = Color(0xFF004E60),
    onTertiaryContainer = Color(0xFFB6EBFF),
    background = Color(0xFF0E141C),
    onBackground = Color(0xFFDDE3EE),
    surface = Color(0xFF0E141C),
    onSurface = Color(0xFFDDE3EE),
    surfaceVariant = Color(0xFF2F353E),
    onSurfaceVariant = Color(0xFFB9CBBE),
    surfaceTint = Color(0xFF00E297),
    inverseSurface = Color(0xFFDDE3EE),
    inverseOnSurface = Color(0xFF2B3139),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF849589),
    outlineVariant = Color(0xFF3B4A40),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF343A42),
    surfaceContainer = Color(0xFF1A2028),
    surfaceContainerHigh = Color(0xFF242A33),
    surfaceContainerHighest = Color(0xFF2F353E),
    surfaceContainerLow = Color(0xFF161C24),
    surfaceContainerLowest = Color(0xFF090F16),
    surfaceDim = Color(0xFF0E141C),
)

internal val GymPulseLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF006C46),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4DFFB2),
    onPrimaryContainer = Color(0xFF002112),
    inversePrimary = Color(0xFF0DF5A4),
    secondary = Color(0xFF843D00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBC8),
    onSecondaryContainer = Color(0xFF321200),
    tertiary = Color(0xFF00677F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB6EBFF),
    onTertiaryContainer = Color(0xFF001F28),
    background = Color(0xFFF6FAFE),
    onBackground = Color(0xFF161C24),
    surface = Color(0xFFF6FAFE),
    onSurface = Color(0xFF161C24),
    surfaceVariant = Color(0xFFDBE5DD),
    onSurfaceVariant = Color(0xFF3F4942),
    surfaceTint = Color(0xFF006C46),
    inverseSurface = Color(0xFF2B3139),
    inverseOnSurface = Color(0xFFECF1F7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF6F7A72),
    outlineVariant = Color(0xFFBFC9C1),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6FAFE),
    surfaceContainer = Color(0xFFE9EEF3),
    surfaceContainerHigh = Color(0xFFE3E8ED),
    surfaceContainerHighest = Color(0xFFDDE3E8),
    surfaceContainerLow = Color(0xFFEFF4F9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD6DBE0),
)
