package app.gympulse.core.database.entity

/**
 * Jede Tabelle trägt dieselben Sync-Spalten:
 * - `id`: UUID v7 als Text (siehe [app.gympulse.core.model.Ids])
 * - `created_at` / `updated_at`: Unix-Millis, `updated_at` wird bei jeder Änderung gesetzt
 * - `deleted_at`: Soft-Delete; gelöschte Zeilen bleiben für den Sync erhalten
 *
 * Abfragen für die UI filtern deshalb immer auf `deleted_at IS NULL`.
 */
interface SyncEntity {
    val id: String
    val createdAt: Long
    val updatedAt: Long
    val deletedAt: Long?
}
