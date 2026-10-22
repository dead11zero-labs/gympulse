package app.gympulse.core.wearsync

import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.WorkoutSession
import app.gympulse.core.workout.ActiveWorkoutController
import app.gympulse.core.workout.SetEditResult
import app.gympulse.core.workout.timer.runningAt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/**
 * Das, was die Wear-Anbindung vom [ActiveWorkoutController] braucht. Eigene Schnittstelle, damit
 * Publisher und Kommando-Verarbeitung mit einem Fake getestet werden können.
 */
interface WearWorkoutPort {
    val activeSession: Flow<WorkoutSession?>

    /** Ende der laufenden Pause oder null; ändert sich bei Start, Anpassung und Abbruch. */
    val restEndsAt: Flow<Instant?>

    /** Startet ein Workout wie die Handy-UI (oder setzt das laufende fort) und liefert die Session-ID. */
    suspend fun startWorkout(planId: String?): String

    suspend fun editSet(sessionId: String, setId: String, transform: (SetEntry) -> SetEntry?): SetEditResult

    suspend fun setSetCompleted(
        sessionId: String,
        setId: String,
        completed: Boolean,
        completedAt: Instant?,
        accept: (SetEntry) -> Boolean,
    ): SetEditResult

    fun adjustRest(deltaSeconds: Int)

    fun skipRest()

    /** @return false, wenn keine Session lief */
    suspend fun finish(): Boolean

    suspend fun discard()
}

class ControllerWorkoutPort @Inject constructor(
    private val controller: ActiveWorkoutController,
    private val time: TimeProvider,
) : WearWorkoutPort {
    override val activeSession: Flow<WorkoutSession?> = controller.activeSession

    override val restEndsAt: Flow<Instant?> =
        controller.restTimerState.map { it.runningAt(time.now())?.endsAt }

    override suspend fun startWorkout(planId: String?): String = controller.startWorkout(planId)

    override suspend fun editSet(sessionId: String, setId: String, transform: (SetEntry) -> SetEntry?) =
        controller.editSet(sessionId, setId, transform)

    override suspend fun setSetCompleted(
        sessionId: String,
        setId: String,
        completed: Boolean,
        completedAt: Instant?,
        accept: (SetEntry) -> Boolean,
    ) = controller.setSetCompleted(sessionId, setId, completed, completedAt, accept)

    override fun adjustRest(deltaSeconds: Int) = controller.adjustRest(deltaSeconds)

    override fun skipRest() = controller.skipRest()

    override suspend fun finish(): Boolean = controller.finishWorkout() != null

    override suspend fun discard() = controller.discardWorkout()
}
