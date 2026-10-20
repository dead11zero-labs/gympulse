package app.gympulse.core.designsystem

import androidx.compose.ui.unit.dp
import app.gympulse.core.designsystem.adaptive.WindowWidthClass
import app.gympulse.core.designsystem.component.ProgressMath
import app.gympulse.core.designsystem.component.StatTrend
import app.gympulse.core.designsystem.component.TrendDirection
import app.gympulse.core.designsystem.theme.Spacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveAndProgressTest {

    @Test
    fun `window width classes follow Material 3 breakpoints`() {
        assertEquals(WindowWidthClass.Compact, WindowWidthClass.fromWidth((-1).dp))
        assertEquals(WindowWidthClass.Compact, WindowWidthClass.fromWidth(0.dp))
        assertEquals(WindowWidthClass.Compact, WindowWidthClass.fromWidth(599.dp))
        assertEquals(WindowWidthClass.Medium, WindowWidthClass.fromWidth(600.dp))
        assertEquals(WindowWidthClass.Medium, WindowWidthClass.fromWidth(839.dp))
        assertEquals(WindowWidthClass.Expanded, WindowWidthClass.fromWidth(840.dp))
        assertEquals(WindowWidthClass.Large, WindowWidthClass.fromWidth(1200.dp))
        assertEquals(WindowWidthClass.ExtraLarge, WindowWidthClass.fromWidth(1600.dp))
        assertEquals(WindowWidthClass.ExtraLarge, WindowWidthClass.fromWidth(4000.dp))
    }

    @Test
    fun `width class drives navigation, margins and columns`() {
        assertFalse(WindowWidthClass.Compact.isAtLeastMedium)
        assertTrue(WindowWidthClass.Medium.isAtLeastMedium)
        assertFalse(WindowWidthClass.Medium.isAtLeastExpanded)
        assertTrue(WindowWidthClass.Expanded.isAtLeastExpanded)
        assertEquals(Spacing.screenMargin, WindowWidthClass.Compact.horizontalMargin)
        assertEquals(Spacing.screenMarginWide, WindowWidthClass.Large.horizontalMargin)
        assertEquals(listOf(4, 8, 12, 12, 12), WindowWidthClass.entries.map { it.gridColumns })
        assertEquals(listOf(2, 3, 4, 4, 4), WindowWidthClass.entries.map { it.statTileColumns })
        // Maximale Inhaltsbreite entspricht dem Expanded-Breakpoint.
        assertEquals(WindowWidthClass.Expanded.minWidth, Spacing.maxContentWidth)
    }

    @Test
    fun `progress fraction is clamped and safe`() {
        assertEquals(0.5f, ProgressMath.fraction(5.0, 10.0), 0.0001f)
        assertEquals(1f, ProgressMath.fraction(15.0, 10.0), 0.0001f)
        assertEquals(0f, ProgressMath.fraction(-3.0, 10.0), 0.0001f)
        assertEquals(0f, ProgressMath.fraction(5.0, 0.0), 0.0001f)
        assertEquals(0f, ProgressMath.fraction(Double.NaN, 10.0), 0.0001f)
        assertEquals(0f, ProgressMath.clamp(Float.NaN), 0.0001f)
        assertEquals(1f, ProgressMath.clamp(1.7f), 0.0001f)
    }

    @Test
    fun `progress percent rounds to whole numbers`() {
        assertEquals(0, ProgressMath.percent(0f))
        assertEquals(67, ProgressMath.percent(2f / 3f))
        assertEquals(100, ProgressMath.percent(3f))
    }

    @Test
    fun `trend sentiment defaults to rising is good`() {
        assertTrue(StatTrend("+5 %", TrendDirection.Up).isPositive)
        assertFalse(StatTrend("-5 %", TrendDirection.Down).isPositive)
        assertTrue(StatTrend("-1 %", TrendDirection.Down, isPositive = true).isPositive)
    }
}
