package app.gympulse.wear.workout

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import app.gympulse.core.wearprotocol.HeartRateLimits
import app.gympulse.wear.data.WearAppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pulsmessung über den ExerciseClient der Health Services (Typ Krafttraining, nur Herzfrequenz).
 *
 * Die Messung läuft nur, solange ein Workout läuft; der [WatchWorkoutService] hält dafür den Prozess
 * im Vordergrund. Ob Krafttraining samt Puls unterstützt wird, fragt die Capability-Abfrage ab, denn
 * das unterscheidet sich je nach Uhr (Galaxy Watch, Pixel Watch). Fehlt die Berechtigung oder gibt
 * es einen Fehler, bleibt [reading] auf [HeartRateReading.NoPermission] bzw. [HeartRateReading.Unavailable]
 * und das Workout läuft ohne Puls weiter.
 */
@Singleton
class HealthServicesHeartRateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    @WearAppScope private val scope: CoroutineScope,
) : HeartRateMonitor {
    private val _reading = MutableStateFlow<HeartRateReading>(HeartRateReading.Off)
    override val reading: StateFlow<HeartRateReading> = _reading.asStateFlow()

    private val lock = Mutex()
    private var running = false
    private var sequence = 0L

    private val client: ExerciseClient by lazy { HealthServices.getClient(context).exerciseClient }

    private val callback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val samples = update.latestMetrics.getData(DataType.HEART_RATE_BPM)
            val bpm = samples.lastOrNull()?.value?.roundToInt() ?: return
            _reading.value = if (bpm in HeartRateLimits.MIN_BPM..HeartRateLimits.MAX_BPM) {
                HeartRateReading.Bpm(bpm, ++sequence)
            } else {
                // 0 oder Ausreißer: Sensor hat gerade keinen Hautkontakt.
                HeartRateReading.Searching
            }
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit

        override fun onRegistered() = Unit

        override fun onRegistrationFailed(throwable: Throwable) {
            if (running) _reading.value = HeartRateReading.Unavailable
        }

        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) = Unit
    }

    override suspend fun start(): Boolean = lock.withLock {
        if (running) return@withLock true
        val permission = HeartRatePermission.requiredFor(Build.VERSION.SDK_INT)
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            _reading.value = HeartRateReading.NoPermission
            return@withLock false
        }
        try {
            val type = supportedExerciseType()
            if (type == null) {
                _reading.value = HeartRateReading.Unavailable
                return@withLock false
            }
            val config = ExerciseConfig.builder(type)
                .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                .setIsAutoPauseAndResumeEnabled(false)
                .setIsGpsEnabled(false)
                .build()
            _reading.value = HeartRateReading.Searching
            client.setUpdateCallback(callback)
            client.startExerciseAsync(config).await()
            running = true
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Z. B. läuft schon ein anderes Workout (Samsung Health) oder die Health Services fehlen.
            _reading.value = HeartRateReading.Unavailable
            false
        }
    }

    override fun stop() {
        scope.launch {
            lock.withLock {
                val wasRunning = running
                running = false
                _reading.value = HeartRateReading.Off
                try {
                    if (wasRunning) client.endExerciseAsync().await()
                    client.clearUpdateCallbackAsync(callback).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Nichts mehr zu beenden, etwa weil das System die Messung schon abgebrochen hat.
                }
            }
        }
    }

    /** Krafttraining, sonst das allgemeine Workout, sofern die Uhr dafür Herzfrequenz liefert. */
    private suspend fun supportedExerciseType(): ExerciseType? {
        val capabilities = client.getCapabilitiesAsync().await()
        return listOf(ExerciseType.STRENGTH_TRAINING, ExerciseType.WORKOUT).firstOrNull { type ->
            type in capabilities.supportedExerciseTypes &&
                DataType.HEART_RATE_BPM in capabilities.getExerciseTypeCapabilities(type).supportedDataTypes
        }
    }
}
