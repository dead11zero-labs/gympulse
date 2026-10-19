package app.gympulse.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Zentrales Theme der App. Alle Screens nutzen ausschließlich die Tokens aus
 * [MaterialTheme] (colorScheme, typography, shapes), keine hartcodierten Farben.
 *
 * @param dynamicColor Material You: Farben aus dem Hintergrundbild (Android 12+).
 */
@Composable
fun GymPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GymPulseTypography,
        shapes = GymPulseShapes,
        content = content,
    )
}
