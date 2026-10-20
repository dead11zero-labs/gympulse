package app.gympulse.core.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.gympulse.core.database.GymPulseDatabase
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * Generischer Tabellenzugriff für Export und Import über SQL statt DAOs: So bleiben Datenbank-Schema
 * und DAOs unverändert, und neue Spalten wandern ohne Codeänderung mit ins Backup.
 *
 * Alle Methoden blockieren und gehören auf einen IO-Thread.
 */
class RoomBackupStore @Inject constructor(private val database: GymPulseDatabase) {

    private val db: SupportSQLiteDatabase get() = database.openHelper.writableDatabase

    fun databaseVersion(): Int = db.version

    fun readRows(spec: TableSpec): List<JsonObject> = db.query(spec.exportSql).use { cursor ->
        val rows = ArrayList<JsonObject>(cursor.count)
        while (cursor.moveToNext()) rows += cursor.toJsonObject()
        rows
    }

    /** ID → Sync-Stand je Tabelle, inklusive gelöschter Zeilen. */
    fun readLocalMeta(tables: Collection<String>): Map<String, Map<String, RowMeta>> = tables.associateWith { table ->
        db.query("SELECT id, updated_at, deleted_at FROM `$table`").use { cursor ->
            val meta = HashMap<String, RowMeta>(cursor.count)
            while (cursor.moveToNext()) {
                meta[cursor.getString(0)] = RowMeta(
                    updatedAt = cursor.getLong(1),
                    deletedAt = if (cursor.isNull(2)) null else cursor.getLong(2),
                )
            }
            meta
        }
    }

    /** Spaltennamen einer Tabelle; leer, wenn es die Tabelle nicht gibt. */
    fun columns(table: String): Set<String> = db.query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndexOrThrow("name")
        buildSet { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
    }

    fun <T> inTransaction(block: () -> T): T = database.runInTransaction(Callable { block() })

    fun insert(table: String, values: ContentValues) {
        db.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
    }

    fun update(table: String, id: String, values: ContentValues) {
        db.update(table, SQLiteDatabase.CONFLICT_ABORT, values, "id = ?", arrayOf(id))
    }
}

private fun Cursor.toJsonObject(): JsonObject {
    val map = LinkedHashMap<String, JsonElement>(columnCount)
    for (i in 0 until columnCount) {
        map[getColumnName(i)] = when (getType(i)) {
            Cursor.FIELD_TYPE_NULL -> JsonNull
            Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(getLong(i))
            Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(getDouble(i))
            Cursor.FIELD_TYPE_STRING -> JsonPrimitive(getString(i))
            // Das Schema kennt keine BLOBs; falls doch, lieber auslassen als die Datei unbrauchbar machen.
            else -> JsonNull
        }
    }
    return JsonObject(map)
}

/** Wandelt eine Backup-Zeile in [ContentValues]; nur [allowedColumns] werden übernommen. */
internal fun JsonObject.toContentValues(allowedColumns: Set<String>): ContentValues {
    val values = ContentValues()
    for ((column, element) in this) {
        if (column !in allowedColumns) continue
        val value = element as JsonPrimitive
        if (value is JsonNull) {
            values.putNull(column)
            continue
        }
        if (value.isString) {
            values.put(column, value.content)
            continue
        }
        val bool: Boolean? = value.booleanOrNull
        val long: Long? = value.longOrNull
        val double: Double? = value.doubleOrNull
        when {
            bool != null -> values.put(column, if (bool) 1L else 0L)
            long != null -> values.put(column, long)
            double != null -> values.put(column, double)
            else -> throw BackupException(
                BackupException.Reason.INVALID_FILE,
                "Column '$column' has an unsupported value",
            )
        }
    }
    return values
}
