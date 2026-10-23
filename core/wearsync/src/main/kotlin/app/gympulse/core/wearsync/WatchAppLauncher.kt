package app.gympulse.core.wearsync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.wear.remote.interactions.RemoteActivityHelper
import app.gympulse.core.wearprotocol.WearCapabilities
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.NodeClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Ergebnis einer Aktion, die etwas auf der Uhr öffnet. */
enum class WatchLaunchResult { STARTED, NO_WATCH, FAILED }

/** Wie die Uhr-App verteilt wird. */
object WearDistribution {
    /**
     * Gibt es einen Play-Store-Eintrag für GymPulse? Erst dann hat "Auf der Uhr installieren" (Play-Seite
     * auf der Uhr) ein Ziel; bis dahin Sideload aus dem Release [RELEASE_PAGE_URL].
     */
    const val PLAY_LISTING_AVAILABLE = false

    const val RELEASE_PAGE_URL = "https://github.com/realgroundzero-gif/gympulse/releases/tag/latest-main"
}

/** Qualifier für das Flag, ob es einen Play-Eintrag gibt ([WearDistribution.PLAY_LISTING_AVAILABLE]). */
@javax.inject.Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class WearPlayListing

/** Öffnet Seiten und die App auf der gekoppelten Uhr (RemoteActivityHelper). */
interface WatchAppLauncher {
    /** Öffnet auf Uhren ohne GymPulse die Play-Store-Seite der App zur Installation. */
    suspend fun openInstallPageOnWatch(): WatchLaunchResult

    /** Öffnet die GymPulse-App auf Uhren, die sie installiert haben. */
    suspend fun openAppOnWatch(): WatchLaunchResult
}

@Singleton
class RemoteWatchAppLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nodeClient: NodeClient,
    private val capabilityClient: CapabilityClient,
) : WatchAppLauncher {

    override suspend fun openInstallPageOnWatch(): WatchLaunchResult =
        launch(withApp = false, uri = PLAY_URI + context.packageName)

    override suspend fun openAppOnWatch(): WatchLaunchResult = launch(withApp = true, uri = OPEN_URI)

    private suspend fun launch(withApp: Boolean, uri: String): WatchLaunchResult = try {
        val connected = nodeClient.connectedNodes.await()
        val installed = capabilityClient
            .getCapability(WearCapabilities.WATCH_APP, CapabilityClient.FILTER_ALL).await().nodes
            .map { it.id }.toSet()
        val targets = connected.filter { (it.id in installed) == withApp }
        if (targets.isEmpty()) {
            WatchLaunchResult.NO_WATCH
        } else {
            val helper = RemoteActivityHelper(context)
            val intent = Intent(Intent.ACTION_VIEW).addCategory(Intent.CATEGORY_BROWSABLE).setData(Uri.parse(uri))
            targets.forEach { helper.startRemoteActivity(intent, it.id).await() }
            WatchLaunchResult.STARTED
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        WatchLaunchResult.FAILED
    }

    private companion object {
        const val PLAY_URI = "market://details?id="
        const val OPEN_URI = "gympulse://open"
    }
}
