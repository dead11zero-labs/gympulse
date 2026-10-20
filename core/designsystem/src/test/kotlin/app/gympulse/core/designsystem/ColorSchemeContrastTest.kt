package app.gympulse.core.designsystem

import androidx.compose.ui.graphics.Color
import app.gympulse.core.designsystem.theme.ColorContrast
import app.gympulse.core.designsystem.theme.contrastPairs
import app.gympulse.core.designsystem.theme.gymPulseColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorSchemeContrastTest {

    @Test
    fun `contrast ratio of black and white is 21`() {
        assertEquals(21.0, ColorContrast.ratio(Color.Black, Color.White), 0.01)
        assertEquals(21.0, ColorContrast.ratio(Color.White, Color.Black), 0.01)
    }

    @Test
    fun `contrast ratio of identical colors is 1`() {
        assertEquals(1.0, ColorContrast.ratio(Color(0xFFAE3200), Color(0xFFAE3200)), 0.0001)
    }

    @Test
    fun `known reference ratio matches WCAG`() {
        // #767676 auf Weiß ist der bekannte Grenzwert für AA (≈ 4.54:1).
        assertEquals(4.54, ColorContrast.ratio(Color(0xFF767676), Color.White), 0.01)
    }

    @Test
    fun `light scheme meets minimum contrast for all tonal pairs`() {
        assertAllPairsPass(dark = false)
    }

    @Test
    fun `dark scheme meets minimum contrast for all tonal pairs`() {
        assertAllPairsPass(dark = true)
    }

    @Test
    fun `light and dark schemes are complete and distinct`() {
        val light = gymPulseColorScheme(darkTheme = false)
        val dark = gymPulseColorScheme(darkTheme = true)
        // Kein Token darf auf dem Material-Standard (Unspecified) stehen bleiben.
        listOf(light, dark).forEach { scheme ->
            scheme.contrastPairs().forEach { pair ->
                assertNotEquals(pair.name, Color.Unspecified, pair.foreground)
                assertNotEquals(pair.name, Color.Unspecified, pair.background)
            }
        }
        assertNotEquals(light.surface, dark.surface)
        assertNotEquals(light.primary, dark.primary)
        // Dunkles Schema: Fläche dunkler als Text, helles Schema umgekehrt.
        assertTrue(ColorContrast.relativeLuminance(dark.surface) < ColorContrast.relativeLuminance(dark.onSurface))
        assertTrue(ColorContrast.relativeLuminance(light.surface) > ColorContrast.relativeLuminance(light.onSurface))
    }

    @Test
    fun `surface containers rise in emphasis`() {
        // Hell: Container werden dunkler, dunkel: heller (tonale Elevation).
        val light = gymPulseColorScheme(darkTheme = false)
        val lightSteps = listOf(
            light.surfaceContainerLowest, light.surfaceContainerLow, light.surfaceContainer,
            light.surfaceContainerHigh, light.surfaceContainerHighest,
        ).map(ColorContrast::relativeLuminance)
        assertEquals(lightSteps.sortedDescending(), lightSteps)

        val dark = gymPulseColorScheme(darkTheme = true)
        val darkSteps = listOf(
            dark.surfaceContainerLowest, dark.surfaceContainerLow, dark.surfaceContainer,
            dark.surfaceContainerHigh, dark.surfaceContainerHighest,
        ).map(ColorContrast::relativeLuminance)
        assertEquals(darkSteps.sorted(), darkSteps)
    }

    private fun assertAllPairsPass(dark: Boolean) {
        val pairs = gymPulseColorScheme(darkTheme = dark).contrastPairs()
        assertTrue(pairs.isNotEmpty())
        val failures = pairs.filterNot { it.passes }
            .map { "${it.name}: ${"%.2f".format(it.ratio)} < ${it.minimum}" }
        assertTrue("Kontrast zu gering (dark=$dark):\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
