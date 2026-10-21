package app.gympulse.core.data.cloud

import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.featuregate.UserFeatureToggles
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Manuelles Cloud-Backup: lädt das lokale JSON-Backup als eine Zeile pro Nutzer hoch und spielt es
 * auf Wunsch über den normalen Import-Pfad wieder ein. Alles setzt aktives [Feature.CLOUD_BACKUP]
 * und ein eingerichtetes Backend voraus.
 */
class CloudBackupSync @Inject constructor(
    private val auth: CloudAuth,
    private val remote: CloudBackupRemote,
    private val local: LocalBackupSource,
    private val featureGate: FeatureGate,
    private val userToggles: UserFeatureToggles,
    private val time: TimeProvider,
) {
    val configuration: CloudConfiguration get() = auth.configuration

    fun observeEnabled(): Flow<Boolean> = featureGate.observe(Feature.CLOUD_BACKUP)

    fun setEnabled(enabled: Boolean) = userToggles.setEnabled(Feature.CLOUD_BACKUP, enabled)

    fun observeUser(): Flow<CloudUser?> = auth.observeUser()

    suspend fun signIn(requester: GoogleIdTokenRequester) {
        requireReady()
        if (!configuration.googleSignInConfigured) throw CloudException(CloudException.Reason.NOT_CONFIGURED)
        auth.signInWithGoogle(requester.request())
    }

    suspend fun signOut() = auth.signOut()

    /** Stand der Sicherung in der Cloud oder null, wenn es noch keine gibt. */
    suspend fun latest(): CloudBackupInfo? {
        val user = requireUser()
        return remote.fetch(user.id)?.let(CloudBackupPayload::info)
    }

    suspend fun backupNow(): CloudBackupInfo {
        val user = requireUser()
        val row = CloudBackupPayload.toRow(user.id, local.exportJson(), time.now())
        remote.upsert(row)
        return CloudBackupPayload.info(row)
    }

    suspend fun restore(): RestoreSummary {
        val user = requireUser()
        val row = remote.fetch(user.id) ?: throw CloudException(CloudException.Reason.NO_BACKUP)
        return local.restoreJson(CloudBackupPayload.toBackupJson(row))
    }

    suspend fun deleteRemote() {
        val user = requireUser()
        remote.delete(user.id)
    }

    private fun requireReady() {
        if (!configuration.backendConfigured || !featureGate.isEnabled(Feature.CLOUD_BACKUP)) {
            throw CloudException(CloudException.Reason.NOT_CONFIGURED)
        }
    }

    private suspend fun requireUser(): CloudUser {
        requireReady()
        return auth.currentUser() ?: throw CloudException(CloudException.Reason.NOT_SIGNED_IN)
    }
}
