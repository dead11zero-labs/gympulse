package app.gympulse.core.data.health

import app.gympulse.core.data.repository.BodyMeasurementRepository
import app.gympulse.core.data.repository.ExerciseRepository
import app.gympulse.core.data.repository.ProfileRepository
import app.gympulse.core.data.repository.WorkoutRepository
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.featuregate.UserFeatureToggles
import app.gympulse.core.model.MeasurementSource
import java.time.Duration
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Health-Connect-Anbindung: exportiert beendete Workouts und übernimmt auf Wunsch das
 * Körpergewicht. Alles läuft nur bei aktivem [Feature.HEALTH_CONNECT], verfügbarem Client und
 * erteilten Berechtigungen; Fehler des Dienstes brechen das Training nie ab.
 */
class HealthConnectSync @Inject constructor(
    private val dataSource: HealthDataSource,
    private val featureGate: FeatureGate,
    private val userToggles: UserFeatureToggles,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val bodyMeasurementRepository: BodyMeasurementRepository,
    private val profileRepository: ProfileRepository,
    private val time: TimeProvider,
) : FinishedWorkoutExporter {

    val requiredPermissions: Set<String> get() = dataSource.requiredPermissions

    fun availability(): HealthAvailability = dataSource.availability()

    fun observeEnabled(): Flow<Boolean> = featureGate.observe(Feature.HEALTH_CONNECT)

    fun setEnabled(enabled: Boolean) = userToggles.setEnabled(Feature.HEALTH_CONNECT, enabled)

    suspend fun hasAllPermissions(): Boolean = guarded(false) {
        dataSource.availability() == HealthAvailability.AVAILABLE &&
            dataSource.grantedPermissions().containsAll(dataSource.requiredPermissions)
    }

    override suspend fun export(sessionId: String) {
        if (!isReady()) return
        guarded(Unit) {
            val session = workoutRepository.observeSession(sessionId).first() ?: return@guarded
            val categories = exerciseRepository.observeExercises(Locale.getDefault().language).first()
                .associate { it.id to it.category }
            val workout = WorkoutHealthMapper.toHealthWorkout(session, categories, time.zone())
                ?: return@guarded
            dataSource.insertWorkout(workout)
        }
    }

    /** Jüngstes Gewicht der letzten [WEIGHT_LOOKBACK] aus Health Connect, oder null. */
    suspend fun latestWeight(): HealthWeight? {
        if (!isReady()) return null
        return guarded(null) { dataSource.readLatestWeight(time.now().minus(WEIGHT_LOOKBACK)) }
    }

    /**
     * Übernimmt [weight] als Körpermaß mit Quelle Health Connect.
     *
     * @return false, wenn derselbe Messwert schon übernommen wurde.
     */
    suspend fun importWeight(weight: HealthWeight): Boolean {
        val existing = bodyMeasurementRepository.observeAll().first()
        if (!WorkoutHealthMapper.isNewWeight(weight, existing)) return false
        profileRepository.recordBodyWeight(
            weightKg = weight.weightKg,
            measuredAt = weight.measuredAt,
            source = MeasurementSource.HEALTH_CONNECT,
        )
        return true
    }

    private suspend fun isReady(): Boolean =
        featureGate.isEnabled(Feature.HEALTH_CONNECT) && hasAllPermissions()

    private inline fun <T> guarded(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Health Connect kann jederzeit Berechtigungen entziehen oder Anfragen drosseln.
        fallback
    }

    private companion object {
        val WEIGHT_LOOKBACK: Duration = Duration.ofDays(365)
    }
}
