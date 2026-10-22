package app.gympulse.core.wearprotocol

internal object Fixtures {
    const val SESSION = "0190a000-0000-7000-8000-000000000001"
    const val OTHER_SESSION = "0190a000-0000-7000-8000-000000000002"
    const val SET_1 = "0190a000-0000-7000-8000-0000000000a1"
    const val SET_2 = "0190a000-0000-7000-8000-0000000000a2"
    const val SET_3 = "0190a000-0000-7000-8000-0000000000b1"

    fun cmdId(n: Int): String = "0190a000-0000-7000-8000-%012x".format(n)

    fun session(
        restEndsAt: Long? = null,
        set1: SetSnapshot = set(SET_1, number = 1, weightKg = 80.0, reps = 8, updatedAt = 1_000),
        restSeconds: Int? = 90,
    ) = SessionSnapshot(
        sessionId = SESSION,
        planId = "plan-push",
        title = "Push Day",
        startedAt = 500,
        restEndsAt = restEndsAt,
        exercises = listOf(
            ExerciseSnapshot(
                sessionExerciseId = "se-1",
                exerciseId = "bench",
                name = "Bankdrücken",
                restSeconds = restSeconds,
                sets = listOf(set1, set(SET_2, number = 2, weightKg = 80.0, reps = 8, updatedAt = 1_000)),
            ),
            ExerciseSnapshot(
                sessionExerciseId = "se-2",
                exerciseId = "pullup",
                name = "Klimmzüge",
                trackingType = WearTrackingType.REPS,
                isBodyweight = true,
                restSeconds = null,
                sets = listOf(set(SET_3, number = 1, weightKg = null, reps = 10, updatedAt = 1_000)),
            ),
        ),
    )

    fun set(id: String, number: Int, weightKg: Double?, reps: Int?, updatedAt: Long, completed: Boolean = false) =
        SetSnapshot(
            setId = id,
            number = number,
            weightKg = weightKg,
            reps = reps,
            isCompleted = completed,
            completedAt = if (completed) updatedAt else null,
            updatedAt = updatedAt,
            previous = PreviousSet(weightKg = 77.5, reps = 8),
        )

    fun snapshot(version: Long, session: SessionSnapshot? = session(), vararg acks: CommandAck) =
        WorkoutSnapshot(version = version, session = session, acks = acks.toList())

    fun SessionSnapshot.set(id: String): SetSnapshot = exercises.flatMap { it.sets }.first { it.setId == id }
}
