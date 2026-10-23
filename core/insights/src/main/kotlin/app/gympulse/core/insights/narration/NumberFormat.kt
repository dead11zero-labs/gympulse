package app.gympulse.core.insights.narration

import kotlin.math.abs
import kotlin.math.roundToLong

/** Locale-unabhängige Zahlenausgabe: höchstens eine Nachkommastelle, im Deutschen mit Komma. */
internal fun formatNumber(value: Double, language: AppLanguage): String {
    val tenths = (value * 10.0).roundToLong()
    val whole = tenths / 10
    val frac = abs(tenths % 10)
    val sign = if (tenths < 0 && whole == 0L) "-" else ""
    val separator = if (language == AppLanguage.DE) "," else "."
    return if (frac == 0L) "$sign$whole" else "$sign$whole$separator$frac"
}

/** Anzeigename ohne Steuerzeichen, Zeilenumbrüche und Trennzeichen, begrenzt in der Länge (Prompt-Hygiene). */
internal fun sanitizeName(raw: String, maxLength: Int = 40): String {
    val cleaned = raw.map { if (it.isISOControl() || it == '|' || it == '`') ' ' else it }
        .joinToString("")
        .trim()
        .replace(Regex("\\s+"), " ")
    return if (cleaned.length <= maxLength) cleaned else cleaned.take(maxLength - 1).trimEnd() + "…"
}
