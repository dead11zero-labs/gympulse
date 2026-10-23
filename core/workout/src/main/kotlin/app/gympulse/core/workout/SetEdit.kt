package app.gympulse.core.workout

import app.gympulse.core.model.SetEntry

/** Outcome of [ActiveWorkoutController.editSet] and [ActiveWorkoutController.setSetCompleted]. */
enum class SetEditStatus {
    /** The set was changed and stored. */
    CHANGED,

    /** The set already had the requested values. */
    UNCHANGED,

    /** The caller's rule (e.g. a newer change on the phone) rejected the change. */
    REJECTED,

    /** The set does not exist (anymore) in the running session. */
    SET_NOT_FOUND,

    /** No session is running. */
    NO_SESSION,

    /** Another session than the requested one is running. */
    OTHER_SESSION,
}

/** @property set the stored set after the call, null if it does not exist */
data class SetEditResult(
    val status: SetEditStatus,
    val set: SetEntry? = null,
)
