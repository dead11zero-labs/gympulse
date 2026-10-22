package app.gympulse.core.data.cloud

import app.gympulse.core.settings.IntegrationSettings
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Ob das Gerät gerade Netz hat. Die Android-Implementierung liegt in `core:cloud`. */
fun interface NetworkStatus {
    fun isOnline(): Boolean
}

/** Ergebnis des automatischen Backups beim App-Start. */
enum class AutoBackupResult {
    /** Das Backup wurde hochgeladen. */
    UPLOADED,

    /** Schon in diesem App-Start ausgeführt oder versucht. */
    ALREADY_RAN,

    /** Automatisches Backup ist aus. */
    DISABLED,

    /** Cloud-Backup ist nicht eingerichtet oder vom Nutzer nicht aktiviert. */
    FEATURE_OFF,

    /** Niemand ist angemeldet. */
    NOT_SIGNED_IN,

    /** Kein Netz. */
    OFFLINE,

    /** Das Hochladen ist fehlgeschlagen; der Fehler wird bewusst verschluckt. */
    FAILED,
}

/**
 * Stößt beim App-Start höchstens einmal ein Cloud-Backup an: nur mit eingeschaltetem Schalter
 * "Automatisches Backup", aktivem Cloud-Backup, angemeldetem Konto und Netz. Fehler stören den
 * Nutzer nie; es gibt keine Meldung und keinen Wiederholungsversuch im selben App-Start.
 */
@Singleton
class AutoCloudBackup @Inject constructor(
    private val sync: CloudBackupSync,
    private val settings: IntegrationSettings,
    private val network: NetworkStatus,
) {
    private val started = AtomicBoolean(false)

    suspend fun runOnce(): AutoBackupResult {
        if (!started.compareAndSet(false, true)) return AutoBackupResult.ALREADY_RAN
        if (!settings.autoCloudBackup.first()) return AutoBackupResult.DISABLED
        if (!sync.configuration.backendConfigured || !sync.observeEnabled().first()) {
            return AutoBackupResult.FEATURE_OFF
        }
        if (!network.isOnline()) return AutoBackupResult.OFFLINE
        return try {
            sync.backupNow()
            AutoBackupResult.UPLOADED
        } catch (e: CancellationException) {
            throw e
        } catch (e: CloudException) {
            if (e.reason == CloudException.Reason.NOT_SIGNED_IN) AutoBackupResult.NOT_SIGNED_IN else AutoBackupResult.FAILED
        } catch (e: Exception) {
            AutoBackupResult.FAILED
        }
    }
}
