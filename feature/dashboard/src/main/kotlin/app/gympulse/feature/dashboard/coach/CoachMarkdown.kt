package app.gympulse.feature.dashboard.coach

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Kleiner Markdown-Renderer für den Coach-Text: Überschriften (#), Aufzählungen (-, *, 1.),
 * **fett**, *kursiv* und `code`. Nicht abgeschlossene Zeichen (der Text streamt) bleiben unverändert stehen.
 */
internal fun renderCoachMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    text.lines().forEachIndexed { index, rawLine ->
        if (index > 0) append('\n')
        val line = rawLine.trimEnd()
        val heading = HEADING.matchEntire(line)
        val bullet = BULLET.matchEntire(line)
        val numbered = NUMBERED.matchEntire(line)
        when {
            heading != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(heading.groupValues[1]) }
            bullet != null -> {
                append("• ")
                appendInline(bullet.groupValues[1])
            }
            numbered != null -> {
                append(numbered.groupValues[1]).append(". ")
                appendInline(numbered.groupValues[2])
            }
            else -> appendInline(line)
        }
    }
}

private fun AnnotatedString.Builder.appendInline(text: String) {
    var i = 0
    while (i < text.length) {
        val (marker, style) = when {
            text.startsWith("**", i) -> "**" to SpanStyle(fontWeight = FontWeight.Bold)
            text.startsWith("__", i) -> "__" to SpanStyle(fontWeight = FontWeight.Bold)
            text[i] == '`' -> "`" to SpanStyle(fontFamily = FontFamily.Monospace)
            text[i] == '*' || text[i] == '_' -> text[i].toString() to SpanStyle(fontStyle = FontStyle.Italic)
            else -> "" to null
        }
        val close = if (style != null) text.indexOf(marker, i + marker.length) else -1
        // Ein Marker ohne Gegenstück oder mit leerem Inhalt ist normaler Text (z. B. "2 * 3").
        if (style != null && close > i + marker.length && !text[i + marker.length].isWhitespace()) {
            withStyle(style) { append(text.substring(i + marker.length, close)) }
            i = close + marker.length
        } else {
            append(text[i])
            i++
        }
    }
}

private val HEADING = Regex("""#{1,6}\s+(.+)""")
private val BULLET = Regex("""\s*[-*•]\s+(.+)""")
private val NUMBERED = Regex("""\s*(\d+)[.)]\s+(.+)""")
