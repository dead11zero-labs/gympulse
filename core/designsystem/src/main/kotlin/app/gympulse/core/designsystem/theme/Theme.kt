package app.gympulse.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Zentrales Theme der App. Alle Screens nutzen ausschließlich die Tokens aus
 * [MaterialTheme] (colorScheme, typography, shapes) sowie [Spacing], [Elevation], [Sizing]
 * und [ExpressiveShapes]; keine hartcodierten Farben oder Größen.
 *
 * @param darkTheme dunkles Schema; die App reicht hier die Wahl aus den Einstellungen durch.
 * @param dynamicColor Material You: Farben aus dem Hintergrundbild (Android 12+), optional per
 *   Einstellung. Standard ist aus: Dann (und auf älteren Geräten) gilt das eigene
 *   Kinetic-Material-Schema (Seed [GymPulseSeedColor], dunkel als Standard der App).
 */
@Composable
fun GymPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        gymPulseColorScheme(darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GymPulseTypography,
        shapes = GymPulseShapes,
        content = content,
    )
}

/** Das eigene (nicht dynamische) Kinetic-Material-Farbschema von GymPulse. */
fun gymPulseColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) GymPulseDarkColors else GymPulseLightColors
