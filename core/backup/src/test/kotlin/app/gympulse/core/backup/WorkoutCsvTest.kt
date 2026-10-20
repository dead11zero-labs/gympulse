package app.gympulse.core.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class WorkoutCsvTest {

    private val utc = ZoneId.of("UTC")

    // 2026-10-20T08:15:00Z (Epoch-Millisekunden)
    private val oct20 = 1_792_484_100_000L

    @Test
    fun encode_writesBomHeaderAndCrlfRows() {
        val csv = WorkoutCsv.encode(
            listOf(WorkoutCsvRow(oct20, "Kniebeuge", 1, 100.0, 5, isWarmup = false)),
            utc,
        )

        val lines = csv.removePrefix(WorkoutCsv.BOM).split("\r\n")
        assertEquals("Datum,Übung,Satznr.,Gewicht kg,Wiederholungen,Aufwärmsatz", lines[0])
        assertEquals("2026-10-20,Kniebeuge,1,100,5,nein", lines[1])
        assertEquals(3, lines.size) // Kopf, eine Zeile, leerer Rest nach dem letzten CRLF
        assertEquals(true, csv.startsWith(WorkoutCsv.BOM))
    }

    @Test
    fun encode_escapesCommaQuoteAndNewline() {
        val csv = WorkoutCsv.encode(
            listOf(WorkoutCsvRow(oct20, "Bankdrücken, \"eng\"\nGriff", 2, 62.5, 8, isWarmup = true)),
            utc,
        )

        val row = csv.removePrefix(WorkoutCsv.BOM).split("\r\n")[1]
        // Anführungszeichen im Feld sind verdoppelt, der Zeilenumbruch bleibt innerhalb des Feldes.
        assertEquals("2026-10-20,\"Bankdrücken, \"\"eng\"\"\nGriff\",2,62.5,8,ja", row)
    }

    @Test
    fun escape_onlyQuotesFieldsThatNeedIt() {
        assertEquals("plain", WorkoutCsv.escape("plain"))
        assertEquals("\"a,b\"", WorkoutCsv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", WorkoutCsv.escape("say \"hi\""))
        assertEquals("\"x\ny\"", WorkoutCsv.escape("x\ny"))
    }

    @Test
    fun encode_leavesMissingWeightAndRepsEmpty() {
        val csv = WorkoutCsv.encode(
            listOf(WorkoutCsvRow(oct20, "Plank", 1, null, null, isWarmup = false)),
            utc,
        )

        assertEquals("2026-10-20,Plank,1,,,nein", csv.removePrefix(WorkoutCsv.BOM).split("\r\n")[1])
    }

    @Test
    fun formatWeight_keepsDecimalsOnlyWhenNeeded() {
        assertEquals("80", WorkoutCsv.formatWeight(80.0))
        assertEquals("62.5", WorkoutCsv.formatWeight(62.5))
    }

    @Test
    fun encode_usesGivenZoneForDate() {
        // 2026-10-19T23:30:00Z ist in Berlin (UTC+2) bereits der 20. Oktober.
        val lateUtc = 1_792_452_600_000L
        val berlin = WorkoutCsv.encode(listOf(WorkoutCsvRow(lateUtc, "Rudern", 1, 40.0, 10, false)), ZoneId.of("Europe/Berlin"))
        val utcDate = WorkoutCsv.encode(listOf(WorkoutCsvRow(lateUtc, "Rudern", 1, 40.0, 10, false)), utc)

        assertEquals("2026-10-20", berlin.split("\r\n")[1].substringBefore(","))
        assertEquals("2026-10-19", utcDate.split("\r\n")[1].substringBefore(","))
    }
}
