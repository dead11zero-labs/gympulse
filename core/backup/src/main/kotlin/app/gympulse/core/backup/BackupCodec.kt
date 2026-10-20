package app.gympulse.core.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** Fehler beim Export oder Import; [reason] bestimmt die Meldung in der UI. */
class BackupException(val reason: Reason, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Reason {
        /** Datei ließ sich nicht lesen oder schreiben. */
        IO,

        /** Kein gültiges JSON oder kein GymPulse-Backup. */
        INVALID_FILE,

        /** Einträge verweisen auf Übungen, Pläne o. Ä., die weder im Backup noch auf dem Gerät sind. */
        MISSING_REFERENCES,

        /** Die Datenbank hat das Schreiben abgelehnt; alle Änderungen wurden zurückgerollt. */
        WRITE_FAILED,
    }
}

/** (De-)Serialisierung und Plausibilitätsprüfung der Backup-Datei. Reine Logik ohne Android. */
object BackupCodec {
    val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** Liest und prüft eine Backup-Datei; wirft [BackupException] mit [BackupException.Reason.INVALID_FILE]. */
    fun decode(text: String): BackupFile {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: SerializationException) {
            throw invalid("Not valid JSON", e)
        } catch (e: IllegalArgumentException) {
            throw invalid("Not a JSON object", e)
        }
        val format = (root["format"] as? JsonPrimitive)?.contentOrNull
        if (format != BackupFormat.FORMAT_ID) throw invalid("Not a GymPulse backup (format=$format)")
        val version = (root["schema_version"] as? JsonPrimitive)?.longOrNull
        if (version == null || version < 1) throw invalid("Missing or invalid schema_version")

        val file = try {
            json.decodeFromJsonElement(BackupFile.serializer(), root)
        } catch (e: SerializationException) {
            throw invalid("Unexpected structure: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw invalid("Unexpected structure: ${e.message}", e)
        }
        file.tables.forEach { (table, rows) ->
            rows.forEachIndexed { index, row -> validateRow(table, index, row) }
        }
        return file
    }

    private fun validateRow(table: String, index: Int, row: JsonObject) {
        if (row.idOrNull() == null) throw invalid("Row $index in '$table' has no id")
        row.forEach { (column, value) ->
            if (value !is JsonPrimitive) {
                throw invalid("Column '$column' in '$table' row $index is not a plain value")
            }
        }
    }

    private fun invalid(message: String, cause: Throwable? = null) =
        BackupException(BackupException.Reason.INVALID_FILE, message, cause)
}

/** ID einer Zeile, nur wenn sie ein nicht leerer String ist. */
internal fun JsonObject.idOrNull(): String? =
    (this["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

internal fun JsonObject.longOrNull(column: String): Long? =
    (this[column] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull

internal fun JsonObject.stringOrNull(column: String): String? =
    (this[column] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.meta(): RowMeta =
    RowMeta(updatedAt = longOrNull("updated_at") ?: 0L, deletedAt = longOrNull("deleted_at"))
