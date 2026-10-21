package app.gympulse.core.data.cloud

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class CloudBackupPayloadTest {

    private val backup = """
        {"format":"gympulse-backup","schema_version":1,"exported_at":"2026-10-20T08:15:30Z",
         "app_version":"0.1.0","tables":{"workout_session":[{"id":"a","updated_at":1}]}}
    """.trimIndent()

    @Test
    fun toRowKeepsPayloadAndSetsUserAndTimestamp() {
        val now = Instant.parse("2026-10-21T10:00:00.123456Z")
        val row = CloudBackupPayload.toRow("user-1", backup, now)

        assertEquals("user-1", row.userId)
        assertEquals("2026-10-21T10:00:00.123Z", row.updatedAt)
        assertEquals(JsonPrimitive("gympulse-backup"), row.payload["format"])
        assertEquals(Json.parseToJsonElement(backup).jsonObject, row.payload)
    }

    @Test
    fun roundTripReturnsEquivalentJson() {
        val row = CloudBackupPayload.toRow("user-1", backup, Instant.EPOCH)
        val restored = CloudBackupPayload.toBackupJson(row)
        assertEquals(Json.parseToJsonElement(backup), Json.parseToJsonElement(restored))
    }

    @Test
    fun infoReadsServerAndExportTimestamps() {
        val row = CloudBackupPayload.toRow("user-1", backup, Instant.EPOCH)
            .copy(updatedAt = "2026-10-21T17:53:37.986123+00:00")
        val info = CloudBackupPayload.info(row)

        assertEquals(Instant.parse("2026-10-21T17:53:37.986123Z"), info.updatedAt)
        assertEquals(Instant.parse("2026-10-20T08:15:30Z"), info.exportedAt)
        assertEquals("0.1.0", info.appVersion)
    }

    @Test
    fun infoToleratesMissingOrBrokenTimestamps() {
        val row = CloudBackupPayload.toRow("user-1", backup, Instant.EPOCH).copy(updatedAt = "yesterday")
        assertNull(CloudBackupPayload.info(row).updatedAt)
        assertNull(CloudBackupPayload.parseInstant(null))
    }

    @Test
    fun rowWireFormatUsesSnakeCaseColumns() {
        val row = CloudBackupRow(userId = "u", payload = Json.parseToJsonElement("{}").jsonObject, updatedAt = "t")
        val encoded = Json.encodeToJsonElement(CloudBackupRow.serializer(), row).jsonObject
        assertEquals(setOf("user_id", "payload", "updated_at"), encoded.keys)
    }

    @Test
    fun invalidJsonIsRejected() {
        assertInvalid { CloudBackupPayload.toRow("u", "not json", Instant.EPOCH) }
        assertInvalid { CloudBackupPayload.toRow("u", "[1,2]", Instant.EPOCH) }
        assertInvalid { CloudBackupPayload.toRow("u", """{"format":"other"}""", Instant.EPOCH) }
    }

    @Test
    fun foreignPayloadIsNotRestored() {
        val row = CloudBackupRow("u", Json.parseToJsonElement("""{"hello":"world"}""").jsonObject)
        assertInvalid { CloudBackupPayload.toBackupJson(row) }
    }

    private fun assertInvalid(block: () -> Unit) {
        try {
            block()
            fail("Expected CloudException")
        } catch (e: CloudException) {
            assertEquals(CloudException.Reason.INVALID_BACKUP, e.reason)
        }
    }
}
