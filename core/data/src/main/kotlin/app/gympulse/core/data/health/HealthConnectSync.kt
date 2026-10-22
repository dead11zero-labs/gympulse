package app.gympulse.core.data.health

import app.gympulse.core.data.repository.ExerciseRepository
import app.gympulse.core.data.repository.WorkoutRepository
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.featuregate.UserFeatureToggles
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Health-Connect-Anbindung: exportiert beendete Workouts. Alles läuft nur bei aktivem
 * [Feature.HEALTH_CONNECT], verfügbarem Client und erteilten Berechtigungen; Fehler des Dienstes brechen das Training nie ab.
 */
class HealthConnectSync @Inject constructor(
    private val dataSource: HealthDataSource,
    private val featureGate: FeatureGate,
    private val userToggles: UserFeatureToggles,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
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
            val language = Locale.getDefault().language
            val known = exerciseRepository.observeExercises(language).first().associateBy { it.id }
            // Inzwischen gelöschte Übungen fehlen in der Liste; Name und Typ einzeln nachladen.
            val exercises = session.exercises.map { it.exerciseId }.distinct().mapNotNull { id ->
                known[id] ?: exerciseRepository.getExerciseIncludingDeleted(id, language)
            }.associateBy { it.id }
            val workout = WorkoutHealthMapper.toHealthWorkout(session, exercises, time.zone())
                ?: return@guarded
            dataSource.insertWorkout(workout)
        }
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
}
