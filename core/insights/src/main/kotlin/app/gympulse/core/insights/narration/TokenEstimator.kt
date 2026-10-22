package app.gympulse.core.insights.narration

import kotlin.math.ceil

/**
 * Grobe, bewusst konservative Token-Schätzung ohne Tokenizer: 1 Token je 3 Zeichen. Gemma-artige
 * Tokenizer liegen bei Englisch um 4 und bei Deutsch um 3,2 Zeichen je Token, die Schätzung
 * überschätzt also eher und hält das Budget damit sicher ein.
 */
object TokenEstimator {
    const val CHARS_PER_TOKEN: Double = 3.0

    fun estimate(text: String): Int = ceil(text.length / CHARS_PER_TOKEN).toInt()
}
