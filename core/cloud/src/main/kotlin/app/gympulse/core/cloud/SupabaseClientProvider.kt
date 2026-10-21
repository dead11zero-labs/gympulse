package app.gympulse.core.cloud

import app.gympulse.core.data.cloud.CloudConfiguration
import app.gympulse.core.data.cloud.CloudException
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Inject
import javax.inject.Singleton

/** Werte aus BuildConfig (gradle.properties); siehe `core/cloud/build.gradle.kts`. */
internal object CloudBuildConfig {
    val supabaseUrl: String get() = BuildConfig.SUPABASE_URL
    val supabaseKey: String get() = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    val googleWebClientId: String get() = BuildConfig.GOOGLE_WEB_CLIENT_ID

    val configuration: CloudConfiguration
        get() = CloudConfiguration(
            backendConfigured = supabaseUrl.isNotBlank() && supabaseKey.isNotBlank(),
            googleSignInConfigured = googleWebClientId.isNotBlank(),
        )
}

/**
 * Erzeugt den Supabase-Client erst bei der ersten Nutzung, also nur, wenn der Nutzer das
 * Cloud-Backup eingeschaltet hat. Die Sitzung speichert supabase-kt selbst auf dem Gerät.
 */
@Singleton
class SupabaseClientProvider @Inject constructor() {

    val configuration: CloudConfiguration = CloudBuildConfig.configuration

    private val lazyClient: Lazy<SupabaseClient> = lazy {
        createSupabaseClient(
            supabaseUrl = CloudBuildConfig.supabaseUrl,
            supabaseKey = CloudBuildConfig.supabaseKey,
        ) {
            install(Auth)
            install(Postgrest)
        }
    }

    val client: SupabaseClient
        get() {
            if (!configuration.backendConfigured) throw CloudException(CloudException.Reason.NOT_CONFIGURED)
            return lazyClient.value
        }
}
