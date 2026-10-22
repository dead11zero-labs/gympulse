package app.gympulse.wear.data

import android.net.Uri
import app.gympulse.core.wearprotocol.PlanSummaryList
import app.gympulse.core.wearprotocol.WearCommand
import app.gympulse.core.wearprotocol.WearPaths
import app.gympulse.core.wearprotocol.WearProtocol
import app.gympulse.core.wearprotocol.WorkoutSnapshot
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataClient.OnDataChangedListener
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.PutDataRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

/** [WearChannel] über [DataClient] (Snapshots, Pläne, Kommandos) und [NodeClient] (Verbindung). */
@Singleton
class DataClientWearChannel @Inject constructor(
    private val dataClient: DataClient,
    private val nodeClient: NodeClient,
) : WearChannel {

    override val snapshots: Flow<WorkoutSnapshot> =
        dataItemFlow(WearPaths.ACTIVE_WORKOUT, WearProtocol::decodeSnapshot)

    override val plans: Flow<PlanSummaryList> =
        dataItemFlow(WearPaths.PLANS_SUMMARY, WearProtocol::decodePlans)

    /** Die Data Layer meldet kein Ereignis für „Telefon weg“, deshalb wird die Knotenliste abgefragt. */
    override val connection: Flow<WearConnection> = flow {
        while (true) {
            emit(if (isPhoneConnected()) WearConnection.CONNECTED else WearConnection.DISCONNECTED)
            delay(CONNECTION_POLL_MILLIS)
        }
    }.distinctUntilChanged()

    override suspend fun sendCommand(command: WearCommand) {
        val request = PutDataRequest.create(WearPaths.command(command.commandId))
            .setData(WearProtocol.encodeCommand(command))
            .setUrgent()
        dataClient.putDataItem(request).await()
    }

    private suspend fun isPhoneConnected(): Boolean = try {
        nodeClient.connectedNodes.await().isNotEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private fun <T : Any> dataItemFlow(path: String, decode: (ByteArray) -> T?): Flow<T> = callbackFlow {
        val listener = OnDataChangedListener { events ->
            for (event in events) {
                if (event.type != DataEvent.TYPE_CHANGED || event.dataItem.uri.path != path) continue
                event.dataItem.data?.let(decode)?.let { trySend(it) }
            }
        }
        // Erst den Listener anmelden, dann den vorhandenen Stand lesen; Duplikate verwirft der Reducer über die Version.
        dataClient.addListener(listener)
        try {
            val buffer = dataClient.getDataItems(wearUri(path)).await()
            try {
                for (item in buffer) item.data?.let(decode)?.let { trySend(it) }
            } finally {
                buffer.release()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Kein Stand lesbar (z. B. Play Services nicht bereit): Änderungen kommen weiter über den Listener.
        }
        awaitClose { dataClient.removeListener(listener) }
    }

    private fun wearUri(path: String): Uri =
        Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(path).build()

    private companion object {
        const val CONNECTION_POLL_MILLIS = 5_000L
    }
}
