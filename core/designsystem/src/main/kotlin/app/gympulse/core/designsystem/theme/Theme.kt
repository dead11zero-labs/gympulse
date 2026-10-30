package app.gympulse.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
 * @param accent gewählte Akzentfarbe für das eigene Schema. Dynamic Color hat Vorrang vor dem
 *   Akzent; [LocalAccentScheme] liefert den Akzent trotzdem (z. B. für den Kalender).
 */
@Composable
fun GymPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    accent: AccentColor = AccentColor.MINT,
    content: @Composable () -> Unit,
) {
    val accentScheme = gymPulseColorScheme(darkTheme, accent)
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        accentScheme
    }

    CompositionLocalProvider(LocalAccentScheme provides accentScheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GymPulseTypography,
            shapes = GymPulseShapes,
            content = content,
        )
    }
}

/**
 * Das GymPulse-Schema mit der gewählten Akzentfarbe, auch wenn Dynamic Color das
 * [MaterialTheme] überschreibt. Für Stellen, die bewusst die App-Farbe zeigen sollen.
 */
val LocalAccentScheme = staticCompositionLocalOf { gymPulseColorScheme(darkTheme = true) }

/** Das eigene (nicht dynamische) Kinetic-Material-Farbschema von GymPulse. */
fun gymPulseColorScheme(darkTheme: Boolean, accent: AccentColor = AccentColor.MINT): ColorScheme =
    (if (darkTheme) GymPulseDarkColors else GymPulseLightColors).withAccent(accent, darkTheme)
