package app.gympulse.core.backup

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Eine Zeile der CSV-Trainingshistorie: ein abgeschlossener Satz mit Kontext. */
data class WorkoutCsvRow(
    val startedAtMillis: Long,
    val exerciseName: String,
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int?,
    val isWarmup: Boolean,
)

/**
 * Erzeugt die CSV-Trainingshistorie nach RFC 4180: Trennzeichen Komma, Zeilenende CRLF, Felder mit
 * Komma, Anführungszeichen oder Zeilenumbruch werden in Anführungszeichen gesetzt und deren
 * Anführungszeichen verdoppelt. Ein UTF-8-BOM vorne sorgt dafür, dass Excel Umlaute richtig liest.
 */
object WorkoutCsv {

    const val BOM = "\uFEFF"
    const val NEWLINE = "\r\n"

    val HEADER: List<String> = listOf("Datum", "Übung", "Satznr.", "Gewicht kg", "Wiederholungen", "Aufwärmsatz")

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun encode(rows: List<WorkoutCsvRow>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        append(BOM)
        append(HEADER.joinToString(",") { escape(it) })
        append(NEWLINE)
        rows.forEach { row ->
            val date = Instant.ofEpochMilli(row.startedAtMillis).atZone(zone).toLocalDate().format(DATE_FORMAT)
            val fields = listOf(
                date,
                row.exerciseName,
                row.setNumber.toString(),
                row.weightKg?.let(::formatWeight).orEmpty(),
                row.reps?.toString().orEmpty(),
                if (row.isWarmup) "ja" else "nein",
            )
            append(fields.joinToString(",") { escape(it) })
            append(NEWLINE)
        }
    }

    /** Ganze Zahlen ohne Nachkommastellen ("80"), sonst mit Punkt ("62.5"). */
    internal fun formatWeight(kg: Double): String =
        if (kg % 1.0 == 0.0) kg.toLong().toString() else kg.toString()

    internal fun escape(field: String): String {
        val needsQuotes = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + field.replace("\"", "\"\"") + "\"" else field
    }
}
