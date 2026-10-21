package app.gympulse.core.data.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** Zeile der Supabase-Tabelle `backups` (siehe `supabase/migrations/0001_backups.sql`). */
@Serializable
data class CloudBackupRow(
    @SerialName("user_id") val userId: String,
    @SerialName("payload") val payload: JsonObject,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** Abbildung zwischen dem lokalen JSON-Backup und einer [CloudBackupRow]. */
object CloudBackupPayload {
    const val TABLE = "backups"

    private const val FORMAT_KEY = "format"
    private const val FORMAT_ID = "gympulse-backup"
    private const val EXPORTED_AT_KEY = "exported_at"
    private const val APP_VERSION_KEY = "app_version"

    private val json = Json { ignoreUnknownKeys = true }

    /** Verpackt den JSON-Text eines Backups als Zeile für [userId]; [now] wird als `updated_at` gesendet. */
    fun toRow(userId: String, backupJson: String, now: Instant): CloudBackupRow =
        CloudBackupRow(
            userId = userId,
            payload = parseBackup(backupJson),
            updatedAt = now.truncatedTo(ChronoUnit.MILLIS).toString(),
        )

    /** JSON-Text für den Import; prüft, dass die Zeile wirklich ein GymPulse-Backup enthält. */
    fun toBackupJson(row: CloudBackupRow): String {
        requireBackup(row.payload)
        return row.payload.toString()
    }

    fun info(row: CloudBackupRow): CloudBackupInfo = CloudBackupInfo(
        updatedAt = parseInstant(row.updatedAt),
        exportedAt = parseInstant(row.payload.string(EXPORTED_AT_KEY)),
        appVersion = row.payload.string(APP_VERSION_KEY),
    )

    private fun parseBackup(text: String): JsonObject {
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw CloudException(CloudException.Reason.INVALID_BACKUP, "Backup is not valid JSON", e)
        }
        val obj = element as? JsonObject
            ?: throw CloudException(CloudException.Reason.INVALID_BACKUP, "Backup is not a JSON object")
        requireBackup(obj)
        return obj
    }

    private fun requireBackup(obj: JsonObject) {
        if (obj.string(FORMAT_KEY) != FORMAT_ID) {
            throw CloudException(CloudException.Reason.INVALID_BACKUP, "Payload is not a GymPulse backup")
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    /** Postgres liefert `2026-10-21T08:15:30.123456+00:00`, das Backup `2026-10-21T08:15:30Z`. */
    internal fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(value).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                Instant.parse(value)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
