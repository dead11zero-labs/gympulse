package app.gympulse.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Wählbare Akzentfarbe der App. [MINT] ist das Kinetic-Material-Grün (Standard); die anderen
 * tauschen nur die Primary-Rollen (primary, primaryContainer, inversePrimary, surfaceTint) des
 * Kinetic-Schemas aus. Flächen, Secondary und Tertiary bleiben, damit der Kontrast stimmt
 * (siehe ColorSchemeContrastTest, der jede Akzentfarbe in Hell und Dunkel prüft).
 *
 * [swatch] ist der Farbton für die Vorschau in den Einstellungen (dunkles `primary`).
 */
enum class AccentColor(val swatch: Color) {
    MINT(Color(0xFF0DF5A4)),
    BLUE(Color(0xFF7DB7FF)),
    VIOLET(Color(0xFFCDB5FF)),
    PINK(Color(0xFFFFAFD0)),
    ORANGE(Color(0xFFFFB374)),
}

private class AccentRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val inversePrimary: Color,
    val surfaceTint: Color,
)

private fun roles(
    primary: Long,
    onPrimary: Long,
    container: Long,
    onContainer: Long,
    inverse: Long,
    tint: Long = primary,
) = AccentRoles(
    Color(primary), Color(onPrimary), Color(container), Color(onContainer), Color(inverse), Color(tint),
)

private fun AccentColor.darkRoles(): AccentRoles? = when (this) {
    AccentColor.MINT -> null
    AccentColor.BLUE -> roles(0xFF7DB7FF, 0xFF00315E, 0xFF0A4A87, 0xFFD3E4FF, 0xFF1F5FA6)
    AccentColor.VIOLET -> roles(0xFFCDB5FF, 0xFF3A1A7A, 0xFF53399A, 0xFFEADDFF, 0xFF6A4FB5)
    AccentColor.PINK -> roles(0xFFFFAFD0, 0xFF5E1138, 0xFF7C2A50, 0xFFFFD8E6, 0xFFA0406C)
    AccentColor.ORANGE -> roles(0xFFFFB374, 0xFF4A2400, 0xFF6B3600, 0xFFFFDCC0, 0xFF8C4A00)
}

private fun AccentColor.lightRoles(): AccentRoles? = when (this) {
    AccentColor.MINT -> null
    AccentColor.BLUE -> roles(0xFF1F5FA6, 0xFFFFFFFF, 0xFFD3E4FF, 0xFF001C3A, 0xFF7DB7FF)
    AccentColor.VIOLET -> roles(0xFF634AAE, 0xFFFFFFFF, 0xFFEADDFF, 0xFF230B5C, 0xFFCDB5FF)
    AccentColor.PINK -> roles(0xFF953660, 0xFFFFFFFF, 0xFFFFD8E6, 0xFF3D0522, 0xFFFFAFD0)
    AccentColor.ORANGE -> roles(0xFF8C4A00, 0xFFFFFFFF, 0xFFFFDCC0, 0xFF2D1600, 0xFFFFB374)
}

internal fun ColorScheme.withAccent(accent: AccentColor, darkTheme: Boolean): ColorScheme {
    val roles = (if (darkTheme) accent.darkRoles() else accent.lightRoles()) ?: return this
    return copy(
        primary = roles.primary,
        onPrimary = roles.onPrimary,
        primaryContainer = roles.primaryContainer,
        onPrimaryContainer = roles.onPrimaryContainer,
        inversePrimary = roles.inversePrimary,
        surfaceTint = roles.surfaceTint,
    )
}
