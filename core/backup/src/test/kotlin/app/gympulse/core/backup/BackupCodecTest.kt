package app.gympulse.core.backup

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {

    @Test
    fun encodeDecode_roundTripKeepsAllFields() {
        val file = BackupFile(
            schemaVersion = BackupFormat.SCHEMA_VERSION,
            exportedAt = "2026-10-20T08:00:00Z",
            appVersion = "0.1.0",
            databaseVersion = 1,
            settings = BackupSettings(restTimerSeconds = 120, weekStart = "SUNDAY", dynamicColor = false),
            tables = mapOf(
                BackupTables.WORKOUT_PLAN to listOf(
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive("p1"),
                            "name" to JsonPrimitive("Push"),
                            "color" to JsonNull,
                            "is_archived" to JsonPrimitive(0),
                            "updated_at" to JsonPrimitive(5L),
                        ),
                    ),
                ),
            ),
        )

        val text = BackupCodec.encode(file)
        val decoded = BackupCodec.decode(text)

        assertEquals(file, decoded)
        assertTrue(text.contains("\"schema_version\": 1"))
        assertTrue(text.contains("\"format\": \"gympulse-backup\""))
    }

    @Test
    fun decode_rejectsInvalidJson() {
        assertInvalid("{ not json")
    }

    @Test
    fun decode_rejectsForeignJson() {
        assertInvalid("""{"hello": "world"}""")
        assertInvalid("""[1, 2, 3]""")
    }

    @Test
    fun decode_rejectsMissingSchemaVersion() {
        assertInvalid("""{"format": "gympulse-backup", "exported_at": "x", "app_version": "1"}""")
    }

    @Test
    fun decode_rejectsRowWithoutId() {
        assertInvalid(
            """
            {"format": "gympulse-backup", "schema_version": 1, "exported_at": "x", "app_version": "1",
             "tables": {"workout_plan": [{"name": "Push"}]}}
            """.trimIndent(),
        )
    }

    @Test
    fun decode_rejectsNestedValues() {
        assertInvalid(
            """
            {"format": "gympulse-backup", "schema_version": 1, "exported_at": "x", "app_version": "1",
             "tables": {"workout_plan": [{"id": "p1", "name": {"nested": true}}]}}
            """.trimIndent(),
        )
    }

    @Test
    fun decode_acceptsNewerVersionAndIgnoresUnknownFields() {
        val file = BackupCodec.decode(
            """
            {"format": "gympulse-backup", "schema_version": 7, "exported_at": "x", "app_version": "9.0",
             "future_field": {"a": 1},
             "tables": {"workout_plan": [{"id": "p1", "name": "Push", "future_column": 3}], "future_table": []}}
            """.trimIndent(),
        )

        assertEquals(7, file.schemaVersion)
        assertNull(file.settings)
        assertEquals("p1", file.tables.getValue("workout_plan").single().idOrNull())
    }

    private fun assertInvalid(text: String) {
        try {
            BackupCodec.decode(text)
            fail("Expected BackupException")
        } catch (e: BackupException) {
            assertEquals(BackupException.Reason.INVALID_FILE, e.reason)
        }
    }
}
