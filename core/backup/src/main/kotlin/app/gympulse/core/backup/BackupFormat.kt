package app.gympulse.core.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * GymPulse-Backup im JSON-Format.
 *
 * Tabellenzeilen werden spaltengetreu abgelegt (Spaltenname wie in der Room-Datenbank, Werte als
 * JSON-Primitive). So bleibt das Format nah am Datenbankschema und am späteren Server-Sync, und
 * ein Import kann unbekannte Spalten aus neueren Exporten einfach ignorieren.
 *
 * [schemaVersion] ist die Version dieses Backup-Formats, nicht die Datenbankversion; sie steigt nur,
 * wenn sich die Struktur so ändert, dass ein älterer Import sie falsch verstehen würde.
 */
@Serializable
data class BackupFile(
    @SerialName("format") val format: String = BackupFormat.FORMAT_ID,
    @SerialName("schema_version") val schemaVersion: Int,
    /** Zeitpunkt des Exports als ISO-8601 (UTC), z. B. "2026-10-20T08:15:30Z". */
    @SerialName("exported_at") val exportedAt: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("database_version") val databaseVersion: Int? = null,
    @SerialName("settings") val settings: BackupSettings? = null,
    /** Zeilen je Tabellenschlüssel aus [BackupTables]. */
    @SerialName("tables") val tables: Map<String, List<JsonObject>> = emptyMap(),
)

/** App-Einstellungen; Enum-Werte als Name, unbekannte Werte werden beim Import übergangen. */
@Serializable
data class BackupSettings(
    @SerialName("rest_timer_seconds") val restTimerSeconds: Int? = null,
    @SerialName("week_start") val weekStart: String? = null,
    @SerialName("weekly_goal") val weeklyGoal: Int? = null,
    @SerialName("theme_mode") val themeMode: String? = null,
    @SerialName("dynamic_color") val dynamicColor: Boolean? = null,
    @SerialName("accent_color") val accentColor: String? = null,
    @SerialName("language") val language: String? = null,
)

object BackupFormat {
    const val FORMAT_ID = "gympulse-backup"

    /** Aktuelle Version des Backup-Formats. */
    const val SCHEMA_VERSION = 1

    const val MIME_TYPE = "application/json"
}
