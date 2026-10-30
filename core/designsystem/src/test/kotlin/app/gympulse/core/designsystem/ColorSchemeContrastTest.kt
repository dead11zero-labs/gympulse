package app.gympulse.core.designsystem

import androidx.compose.ui.graphics.Color
import app.gympulse.core.designsystem.theme.AccentColor
import app.gympulse.core.designsystem.theme.ColorContrast
import app.gympulse.core.designsystem.theme.GymPulseSeedColor
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

    @Test
    fun `dark scheme follows Kinetic Material`() {
        val dark = gymPulseColorScheme(darkTheme = true)
        // Leuchtende Akzente laut DESIGN.md auf den Rollen, die Material3-Komponenten nutzen.
        assertEquals(Color(0xFF0DF5A4), dark.primary)
        assertEquals(Color(0xFFFF7F1C), dark.secondary)
        assertEquals(Color(0xFF47D6FF), dark.tertiary)
        // Flächen 1:1 aus dem YAML-Frontmatter.
        assertEquals(Color(0xFF0E141C), dark.surface)
        assertEquals(Color(0xFF161C24), dark.surfaceContainerLow)
        assertEquals(Color(0xFF1A2028), dark.surfaceContainer)
        assertEquals(Color(0xFF242A33), dark.surfaceContainerHigh)
        assertEquals(Color(0xFF3B4A40), dark.outlineVariant)
        assertEquals(0xFF0DF5A4, GymPulseSeedColor)
    }

    @Test
    fun `tinted badge and chip pairs are checked in both schemes`() {
        listOf(true, false).forEach { dark ->
            val names = gymPulseColorScheme(darkTheme = dark).contrastPairs().map { it.name }
            assertTrue(names.any { it.startsWith("secondary/prBadge") })
            assertTrue("tertiary/statusChip" in names)
            assertTrue("onSurface/statusChip" in names)
            assertTrue("secondary/surfaceContainer" in names)
        }
    }

    @Test
    fun `navigation indicator pair has enough contrast`() {
        listOf(true, false).forEach { dark ->
            val scheme = gymPulseColorScheme(darkTheme = dark)
            assertTrue(ColorContrast.ratio(scheme.onPrimary, scheme.primary) >= ColorContrast.MIN_TEXT)
            // Pille hebt sich von der Leiste ab (Bedienelement-Grenze 3:1).
            assertTrue(ColorContrast.ratio(scheme.primary, scheme.surfaceContainerLowest) >= ColorContrast.MIN_NON_TEXT)
        }
    }

    @Test
    fun `every accent color meets minimum contrast in both schemes`() {
        AccentColor.entries.forEach { accent ->
            listOf(true, false).forEach { dark -> assertAllPairsPass(dark, accent) }
        }
    }

    @Test
    fun `accents change only the primary roles and mint is the default`() {
        listOf(true, false).forEach { dark ->
            val base = gymPulseColorScheme(dark)
            assertEquals(base, gymPulseColorScheme(dark, AccentColor.MINT))
            AccentColor.entries.filter { it != AccentColor.MINT }.forEach { accent ->
                val scheme = gymPulseColorScheme(dark, accent)
                assertNotEquals(base.primary, scheme.primary)
                assertEquals(base.surface, scheme.surface)
                assertEquals(base.secondary, scheme.secondary)
                assertEquals(base.tertiary, scheme.tertiary)
            }
        }
    }

    private fun assertAllPairsPass(dark: Boolean, accent: AccentColor = AccentColor.MINT) {
        val pairs = gymPulseColorScheme(darkTheme = dark, accent = accent).contrastPairs()
        assertTrue(pairs.isNotEmpty())
        val failures = pairs.filterNot { it.passes }
            .map { "${it.name}: ${"%.2f".format(it.ratio)} < ${it.minimum}" }
        assertTrue("Kontrast zu gering (dark=$dark, accent=$accent):\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
