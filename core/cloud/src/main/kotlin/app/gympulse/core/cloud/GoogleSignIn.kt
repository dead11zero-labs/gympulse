package app.gympulse.core.cloud

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import app.gympulse.core.data.cloud.CloudException
import app.gympulse.core.data.cloud.GoogleIdToken
import app.gympulse.core.data.cloud.GoogleIdTokenRequester
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.MessageDigest
import java.util.UUID

/**
 * "Mit Google anmelden" über den Credential Manager. Supabase prüft das ID-Token gegen die
 * Web-Client-ID und den Nonce: im Token steckt der SHA-256-Hash, Supabase bekommt den Rohwert.
 */
object GoogleSignIn {

    /** [activityContext] muss eine Activity sein, weil der Credential Manager eine Auswahl anzeigt. */
    fun requester(activityContext: Context): GoogleIdTokenRequester =
        GoogleIdTokenRequester { requestIdToken(activityContext) }

    private suspend fun requestIdToken(context: Context): GoogleIdToken {
        val clientId = CloudBuildConfig.googleWebClientId
        if (clientId.isBlank()) throw CloudException(CloudException.Reason.NOT_CONFIGURED, "No Google web client ID")

        val rawNonce = UUID.randomUUID().toString()
        val option = GetSignInWithGoogleOption.Builder(clientId)
            .setNonce(sha256(rawNonce))
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val response = try {
            CredentialManager.create(context).getCredential(context, request)
        } catch (e: GetCredentialCancellationException) {
            throw CloudException(CloudException.Reason.CANCELLED, e.message, e)
        } catch (e: NoCredentialException) {
            throw CloudException(CloudException.Reason.NO_GOOGLE_ACCOUNT, e.message, e)
        } catch (e: GetCredentialException) {
            throw CloudException(CloudException.Reason.SIGN_IN_FAILED, e.message, e)
        }

        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw CloudException(CloudException.Reason.SIGN_IN_FAILED, "Unexpected credential type")
        }
        val idToken = try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            throw CloudException(CloudException.Reason.SIGN_IN_FAILED, e.message, e)
        }
        return GoogleIdToken(idToken = idToken, rawNonce = rawNonce)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
}
