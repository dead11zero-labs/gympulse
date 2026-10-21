package app.gympulse.core.data.cloud

import kotlinx.coroutines.flow.Flow

/*
 * Schnittstellen zum Cloud-Backup. Die Android-Implementierungen (Supabase, Credential Manager,
 * JSON-Backup) liegen in `core:cloud`; hier stehen nur die Verträge, damit Logik und Tests ohne
 * Android-Laufzeit auskommen.
 */

/** Anmeldung beim Cloud-Backend. */
interface CloudAuth {
    val configuration: CloudConfiguration

    /** Aktuelles Konto; null, solange niemand angemeldet ist. */
    fun observeUser(): Flow<CloudUser?>

    /** Wartet, bis eine gespeicherte Sitzung geladen ist, und liefert das Konto oder null. */
    suspend fun currentUser(): CloudUser?

    suspend fun signInWithGoogle(token: GoogleIdToken)

    suspend fun signOut()
}

/** Tabelle `backups` mit einer Zeile pro Nutzer. */
interface CloudBackupRemote {
    suspend fun upsert(row: CloudBackupRow)

    suspend fun fetch(userId: String): CloudBackupRow?

    suspend fun delete(userId: String)
}

/** Lokales JSON-Backup (siehe `core:backup`). */
interface LocalBackupSource {
    /** Erstellt ein vollständiges Backup als JSON-Text. */
    suspend fun exportJson(): String

    /** Spielt ein Backup über den normalen Import-Pfad ein. */
    suspend fun restoreJson(json: String): RestoreSummary
}
