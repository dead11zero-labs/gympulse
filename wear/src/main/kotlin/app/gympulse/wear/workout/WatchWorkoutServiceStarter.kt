package app.gympulse.wear.workout

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.gympulse.wear.data.WatchWorkoutRepository
import app.gympulse.wear.data.WearAppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Startet den [WatchWorkoutService], sobald eine Session aktiv ist; beendet wird er von sich aus,
 * wenn die Session endet oder verworfen wird.
 *
 * Android verbietet Foreground Services aus dem Hintergrund (API 31+). Klappt der Start nicht,
 * wird er beim nächsten Öffnen der App ([ensureRunning] aus der Activity) erneut versucht. Bis dahin
 * meldet die Uhr keine [app.gympulse.core.wearprotocol.WearCapabilities.WATCH_REST_ALERTS], und das
 * Telefon vibriert selbst.
 */
@Singleton
class WatchWorkoutServiceStarter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: WatchWorkoutRepository,
    // Früh erzeugen, damit eine liegen gebliebene Capability beim Prozessstart entfernt wird.
    @Suppress("unused") private val advertiser: RestAlertAdvertiser,
    @WearAppScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    /** Idempotent; die Application ruft das in `onCreate` auf. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            repository.session.map { it != null }.distinctUntilChanged().collect { active ->
                if (active) startService()
            }
        }
    }

    /** Startet den Dienst, falls gerade eine Session läuft (z. B. beim Öffnen der App). */
    fun ensureRunning() {
        if (repository.state.value.session != null) startService()
    }

    /** @return false, wenn das System den Start verweigert hat. */
    fun startService(): Boolean = try {
        ContextCompat.startForegroundService(context, Intent(context, WatchWorkoutService::class.java))
        true
    } catch (e: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException (API 31+) beim Start aus dem Hintergrund.
        false
    } catch (e: SecurityException) {
        false
    }
}
