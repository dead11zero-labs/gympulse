package app.gympulse.core.cloud

import app.gympulse.core.data.cloud.CloudException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Führt einen Aufruf gegen Supabase aus und übersetzt Fehler (HTTP, Netzwerk, Serialisierung)
 * in eine [CloudException] mit [reason]. Abbrüche der Coroutine bleiben unverändert.
 */
internal suspend fun <T> cloudCall(reason: CloudException.Reason, block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: CloudException) {
    throw e
} catch (e: Exception) {
    throw CloudException(reason, e.message, e)
}
