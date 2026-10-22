package app.gympulse.core.wearsync

import app.gympulse.core.wearprotocol.WearCapabilities
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Stand der Uhr-App aus Sicht des Telefons. */
data class WatchAppState(
    /** Auf mindestens einer gekoppelten Uhr ist die GymPulse-App installiert. */
    val installed: Boolean,
    /** Eine dieser Uhren ist gerade direkt (Bluetooth/WLAN) verbunden. */
    val connected: Boolean,
) {
    companion object {
        val NONE = WatchAppState(installed = false, connected = false)
    }
}

/** Capability-Prüfung „Uhr-App installiert und verbunden?“. */
interface WatchAppStatus {
    /** Aktueller Stand, danach jede Änderung. */
    val state: Flow<WatchAppState>
}

/** [WatchAppStatus] über die Capability [WearCapabilities.WATCH_APP] der Uhr-App. */
@Singleton
class CapabilityWatchAppStatus @Inject constructor(
    private val capabilityClient: CapabilityClient,
) : WatchAppStatus {

    override val state: Flow<WatchAppState> = callbackFlow {
        suspend fun refresh() {
            trySend(query())
        }
        // Meldet Änderungen der erreichbaren Knoten; installiert/verbunden wird dann neu abgefragt.
        val listener = CapabilityClient.OnCapabilityChangedListener { launch { refresh() } }
        capabilityClient.addListener(listener, WearCapabilities.WATCH_APP)
        refresh()
        awaitClose { capabilityClient.removeListener(listener, WearCapabilities.WATCH_APP) }
    }.distinctUntilChanged()

    private suspend fun query(): WatchAppState = try {
        val all = nodes(CapabilityClient.FILTER_ALL)
        val reachable = nodes(CapabilityClient.FILTER_REACHABLE)
        WatchAppState(
            installed = all.isNotEmpty() || reachable.isNotEmpty(),
            connected = reachable.any { it.isNearby },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Keine Play Services / keine Wear-API: so tun, als gäbe es keine Uhr.
        WatchAppState.NONE
    }

    private suspend fun nodes(filter: Int): Set<Node> =
        capabilityClient.getCapability(WearCapabilities.WATCH_APP, filter).await().nodes
}
