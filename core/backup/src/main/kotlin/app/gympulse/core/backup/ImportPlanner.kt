package app.gympulse.core.backup

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Sync-Stand einer Zeile. */
data class RowMeta(val updatedAt: Long, val deletedAt: Long?) {
    val isDeleted: Boolean get() = deletedAt != null
}

/** Wie mit Zeilen umgegangen wird, deren UUID es lokal schon gibt. */
enum class ConflictStrategy {
    /** Die Zeile mit dem neueren `updated_at` gewinnt (Standard). */
    NEWER_WINS,

    /** Vorhandene Zeilen bleiben unverändert, nur neue kommen dazu. */
    KEEP_EXISTING,

    /** Zeilen aus dem Backup ersetzen vorhandene. */
    OVERWRITE,
}

enum class RowAction { INSERT, UPDATE, SKIP }

/**
 * Konfliktregel pro Zeile, unabhängig von der Tabelle.
 *
 * Gelöschte Zeilen bleiben gelöscht: Ist die lokale Zeile gelöscht, wird sie nie wiederbelebt; ist die
 * Zeile im Backup gelöscht, setzt sich die Löschung durch (außer bei [ConflictStrategy.KEEP_EXISTING]).
 */
object ConflictResolver {
    fun resolve(
        incoming: RowMeta,
        local: RowMeta?,
        strategy: ConflictStrategy,
        insertAllowed: Boolean = true,
    ): RowAction = when {
        local == null -> if (insertAllowed) RowAction.INSERT else RowAction.SKIP
        local.isDeleted -> RowAction.SKIP
        strategy == ConflictStrategy.KEEP_EXISTING -> RowAction.SKIP
        incoming.isDeleted -> RowAction.UPDATE
        strategy == ConflictStrategy.OVERWRITE -> RowAction.UPDATE
        incoming.updatedAt > local.updatedAt -> RowAction.UPDATE
        else -> RowAction.SKIP
    }
}

data class PlannedRow(val id: String, val action: RowAction, val row: JsonObject)

data class TablePlan(
    val spec: TableSpec,
    val rows: List<PlannedRow>,
    /** Zeilen, die geschrieben würden, deren Fremdschlüssel aber ins Leere zeigen. */
    val missingReferences: Int,
) {
    val total: Int get() = rows.size
    val inserts: Int get() = rows.count { it.action == RowAction.INSERT }
    val updates: Int get() = rows.count { it.action == RowAction.UPDATE }
    val skipped: Int get() = rows.count { it.action == RowAction.SKIP }
}

data class ImportPlan(
    val tables: List<TablePlan>,
    /** Abschnitte im Backup, die diese App-Version nicht kennt (z. B. aus einer neueren Version). */
    val unknownTables: List<String>,
) {
    val inserts: Int get() = tables.sumOf { it.inserts }
    val updates: Int get() = tables.sumOf { it.updates }
    val skipped: Int get() = tables.sumOf { it.skipped }
    val missingReferences: Int get() = tables.sumOf { it.missingReferences }
    val changes: Int get() = inserts + updates
}

/**
 * Plant einen Import: welche Zeile eingefügt, aktualisiert oder übersprungen wird. Rein funktional,
 * damit Vorschau und Import dieselbe Entscheidung treffen und sich das ohne Datenbank testen lässt.
 *
 * @param local lokaler Stand je Zieltabelle: ID → [RowMeta].
 */
object ImportPlanner {
    fun plan(
        file: BackupFile,
        local: Map<String, Map<String, RowMeta>>,
        strategy: ConflictStrategy,
        specs: List<TableSpec> = BackupTables.all,
    ): ImportPlan {
        val knownKeys = specs.mapTo(HashSet()) { it.key }

        val planned = specs.map { spec ->
            val localRows = local[spec.table].orEmpty()
            val rows = file.tables[spec.key].orEmpty()
                .let { if (spec.singleton) remapSingleton(it, localRows) else it }
                .let(::latestPerId)
                .map { row ->
                    val id = requireNotNull(row.idOrNull())
                    PlannedRow(id, ConflictResolver.resolve(row.meta(), localRows[id], strategy, spec.insertAllowed), row)
                }
            spec to rows
        }

        // IDs, die nach dem Import in jeder Tabelle vorhanden sind (lokal oder neu eingefügt).
        val available = HashMap<String, MutableSet<String>>()
        local.forEach { (table, rows) -> available.getOrPut(table) { HashSet() }.addAll(rows.keys) }
        planned.forEach { (spec, rows) ->
            rows.filter { it.action == RowAction.INSERT }
                .forEach { available.getOrPut(spec.table) { HashSet() }.add(it.id) }
        }

        val tables = planned.map { (spec, rows) ->
            val missing = rows.count { candidate ->
                candidate.action != RowAction.SKIP && spec.references.any { ref ->
                    val target = candidate.row.stringOrNull(ref.column)
                    target != null && available[ref.parentTable]?.contains(target) != true
                }
            }
            TablePlan(spec, rows, missing)
        }
        val unknown = file.tables.filter { (key, rows) -> key !in knownKeys && rows.isNotEmpty() }.keys.toList()
        return ImportPlan(tables, unknown)
    }

    /** Doppelte IDs in einer Datei: die Zeile mit dem neuesten `updated_at` zählt. */
    private fun latestPerId(rows: List<JsonObject>): List<JsonObject> {
        if (rows.size == rows.distinctBy { it.idOrNull() }.size) return rows
        val latest = LinkedHashMap<String, JsonObject>()
        rows.forEach { row ->
            val id = requireNotNull(row.idOrNull())
            val current = latest[id]
            if (current == null || row.meta().updatedAt >= current.meta().updatedAt) latest[id] = row
        }
        return latest.values.toList()
    }

    /** Eine aktive Profilzeile im Backup übernimmt die ID der aktiven lokalen Zeile. */
    private fun remapSingleton(rows: List<JsonObject>, local: Map<String, RowMeta>): List<JsonObject> {
        val localActiveId = local.entries.firstOrNull { !it.value.isDeleted }?.key ?: return rows
        if (rows.any { it.idOrNull() == localActiveId }) return rows
        var remapped = false
        return rows.map { row ->
            if (!remapped && !row.meta().isDeleted) {
                remapped = true
                JsonObject(row + ("id" to JsonPrimitive(localActiveId)))
            } else {
                row
            }
        }
    }
}
