package app.gympulse.core.wearsync

import app.gympulse.core.wearprotocol.WearPaths
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Empfängt Kommandos der Uhr (`/cmd/<uuid>`), auch ohne offene App: Die Play Services wecken dafür
 * den Prozess. Die Verarbeitung läuft blockierend im Hintergrund-Thread von [onDataChanged], weil
 * der Dienst danach beendet werden darf; die Events werden vorher kopiert, da der Puffer danach
 * ungültig ist.
 */
@AndroidEntryPoint
class WearCommandListenerService : WearableListenerService() {
    @Inject lateinit var processor: WearCommandProcessor

    @Inject lateinit var wearSync: WearSync

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val commands = dataEvents
            .filter { it.type == DataEvent.TYPE_CHANGED }
            .mapNotNull { event ->
                val path = event.dataItem.uri.path ?: return@mapNotNull null
                if (WearPaths.isCommandPath(path)) WearDataItem(path, event.dataItem.data) else null
            }
        if (commands.isEmpty()) return
        // Normalerweise schon durch die Application gestartet; idempotent.
        wearSync.start()
        runBlocking {
            withTimeoutOrNull(PROCESS_TIMEOUT_MILLIS) { processor.process(commands) }
        }
    }

    private companion object {
        const val PROCESS_TIMEOUT_MILLIS = 15_000L
    }
}
