package app.gympulse.feature.settings

import app.gympulse.core.model.MeasurementType
import app.gympulse.core.settings.SettingsLimits

/** Ergebnis einer Eingabeprüfung. */
internal sealed interface Validated<out T> {
    data class Ok<out T>(val value: T) : Validated<T>
    data object Invalid : Validated<Nothing>
}

/** Prüfung der Texteingaben aus den Dialogen. Dezimaltrenner Punkt oder Komma. */
internal object InputValidation {
    val WEIGHT_KG: ClosedFloatingPointRange<Double> = 20.0..400.0
    val HEIGHT_CM: ClosedFloatingPointRange<Double> = 100.0..250.0
    val BODY_FAT_PERCENT: ClosedFloatingPointRange<Double> = 3.0..60.0
    val CIRCUMFERENCE_CM: ClosedFloatingPointRange<Double> = 10.0..300.0
    const val NAME_MAX_LENGTH = 50

    private val decimalPattern = Regex("""\d{1,3}([.,]\d{1,2})?""")
    private val integerPattern = Regex("""\d{1,4}""")

    private fun parseDecimal(input: String): Double? {
        val text = input.trim()
        if (!decimalPattern.matches(text)) return null
        return text.replace(',', '.').toDoubleOrNull()
    }

    /** Körpergewicht in kg, Pflichtfeld, 20 bis 400. */
    fun weightKg(input: String): Validated<Double> {
        val value = parseDecimal(input)
        return if (value != null && value in WEIGHT_KG) Validated.Ok(value) else Validated.Invalid
    }

    /** Größe in cm, optional (leer = nicht gesetzt), 100 bis 250. */
    fun heightCm(input: String): Validated<Double?> {
        if (input.isBlank()) return Validated.Ok(null)
        val value = parseDecimal(input)
        return if (value != null && value in HEIGHT_CM) Validated.Ok(value) else Validated.Invalid
    }

    /** Name, optional (leer = nicht gesetzt), höchstens [NAME_MAX_LENGTH] Zeichen. */
    fun name(input: String): Validated<String?> {
        val text = input.trim()
        return if (text.length <= NAME_MAX_LENGTH) Validated.Ok(text.ifEmpty { null }) else Validated.Invalid
    }

    fun restTimerSeconds(input: String): Validated<Int> = integer(input, SettingsLimits.REST_TIMER_SECONDS)

    fun weeklyGoal(input: String): Validated<Int> = integer(input, SettingsLimits.WEEKLY_GOAL)

    private fun integer(input: String, range: IntRange): Validated<Int> {
        val text = input.trim()
        if (!integerPattern.matches(text)) return Validated.Invalid
        val value = text.toIntOrNull()
        return if (value != null && value in range) Validated.Ok(value) else Validated.Invalid
    }

    /** Zulässiger Wertebereich eines Körpermaßes: kg, Prozent oder cm je nach Typ. */
    fun rangeFor(type: MeasurementType): ClosedFloatingPointRange<Double> = when (type) {
        MeasurementType.WEIGHT -> WEIGHT_KG
        MeasurementType.BODY_FAT -> BODY_FAT_PERCENT
        else -> CIRCUMFERENCE_CM
    }

    /** Pflichtwert eines Körpermaßes im Bereich von [rangeFor]. */
    fun measurement(type: MeasurementType, input: String): Validated<Double> {
        val value = parseDecimal(input)
        return if (value != null && value in rangeFor(type)) Validated.Ok(value) else Validated.Invalid
    }

    /** Optionaler Wert eines Körpermaßes (leer = nicht erfasst). */
    fun optionalMeasurement(type: MeasurementType, input: String): Validated<Double?> {
        if (input.isBlank()) return Validated.Ok(null)
        return when (val result = measurement(type, input)) {
            is Validated.Ok -> Validated.Ok(result.value)
            Validated.Invalid -> Validated.Invalid
        }
    }
}
