package app.gympulse.wear.workout

import app.gympulse.core.wearprotocol.WearPaths
import app.gympulse.core.wearprotocol.WearProtocol
import app.gympulse.wear.data.WatchWorkoutRepository
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Weckt die Uhr-App, wenn das Telefon ein Workout meldet, auch wenn sie gerade nicht läuft.
 *
 * Ohne diesen Dienst bekäme die Uhr den Snapshot nur bei laufendem Prozess mit; dann würde weder
 * die Uhr vibrieren noch (bei verbundener Uhr) das Telefon. Den eigentlichen Start übernimmt der
 * [WatchWorkoutServiceStarter], sobald das Repository die Session kennt. Bis dahin wartet
 * [onDataChanged] kurz (Hintergrund-Thread), damit der Prozess nicht vorher beendet wird.
 */
@AndroidEntryPoint
class WorkoutDataListenerService : WearableListenerService() {
    @Inject lateinit var repository: WatchWorkoutRepository

    @Inject lateinit var starter: WatchWorkoutServiceStarter

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val hasActiveSession = dataEvents.any { event ->
            event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == WearPaths.ACTIVE_WORKOUT &&
                event.dataItem.data?.let(WearProtocol::decodeSnapshot)?.session != null
        }
        if (!hasActiveSession) return
        // Normalerweise schon durch die Application gestartet; idempotent.
        starter.start()
        runBlocking {
            withTimeoutOrNull(HANDOVER_TIMEOUT_MILLIS) { repository.session.first { it != null } }
        }
        starter.ensureRunning()
    }

    private companion object {
        const val HANDOVER_TIMEOUT_MILLIS = 5_000L
    }
}
