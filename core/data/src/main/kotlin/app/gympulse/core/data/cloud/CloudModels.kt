package app.gympulse.core.data.cloud

import java.time.Instant

/** Angemeldetes Konto beim Cloud-Backend. */
data class CloudUser(val id: String, val email: String?, val avatarUrl: String? = null)

/**
 * Was dieser Build mitbringt: [backendConfigured] heißt, Supabase-URL und Publishable Key sind
 * gesetzt; [googleSignInConfigured] heißt, eine Google-Web-Client-ID ist hinterlegt.
 */
data class CloudConfiguration(
    val backendConfigured: Boolean,
    val googleSignInConfigured: Boolean,
) {
    val signInAvailable: Boolean get() = backendConfigured && googleSignInConfigured

    companion object {
        val NONE = CloudConfiguration(backendConfigured = false, googleSignInConfigured = false)
    }
}

/** Google-ID-Token plus der unverschlüsselte Nonce, dessen SHA-256 im Token steckt. */
data class GoogleIdToken(val idToken: String, val rawNonce: String)

/**
 * Holt ein Google-ID-Token, in der App über den Credential Manager. Braucht einen Activity-Kontext,
 * deshalb erzeugt ihn die Oberfläche und reicht ihn an die Logik weiter.
 */
fun interface GoogleIdTokenRequester {
    suspend fun request(): GoogleIdToken
}

/** Stand der Sicherung in der Cloud. */
data class CloudBackupInfo(
    /** Zeitpunkt des letzten Hochladens laut Server. */
    val updatedAt: Instant?,
    /** Zeitpunkt, zu dem das Backup auf dem Gerät erstellt wurde. */
    val exportedAt: Instant?,
    val appVersion: String?,
)

data class RestoreSummary(val inserted: Int, val updated: Int, val skipped: Int)

/** Fehler der Cloud-Funktionen mit einem für die Oberfläche auswertbaren [reason]. */
class CloudException(
    val reason: Reason,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: reason.name, cause) {
    enum class Reason {
        /** Backend oder Google-Anmeldung in diesem Build nicht eingerichtet, oder Feature aus. */
        NOT_CONFIGURED,

        /** Der Nutzer hat die Anmeldung abgebrochen; keine Meldung nötig. */
        CANCELLED,
        NO_GOOGLE_ACCOUNT,
        SIGN_IN_FAILED,
        NOT_SIGNED_IN,
        NETWORK,
        NO_BACKUP,
        INVALID_BACKUP,
        RESTORE_FAILED,
        UNKNOWN,
    }
}
