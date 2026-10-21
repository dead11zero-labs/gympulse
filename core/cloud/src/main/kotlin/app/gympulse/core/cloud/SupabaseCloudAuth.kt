package app.gympulse.core.cloud

import app.gympulse.core.data.cloud.CloudAuth
import app.gympulse.core.data.cloud.CloudConfiguration
import app.gympulse.core.data.cloud.CloudException
import app.gympulse.core.data.cloud.CloudUser
import app.gympulse.core.data.cloud.GoogleIdToken
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Anmeldung bei Supabase Auth mit einem Google-ID-Token (Credential Manager). */
class SupabaseCloudAuth @Inject constructor(
    private val supabase: SupabaseClientProvider,
) : CloudAuth {

    override val configuration: CloudConfiguration get() = supabase.configuration

    override fun observeUser(): Flow<CloudUser?> {
        if (!configuration.backendConfigured) return flowOf(null)
        return flow {
            val auth = supabase.client.auth
            auth.awaitInitialization()
            emitAll(
                auth.sessionStatus.map { status ->
                    (status as? SessionStatus.Authenticated)?.session?.user?.toCloudUser()
                },
            )
        }.distinctUntilChanged()
    }

    override suspend fun currentUser(): CloudUser? {
        if (!configuration.backendConfigured) return null
        val auth = supabase.client.auth
        auth.awaitInitialization()
        return auth.currentUserOrNull()?.toCloudUser()
    }

    override suspend fun signInWithGoogle(token: GoogleIdToken) {
        cloudCall(CloudException.Reason.SIGN_IN_FAILED) {
            supabase.client.auth.signInWith(IDToken) {
                idToken = token.idToken
                provider = Google
                nonce = token.rawNonce
            }
        }
    }

    override suspend fun signOut() {
        if (!configuration.backendConfigured) return
        cloudCall(CloudException.Reason.NETWORK) { supabase.client.auth.signOut() }
    }

    private fun UserInfo.toCloudUser() = CloudUser(id = id, email = email)
}
