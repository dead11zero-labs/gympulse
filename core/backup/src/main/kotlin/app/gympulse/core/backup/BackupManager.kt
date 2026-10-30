package app.gympulse.core.backup

import app.gympulse.core.designsystem.theme.AccentColor
import android.content.Context
import android.content.pm.PackageManager
import android.database.SQLException
import android.net.Uri
import android.os.Build
import app.gympulse.core.settings.AppLanguage
import app.gympulse.core.settings.ThemeMode
import app.gympulse.core.settings.UserSettings
import app.gympulse.core.settings.UserSettingsRepository
import app.gympulse.core.settings.WeekStart
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Gelesenes Backup plus lokaler Stand, Grundlage für die Vorschau. */
data class ImportPreview(
    val file: BackupFile,
    val local: Map<String, Map<String, RowMeta>>,
) {
    /** Backup stammt aus einer neueren App-Version; unbekannte Felder werden ignoriert. */
    val isNewerSchema: Boolean get() = file.schemaVersion > BackupFormat.SCHEMA_VERSION
    val hasSettings: Boolean get() = file.settings != null

    fun plan(strategy: ConflictStrategy): ImportPlan = ImportPlanner.plan(file, local, strategy)
}

data class ExportResult(val rows: Int)

data class ImportResult(val inserted: Int, val updated: Int, val skipped: Int, val settingsApplied: Boolean)

/**
 * JSON-Export und -Import aller Nutzerdaten.
 *
 * Der Import läuft in einer einzigen Transaktion: Schlägt eine Zeile fehl, wird alles zurückgerollt
 * und eine [BackupException] geworfen. Einstellungen werden erst nach erfolgreichem Datenbank-Import
 * übernommen.
 */
