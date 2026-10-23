package app.gympulse.wear.ui

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil

/** Gewicht in kg ohne unnötige Nachkommastellen: 80 statt 80.0, 82,5 in deutscher Locale. */
fun formatKg(value: Double, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
        isGroupingUsed = false
    }.format(value)

/** `m:ss`, z. B. für Pausen und Satzdauer. */
fun formatCountdown(totalSeconds: Int): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

/**
 * Verbleibende Pausenzeit in ganzen Sekunden (aufgerundet) oder null, wenn keine Pause läuft
 * bzw. sie abgelaufen ist.
 */
fun restRemainingSeconds(restEndsAt: Long?, now: Long): Int? {
    if (restEndsAt == null) return null
    val remainingMillis = restEndsAt - now
    if (remainingMillis <= 0) return null
    return ceil(remainingMillis / 1000.0).toInt()
}
