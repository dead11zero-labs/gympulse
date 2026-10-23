package app.gympulse.wear.workout

import app.gympulse.core.wearprotocol.WearCapabilities
import app.gympulse.wear.data.WearAppScope
import com.google.android.gms.wearable.CapabilityClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Meldet dem Telefon, ob die Uhr das Pausenende gerade selbst signalisiert. */
interface RestAlertCapability {
    suspend fun setAdvertised(active: Boolean)
}

/** [RestAlertCapability] über eine dynamische lokale Capability ([WearCapabilities.WATCH_REST_ALERTS]). */
class CapabilityRestAlertCapability @Inject constructor(
    private val capabilityClient: CapabilityClient,
) : RestAlertCapability {
    override suspend fun setAdvertised(active: Boolean) {
        try {
            if (active) {
                capabilityClient.addLocalCapability(WearCapabilities.WATCH_REST_ALERTS).await()
            } else {
                capabilityClient.removeLocalCapability(WearCapabilities.WATCH_REST_ALERTS).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Schon (nicht) angemeldet oder Play Services fehlen: Das Telefon vibriert dann im Zweifel selbst.
        }
    }
}

/**
 * Hält die Capability passend zum Workout-Dienst: an, solange er läuft, sonst aus.
 *
 * Der Startwert ist „aus“, damit eine liegen gebliebene Anmeldung (Prozess wurde während eines
 * Workouts beendet) beim nächsten Start der App entfernt wird. Die Werte werden von genau einer
 * Coroutine nacheinander geschrieben, damit sich An und Aus nicht überholen.
 */
@Singleton
class RestAlertAdvertiser @Inject constructor(
    private val capability: RestAlertCapability,
    @WearAppScope scope: CoroutineScope,
) {
    private val active = MutableStateFlow(false)

    init {
        scope.launch { active.collect { capability.setAdvertised(it) } }
    }

    fun setActive(value: Boolean) {
        active.value = value
    }
}
