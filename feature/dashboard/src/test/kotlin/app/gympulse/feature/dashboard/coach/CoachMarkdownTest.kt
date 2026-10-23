package app.gympulse.feature.dashboard.coach

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class CoachMarkdownTest {

    @Test
    fun bold_isStyledAndMarkersDisappear() {
        val result = renderCoachMarkdown("Das ist **stark** heute")
        assertEquals("Das ist stark heute", result.text)
        val span = result.spanStyles.single()
        assertEquals(FontWeight.Bold, span.item.fontWeight)
        assertEquals("stark", result.text.substring(span.start, span.end))
    }

    @Test
    fun italic_isStyled() {
        val result = renderCoachMarkdown("ein *Hauch* Ruhe")
        assertEquals("ein Hauch Ruhe", result.text)
        assertEquals(FontStyle.Italic, result.spanStyles.single().item.fontStyle)
    }

    @Test
    fun bulletsAndHeadings_areConverted() {
        val result = renderCoachMarkdown("## Fazit\n- Bankdrücken\n* Kniebeuge\n1. Rudern")
        assertEquals("Fazit\n• Bankdrücken\n• Kniebeuge\n1. Rudern", result.text)
        assertEquals(FontWeight.Bold, result.spanStyles.first().item.fontWeight)
    }

    @Test
    fun unfinishedMarkers_stayLiteral() {
        assertEquals("halb **fett", renderCoachMarkdown("halb **fett").text)
        assertEquals("2 * 3 = 6", renderCoachMarkdown("2 * 3 = 6").text)
    }
}
