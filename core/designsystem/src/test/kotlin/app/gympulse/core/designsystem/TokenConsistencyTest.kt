package app.gympulse.core.designsystem

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gympulse.core.designsystem.theme.Elevation
import app.gympulse.core.designsystem.theme.GymPulseTypography
import app.gympulse.core.designsystem.theme.PlusJakartaSans
import app.gympulse.core.designsystem.theme.RobotoFlex
import app.gympulse.core.designsystem.theme.ShapeTokens
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.designsystem.theme.TabularNumbers
import app.gympulse.core.designsystem.theme.metricHero
import app.gympulse.core.designsystem.theme.metricLarge
import app.gympulse.core.designsystem.theme.metricMedium
import app.gympulse.core.designsystem.theme.metricSmall
import app.gympulse.core.designsystem.theme.metricUnit
import app.gympulse.core.designsystem.theme.setValue
import app.gympulse.core.designsystem.theme.stepperValue
import app.gympulse.core.designsystem.theme.tabularNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenConsistencyTest {

    @Test
    fun `spacing follows the 4 dp grid and rises strictly`() {
        Spacing.scale.forEach { assertOnGrid(it, 4f) }
        assertStrictlyAscending(Spacing.scale)
        // Ab 8 dp gilt das 8-dp-Raster (12 dp ist die erlaubte Zwischenstufe).
        listOf(Spacing.xs, Spacing.m, Spacing.l, Spacing.xl, Spacing.xxl, Spacing.xxxl).forEach { assertOnGrid(it, 8f) }
        assertOnGrid(Spacing.maxContentWidth, 8f)
        assertOnGrid(Spacing.maxFormWidth, 8f)
    }

    @Test
    fun `elevation levels match Material 3`() {
        assertEquals(listOf(0.dp, 1.dp, 3.dp, 6.dp, 8.dp, 12.dp), Elevation.scale)
    }

    @Test
    fun `shape scale follows Kinetic Material rounding`() {
        assertEquals(
            listOf(0.dp, 4.dp, 8.dp, 16.dp, 24.dp, 28.dp, 32.dp, 40.dp, 48.dp),
            ShapeTokens.scale,
        )
        assertStrictlyAscending(ShapeTokens.scale)
        // DESIGN.md rounded: sm 8, DEFAULT 16, md 24, lg 32, xl 48.
        assertEquals(8.dp, ShapeTokens.small)
        assertEquals(16.dp, ShapeTokens.medium)
        assertEquals(24.dp, ShapeTokens.large)
        assertEquals(32.dp, ShapeTokens.extraLarge)
        assertEquals(48.dp, ShapeTokens.extraExtraLarge)
    }

    @Test
    fun `window margins and gutters follow Kinetic Material`() {
        assertEquals(16.dp, Spacing.marginCompact)
        assertEquals(12.dp, Spacing.gutterCompact)
        assertEquals(32.dp, Spacing.marginMedium)
        assertEquals(20.dp, Spacing.gutterMedium)
        assertOnGrid(Spacing.gutterMedium, 4f)
    }

    @Test
    fun `typography follows Kinetic Material`() {
        val t = GymPulseTypography
        // Plus Jakarta Sans für Display/Headline/Title/Label, Roboto Flex für Body.
        listOf(t.displayLarge, t.headlineLarge, t.titleLarge, t.labelLarge, t.labelSmall).forEach {
            assertEquals(PlusJakartaSans, it.fontFamily)
        }
        listOf(t.bodyLarge, t.bodyMedium, t.bodySmall).forEach { assertEquals(RobotoFlex, it.fontFamily) }
        assertEquals(56f, t.displayLarge.fontSize.value)
        assertEquals(FontWeight.ExtraBold, t.displayLarge.fontWeight)
        assertEquals(32f, t.headlineLarge.fontSize.value)
        assertEquals(22f, t.headlineMedium.fontSize.value)
        assertEquals(18f, t.titleLarge.fontSize.value)
        assertEquals(FontWeight.Bold, t.titleLarge.fontWeight)
        assertEquals(16f, t.bodyLarge.fontSize.value)
        assertEquals(10f, t.labelSmall.fontSize.value)
        assertEquals(0.06f, t.labelSmall.letterSpacing.value, 0.0001f)
        assertEquals(-0.02f, t.displayLarge.letterSpacing.value, 0.0001f)
    }

    @Test
    fun `tabular numbers are available for timers, weight and reps`() {
        val t = GymPulseTypography
        assertEquals("tnum", TabularNumbers.FEATURE_SETTINGS)
        assertEquals("tnum", t.bodyLarge.tabularNumbers().fontFeatureSettings)
        assertEquals("tnum", t.stepperValue.fontFeatureSettings)
        assertEquals("tnum", t.setValue.fontFeatureSettings)
        assertEquals(t.headlineMedium.fontSize, t.stepperValue.fontSize)
    }

    @Test
    fun `touch targets and icons are accessible`() {
        assertTrue(Sizing.minTouchTarget >= 48.dp)
        assertTrue(Sizing.iconContainer >= Sizing.icon)
        assertTrue(Sizing.emptyStateIconContainer > Sizing.emptyStateIcon)
        assertTrue(Sizing.progressRing > Sizing.progressRingStroke * 4)
    }

    @Test
    fun `type scale keeps a clear hierarchy`() {
        val t = GymPulseTypography
        val sizes = listOf(
            t.displayLarge, t.displayMedium, t.displaySmall,
            t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall,
        ).map { it.fontSize.value }
        assertEquals(sizes.sortedDescending(), sizes)
        assertTrue(t.bodyLarge.fontSize.value >= t.bodyMedium.fontSize.value)
        assertTrue(t.bodyMedium.fontSize.value >= t.bodySmall.fontSize.value)
        assertTrue(t.labelLarge.fontSize.value >= t.labelSmall.fontSize.value)
    }

    @Test
    fun `metric styles are emphasized with tabular figures`() {
        val t = GymPulseTypography
        val metrics = listOf(t.metricHero, t.metricLarge, t.metricMedium, t.metricSmall)
        metrics.forEach { style ->
            assertEquals("tnum", style.fontFeatureSettings)
            assertTrue((style.fontWeight ?: FontWeight.Normal) >= FontWeight.SemiBold)
        }
        val sizes = metrics.map { it.fontSize.value }
        assertEquals(sizes.sortedDescending(), sizes)
        assertTrue(t.metricUnit.fontSize.value < t.metricLarge.fontSize.value)
        // Kennzahlen sind größer als Fließtext.
        assertTrue(t.metricSmall.fontSize.value > t.bodyLarge.fontSize.value)
    }

    private fun assertOnGrid(value: Dp, step: Float) {
        assertEquals("$value liegt nicht im $step-dp-Raster", 0f, value.value % step, 0.0001f)
    }

    private fun assertStrictlyAscending(values: List<Dp>) {
        values.zipWithNext().forEach { (a, b) -> assertTrue("$a < $b", a < b) }
    }
}
