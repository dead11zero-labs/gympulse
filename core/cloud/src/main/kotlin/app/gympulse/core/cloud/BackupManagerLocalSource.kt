package app.gympulse.core.cloud

import app.gympulse.core.backup.BackupCodec
import app.gympulse.core.backup.BackupException
import app.gympulse.core.backup.BackupManager
import app.gympulse.core.backup.ConflictStrategy
import app.gympulse.core.data.cloud.CloudException
import app.gympulse.core.data.cloud.LocalBackupSource
import app.gympulse.core.data.cloud.RestoreSummary
import javax.inject.Inject

/**
 * Brücke zum JSON-Backup aus `core:backup`. Wiederherstellen nutzt denselben Import wie die
 * Datei-Funktion: eine Transaktion, neuere Stände gewinnen, Einstellungen werden übernommen.
 */
class BackupManagerLocalSource @Inject constructor(
    private val backupManager: BackupManager,
) : LocalBackupSource {

    override suspend fun exportJson(): String = BackupCodec.encode(backupManager.createBackup())

    override suspend fun restoreJson(json: String): RestoreSummary {
        try {
            val file = BackupCodec.decode(json)
            val result = backupManager.import(file, ConflictStrategy.NEWER_WINS, applySettings = true)
            return RestoreSummary(result.inserted, result.updated, result.skipped)
        } catch (e: BackupException) {
            val reason = when (e.reason) {
                BackupException.Reason.INVALID_FILE -> CloudException.Reason.INVALID_BACKUP
                else -> CloudException.Reason.RESTORE_FAILED
            }
            throw CloudException(reason, e.message, e)
        }
    }
}
