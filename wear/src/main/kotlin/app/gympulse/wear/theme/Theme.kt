package app.gympulse.wear.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/*
 * Kleines Wear-Theme mit den Kinetic-Farben der Handy-App (core:designsystem, dunkles Schema).
 * Ausnahme zur Regel "nur Tokens aus core:designsystem": Wear Material 3 hat ein eigenes
 * ColorScheme/Typography, daher liegen die Tokens hier. Die Uhr ist immer dunkel (AMOLED).
 */
private val GymPulseWearColors = ColorScheme(
    primary = Color(0xFF0DF5A4),
    primaryDim = Color(0xFF00E297),
    primaryContainer = Color(0xFF005234),
    onPrimary = Color(0xFF003822),
    onPrimaryContainer = Color(0xFF4DFFB2),
    secondary = Color(0xFFFF7F1C),
    secondaryDim = Color(0xFFE56F10),
    secondaryContainer = Color(0xFF753400),
    onSecondary = Color(0xFF522300),
    onSecondaryContainer = Color(0xFFFFDBC8),
    tertiary = Color(0xFF47D6FF),
    tertiaryDim = Color(0xFF2FC2EB),
    tertiaryContainer = Color(0xFF004E60),
    onTertiary = Color(0xFF003543),
    onTertiaryContainer = Color(0xFFB6EBFF),
    surfaceContainerLow = Color(0xFF161C24),
    surfaceContainer = Color(0xFF1A2028),
    surfaceContainerHigh = Color(0xFF242A33),
    onSurface = Color(0xFFDDE3EE),
    onSurfaceVariant = Color(0xFFB9CBBE),
    outline = Color(0xFF849589),
    outlineVariant = Color(0xFF3B4A40),
    background = Color(0xFF000000),
    onBackground = Color(0xFFDDE3EE),
    error = Color(0xFFFFB4AB),
    errorDim = Color(0xFFE59C94),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun GymPulseWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GymPulseWearColors, content = content)
}