class BackupManager internal constructor(
    private val store: RoomBackupStore,
    private val settingsRepository: UserSettingsRepository,
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(
        store: RoomBackupStore,
        settingsRepository: UserSettingsRepository,
        @ApplicationContext context: Context,
    ) : this(store, settingsRepository, context, Dispatchers.IO)

    /** Liest alle Nutzerdaten in ein [BackupFile]. */
    suspend fun createBackup(now: Instant = Instant.now()): BackupFile = withContext(ioDispatcher) {
        val tables = LinkedHashMap<String, List<kotlinx.serialization.json.JsonObject>>()
        store.inTransaction {
            BackupTables.all.forEach { spec -> tables[spec.key] = store.readRows(spec) }
        }
        BackupFile(
            schemaVersion = BackupFormat.SCHEMA_VERSION,
            exportedAt = now.truncatedTo(ChronoUnit.SECONDS).toString(),
            appVersion = appVersion(),
            databaseVersion = store.databaseVersion(),
            settings = settingsRepository.settings.first().toBackup(),
            tables = tables,
        )
    }

    /** Schreibt ein Backup in das per Storage Access Framework gewählte Dokument. */
    suspend fun exportTo(uri: Uri): ExportResult {
        val file = createBackup()
        writeText(uri, BackupCodec.encode(file), "backup")
        return ExportResult(rows = file.tables.values.sumOf { it.size })
    }

    /**
     * Schreibt die Trainingshistorie (ein Satz pro Zeile) als CSV in das per Storage Access Framework
     * gewählte Dokument. Die Übungsnamen folgen der App-Sprache.
     */
    suspend fun exportCsvTo(uri: Uri): ExportResult {
        val locale = settingsRepository.settings.first().language.languageTag.substringBefore('-')
            .ifEmpty { java.util.Locale.getDefault().language }
        val rows = withContext(ioDispatcher) { store.inTransaction { store.readWorkoutHistory(locale) } }
        writeText(uri, WorkoutCsv.encode(rows), "CSV")
        return ExportResult(rows = rows.size)
    }

    private suspend fun writeText(uri: Uri, text: String, what: String) {
        withContext(ioDispatcher) {
            try {
                openOutput(uri).bufferedWriter(Charsets.UTF_8).use { it.write(text) }
            } catch (e: IOException) {
                throw BackupException(BackupException.Reason.IO, "Could not write $what", e)
            } catch (e: SecurityException) {
                throw BackupException(BackupException.Reason.IO, "No permission to write $what", e)
            }
        }
    }

    /** Liest ein Backup aus einem Dokument und bereitet die Vorschau vor. */
    suspend fun readFrom(uri: Uri): ImportPreview {
        val text = withContext(ioDispatcher) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw BackupException(BackupException.Reason.IO, "Could not open $uri")
                input.use { stream ->
                    val bytes = stream.readBytesLimited(MAX_FILE_BYTES)
                    String(bytes, Charsets.UTF_8)
                }
            } catch (e: IOException) {
                throw BackupException(BackupException.Reason.IO, "Could not read backup", e)
            } catch (e: SecurityException) {
                throw BackupException(BackupException.Reason.IO, "No permission to read backup", e)
            }
        }
        return preview(BackupCodec.decode(text))
    }

    suspend fun preview(file: BackupFile): ImportPreview = withContext(ioDispatcher) {
        ImportPreview(file, store.readLocalMeta(BackupTables.targetTables))
    }

    /**
     * Importiert [file] in einer Transaktion. Der Plan wird innerhalb der Transaktion neu berechnet,
     * damit zwischen Vorschau und Import geänderte Daten berücksichtigt werden.
     */
    suspend fun import(file: BackupFile, strategy: ConflictStrategy, applySettings: Boolean): ImportResult {
        val plan = withContext(ioDispatcher) {
            try {
                store.inTransaction { writePlan(file, strategy) }
            } catch (e: BackupException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: SQLException) {
                throw BackupException(BackupException.Reason.WRITE_FAILED, e.message ?: "Database error", e)
            } catch (e: RuntimeException) {
                throw BackupException(BackupException.Reason.WRITE_FAILED, e.message ?: "Import failed", e)
            }
        }
        val settings = file.settings
        val settingsApplied = applySettings && settings != null
        if (settingsApplied) applySettings(requireNotNull(settings))
        return ImportResult(plan.inserts, plan.updates, plan.skipped, settingsApplied)
    }

    private fun writePlan(file: BackupFile, strategy: ConflictStrategy): ImportPlan {
        val plan = ImportPlanner.plan(file, store.readLocalMeta(BackupTables.targetTables), strategy)
        if (plan.missingReferences > 0) {
            throw BackupException(
                BackupException.Reason.MISSING_REFERENCES,
                "${plan.missingReferences} rows reference data that is neither in the backup nor on this device",
            )
        }
        val columnCache = HashMap<String, Set<String>>()
        plan.tables.forEach { tablePlan ->
            val table = tablePlan.spec.table
            val columns = columnCache.getOrPut(table) { store.columns(table) }
            if (columns.isEmpty()) return@forEach
            val allowed = tablePlan.spec.importColumns?.intersect(columns) ?: columns
            tablePlan.rows.forEach { row ->
                when (row.action) {
                    RowAction.INSERT -> store.insert(table, row.row.toContentValues(allowed))
                    RowAction.UPDATE -> {
                        val values = row.row.toContentValues(allowed - "id")
                        if (values.size() > 0) store.update(table, row.id, values)
                    }
                    RowAction.SKIP -> Unit
                }
            }
        }
        return plan
    }

    private suspend fun applySettings(settings: BackupSettings) {
        settings.restTimerSeconds?.let { settingsRepository.setRestTimerSeconds(it) }
        settings.weekStart.toEnum<WeekStart>()?.let { settingsRepository.setWeekStart(it) }
        settings.weeklyGoal?.let { settingsRepository.setWeeklyGoal(it) }
        settings.themeMode.toEnum<ThemeMode>()?.let { settingsRepository.setThemeMode(it) }
        settings.dynamicColor?.let { settingsRepository.setDynamicColor(it) }
        settings.accentColor.toEnum<AccentColor>()?.let { settingsRepository.setAccentColor(it) }
        settings.language.toEnum<AppLanguage>()?.let { settingsRepository.setLanguage(it) }
    }

    private fun openOutput(uri: Uri): OutputStream {
        val resolver = context.contentResolver
        // "wt" kürzt eine vorhandene Datei; nicht jeder Anbieter kennt den Modus.
        val stream = try {
            resolver.openOutputStream(uri, "wt")
        } catch (_: FileNotFoundException) {
            resolver.openOutputStream(uri, "w")
        } catch (_: IllegalArgumentException) {
            resolver.openOutputStream(uri, "w")
        }
        return stream ?: throw IOException("Could not open $uri")
    }

    private fun appVersion(): String = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName ?: UNKNOWN_VERSION
    } catch (_: PackageManager.NameNotFoundException) {
        UNKNOWN_VERSION
    }

    companion object {
        /** Schutz vor versehentlich gewählten Riesendateien. */
        const val MAX_FILE_BYTES: Long = 64L * 1024 * 1024
        private const val UNKNOWN_VERSION = "unknown"

        /** Vorgeschlagener Dateiname für den Export, z. B. "gympulse-backup-2026-10-20.json". */
        fun suggestedFileName(date: java.time.LocalDate = java.time.LocalDate.now()): String =
            "gympulse-backup-$date.json"

        /** Vorgeschlagener Dateiname für den CSV-Export, z. B. "gympulse-history-2026-10-20.csv". */
        fun suggestedCsvFileName(date: java.time.LocalDate = java.time.LocalDate.now()): String =
            "gympulse-history-$date.csv"
    }
}

internal fun UserSettings.toBackup() = BackupSettings(
    restTimerSeconds = restTimerSeconds,
    weekStart = weekStart.name,
    weeklyGoal = weeklyGoal,
    themeMode = themeMode.name,
    dynamicColor = dynamicColor,
    accentColor = accentColor.name,
    language = language.name,
)

private inline fun <reified E : Enum<E>> String?.toEnum(): E? = enumValues<E>().firstOrNull { it.name == this }

private fun java.io.InputStream.readBytesLimited(limit: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) {
            throw BackupException(BackupException.Reason.INVALID_FILE, "File is larger than $limit bytes")
        }
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
