package app.gympulse.core.wearsync

import android.net.Uri
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataRequest
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Ein DataItem als Kopie: Pfad ohne Host und Nutzlast (die Puffer der Play Services leben nicht lange). */
class WearDataItem(val path: String, val data: ByteArray?)

/**
 * Schmale Schnittstelle zur Wearable Data Layer am Telefon. Die Implementierung hängt an den
 * Play Services ([PlayServicesWearDataClient]); Tests nutzen einen Fake.
 */
interface WearDataClient {
    /** Legt [data] unter [path] ab bzw. ersetzt das vorhandene Item. Wirft bei einem Fehler. */
    suspend fun put(path: String, data: ByteArray, urgent: Boolean = true)

    /** Löscht die Items unter genau [path], egal von welchem Knoten sie stammen. */
    suspend fun delete(path: String)

    /** Alle vorhandenen Items, deren Pfad mit [pathPrefix] beginnt. */
    suspend fun items(pathPrefix: String): List<WearDataItem>
}

@Singleton
class PlayServicesWearDataClient @Inject constructor(
    private val dataClient: DataClient,
) : WearDataClient {

    override suspend fun put(path: String, data: ByteArray, urgent: Boolean) {
        val request = PutDataRequest.create(path).setData(data)
        if (urgent) request.setUrgent()
        dataClient.putDataItem(request).await()
    }

    override suspend fun delete(path: String) {
        dataClient.deleteDataItems(wearUri(path)).await()
    }

    override suspend fun items(pathPrefix: String): List<WearDataItem> {
        val buffer = dataClient.getDataItems(wearUri(pathPrefix), DataClient.FILTER_PREFIX).await()
        return try {
            buffer.mapNotNull { item -> item.uri.path?.let { WearDataItem(it, item.data) } }
        } finally {
            buffer.release()
        }
    }

    /** URI ohne Host: trifft die Items aller Knoten. */
    private fun wearUri(path: String): Uri =
        Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(path).build()
}
