package app.gympulse.core.wearsync

import app.gympulse.core.data.repository.ExerciseRepository
import app.gympulse.core.data.repository.PlanRepository
import app.gympulse.core.data.repository.WorkoutRepository
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.WorkoutPlan
import app.gympulse.core.workout.timer.RestTimerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject

/** Daten, die der Snapshot neben der Session braucht (Namen, „Vorher“-Werte, Pläne, Pausendauer). */
interface WearSnapshotSources {
    /** Übungskatalog nach ID, Namen in der Gerätesprache wie im Workout-Screen. */
    val exercises: Flow<Map<String, Exercise>>

    /** Alle Pläne; archivierte filtert das Mapping heraus. */
    val plans: Flow<List<WorkoutPlan>>

    /** Pause aus den Einstellungen für Übungen ohne eigene Pausendauer. */
    val defaultRestSeconds: Flow<Int>

    /** Sätze der letzten abgeschlossenen Session dieser Übung („Vorher“), ohne [currentSessionId]. */
    suspend fun previousSets(exerciseId: String, currentSessionId: String): List<SetEntry>

    suspend fun planExists(planId: String): Boolean
}

class RepositoryWearSnapshotSources @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val planRepository: PlanRepository,
    private val workoutRepository: WorkoutRepository,
    restTimerSettings: RestTimerSettings,
) : WearSnapshotSources {
    override val exercises: Flow<Map<String, Exercise>>
        get() = exerciseRepository.observeExercises(Locale.getDefault().language).map { list -> list.associateBy { it.id } }

    override val plans: Flow<List<WorkoutPlan>> = planRepository.observePlans()

    override val defaultRestSeconds: Flow<Int> = restTimerSettings.restSeconds

    override suspend fun previousSets(exerciseId: String, currentSessionId: String): List<SetEntry> =
        workoutRepository.previousSets(exerciseId, currentSessionId)

    override suspend fun planExists(planId: String): Boolean =
        planRepository.observePlan(planId).first()?.takeUnless { it.isArchived } != null
}
