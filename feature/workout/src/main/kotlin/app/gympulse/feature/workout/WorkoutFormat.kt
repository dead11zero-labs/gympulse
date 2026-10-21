package app.gympulse.feature.workout

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Parsing and formatting of set inputs and time displays. Weights are always kg. */
internal object WorkoutFormat {
    private const val MAX_INTEGER_DIGITS = 4
    private const val MAX_FRACTION_DIGITS = 2
    private const val MAX_DECIMAL_VALUE = 9_999.0
    private const val MAX_INTEGER_VALUE = 9_999
    private const val ROUNDING_FACTOR = 100.0

    /**
     * Keeps only digits and a single decimal separator (comma or dot), with at most
     * four integer and two fraction digits. Returns null if the input cannot be accepted.
     */
    fun sanitizeDecimal(input: String): String? {
        val text = input.trim()
        if (text.any { !it.isDigit() && it != ',' && it != '.' }) return null
        val separators = text.count { it == ',' || it == '.' }
        if (separators > 1) return null
        val parts = text.split(',', '.')
        if (parts[0].length > MAX_INTEGER_DIGITS) return null
        if (parts.size == 2 && parts[1].length > MAX_FRACTION_DIGITS) return null
        return text
    }

    /** Keeps only digits, at most four of them. Returns null if the input cannot be accepted. */
    fun sanitizeInteger(input: String): String? {
        val text = input.trim()
        if (text.any { !it.isDigit() } || text.length > MAX_INTEGER_DIGITS) return null
        return text
    }

    /** "62,5" and "62.5" both become 62.5; blank or incomplete input becomes null. */
    fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

    fun parseInteger(text: String): Int? = text.trim().toIntOrNull()

    /** Input field text for a weight or distance: no grouping, up to two decimals. */
    fun formatDecimalInput(value: Double?, locale: Locale = Locale.getDefault()): String =
        value?.let { DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale)).format(it) }.orEmpty()

    fun formatIntegerInput(value: Int?): String = value?.toString().orEmpty()

    /** Steps a weight or distance by [delta]; blank counts as 0, the result stays within 0 and 9999.99. */
    fun adjustDecimal(current: Double?, delta: Double): Double {
        val next = ((current ?: 0.0) + delta).coerceIn(0.0, MAX_DECIMAL_VALUE)
        return Math.round(next * ROUNDING_FACTOR) / ROUNDING_FACTOR
    }

    /** Steps a rep count by [delta]; blank counts as 0, the result stays within 0 and 9999. */
    fun adjustInteger(current: Int?, delta: Int): Int = ((current ?: 0) + delta).coerceIn(0, MAX_INTEGER_VALUE)

    /** Display of a weight or volume, with grouping, e.g. "1.234,5" in German. */
    fun formatKg(value: Double, locale: Locale = Locale.getDefault()): String =
        DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(locale)).format(value)

    /** Elapsed time as "m:ss" or "h:mm:ss". */
    fun formatElapsed(totalSeconds: Long): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        val ss = secs.toString().padStart(2, '0')
        return if (hours > 0) {
            "$hours:${minutes.toString().padStart(2, '0')}:$ss"
        } else {
            "$minutes:$ss"
        }
    }
}
