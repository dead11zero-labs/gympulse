package app.gympulse.feature.library.data

/** Liest eine Dezimalzahl, Komma und Punkt sind erlaubt; null bei Leerstring oder Unsinn. */
fun parseDecimalOrNull(text: String): Double? {
    val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
    return value.takeIf { it.isFinite() }
}

/** Liest eine ganze Zahl; null bei Leerstring oder Unsinn. */
fun parseIntOrNull(text: String): Int? = text.trim().toIntOrNull()
