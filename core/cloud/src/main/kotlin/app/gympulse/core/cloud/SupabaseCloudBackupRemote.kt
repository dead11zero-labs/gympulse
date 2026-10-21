package app.gympulse.core.cloud

import app.gympulse.core.data.cloud.CloudBackupPayload
import app.gympulse.core.data.cloud.CloudBackupRemote
import app.gympulse.core.data.cloud.CloudBackupRow
import app.gympulse.core.data.cloud.CloudException
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject

/**
 * Tabelle `backups` über PostgREST. Row Level Security sorgt dafür, dass jedes Konto nur seine
 * eigene Zeile sieht und ändern kann; der Filter auf `user_id` ist zusätzlich explizit.
 */
class SupabaseCloudBackupRemote @Inject constructor(
    private val supabase: SupabaseClientProvider,
) : CloudBackupRemote {

    override suspend fun upsert(row: CloudBackupRow) {
        cloudCall(CloudException.Reason.NETWORK) {
            supabase.client.from(CloudBackupPayload.TABLE).upsert(row) {
                onConflict = USER_ID
            }
        }
    }

    override suspend fun fetch(userId: String): CloudBackupRow? = cloudCall(CloudException.Reason.NETWORK) {
        supabase.client.from(CloudBackupPayload.TABLE)
            .select {
                filter { eq(USER_ID, userId) }
            }
            .decodeList<CloudBackupRow>()
            .firstOrNull()
    }

    override suspend fun delete(userId: String) {
        cloudCall(CloudException.Reason.NETWORK) {
            supabase.client.from(CloudBackupPayload.TABLE).delete {
                filter { eq(USER_ID, userId) }
            }
        }
    }

    private companion object {
        const val USER_ID = "user_id"
    }
}
