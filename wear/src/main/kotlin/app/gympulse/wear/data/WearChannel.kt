package app.gympulse.wear.data

import app.gympulse.core.wearprotocol.PlanSummaryList
import app.gympulse.core.wearprotocol.WearCommand
import app.gympulse.core.wearprotocol.WorkoutSnapshot
import kotlinx.coroutines.flow.Flow

/** Verbindung zum Telefon aus Sicht der Uhr. */
enum class WearConnection {
    /** Noch nicht geprüft (App-Start). */
    UNKNOWN,
    CONNECTED,
    DISCONNECTED,
}

/**
 * Schmale Schnittstelle zur Wearable Data Layer API. Die Implementierung ([DataClientWearChannel])
 * hängt an Play Services; Tests nutzen einen Fake.
 */
interface WearChannel {
    /**
     * Stand von `/workout/active`: zuerst der zuletzt bekannte Snapshot (falls vorhanden),
     * danach jede Änderung. Kaputte oder inkompatible Nutzlasten werden nicht ausgegeben.
     */
    val snapshots: Flow<WorkoutSnapshot>

    /** Stand von `/plans/summary`, gleiche Semantik wie [snapshots]. */
    val plans: Flow<PlanSummaryList>

    /** Erreichbarkeit des Telefons. Emittiert nur bei Änderungen. */
    val connection: Flow<WearConnection>

    /**
     * Legt [command] als urgent DataItem unter `/cmd/<commandId>` ab. Die Play Services puffern es,
     * wenn das Telefon gerade fehlt. Gleiche ID, gleicher Inhalt: kein Effekt. Wirft bei einem Fehler.
     */
    suspend fun sendCommand(command: WearCommand)
}
