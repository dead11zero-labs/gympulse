package app.gympulse.core.wearprotocol

/**
 * Zustand des Workouts auf der Uhr: letzter bestätigter Snapshot des Telefons plus die lokal
 * ausgelösten, noch nicht quittierten Kommandos. [session] ist die daraus berechnete Ansicht,
 * die die UI anzeigt (optimistisch).
 *
 * Instanzen entstehen über [WatchWorkoutReducer]; der Reducer ist rein und threadfrei.
 *
 * @property acknowledgedIds zuletzt quittierte Kommando-IDs, damit ein doppelt ausgelöstes oder
 *   erneut zugestelltes Kommando nicht wieder angewendet wird (begrenzt auf
 *   [WatchWorkoutReducer.MAX_REMEMBERED_ACKS])
 * @property notices Quittungen für eigene Kommandos, die das Telefon ignoriert hat; die UI zeigt
 *   sie als Hinweis und räumt sie mit [WatchWorkoutReducer.dismissNotices] ab
 * @property incompatibleProtocol das Telefon spricht eine andere [WearProtocol.VERSION]
 */
data class WatchWorkoutState(
    val confirmed: WorkoutSnapshot? = null,
    val pending: List<WearCommand> = emptyList(),
    val acknowledgedIds: List<String> = emptyList(),
    val notices: List<CommandAck> = emptyList(),
    val incompatibleProtocol: Boolean = false,
    val session: SessionSnapshot? = null,
) {
    /** Version des letzten bestätigten Snapshots oder null, solange keiner angekommen ist. */
    val version: Long? get() = confirmed?.version

    /** Ein Start wurde ausgelöst, das Telefon hat die Session aber noch nicht gemeldet. */
    val isStartPending: Boolean get() = pending.any { it is StartWorkout }

    /** Beenden oder Verwerfen wurde ausgelöst und ist noch nicht quittiert. */
    val isEndPending: Boolean get() = pending.any { it is Finish || it is Discard }

    val hasPendingCommands: Boolean get() = pending.isNotEmpty()
}

/**
 * Reducer für den optimistischen Zustand der Uhr.
 *
 * Regeln:
 * 1. Ein lokales Kommando wird sofort auf die Ansicht angewendet und bleibt in [WatchWorkoutState.pending],
 *    bis ein Snapshot es quittiert. Gleiche Kommando-ID zweimal ist ein No-op.
 * 2. Ein Snapshot wird nur übernommen, wenn seine `version` größer ist als die zuletzt gesehene.
 *    Quittierte Kommandos fallen dann aus `pending`; die übrigen werden auf den neuen Stand neu angewendet.
 * 3. Konflikte am selben Satz werden pro Feld (Gewicht, Wiederholungen, abgehakt) entschieden:
 *    der spätere Zeitstempel gewinnt. Für den Telefon-Stand gilt [SetSnapshot.updatedAt] bzw.
 *    [SetSnapshot.fieldUpdatedAt], für Kommandos [WearCommand.issuedAt]. Bei Gleichstand gewinnt
 *    das Kommando; mehrere Kommandos werden nach (issuedAt, commandId) geordnet, damit die
 *    Reihenfolge der Zustellung keine Rolle spielt.
 * 4. Kommandos für eine andere Session ändern die Ansicht nicht.
 */
object WatchWorkoutReducer {
    const val MAX_REMEMBERED_ACKS = 256

    private val commandOrder = compareBy<WearCommand>({ it.issuedAt }, { it.commandId })

    /** Lokal ausgelöstes Kommando optimistisch anwenden. */
    fun onLocalCommand(state: WatchWorkoutState, command: WearCommand): WatchWorkoutState {
        val id = command.commandId
        if (state.pending.any { it.commandId == id } || id in state.acknowledgedIds) return state
        val pending = state.pending + command
        return state.copy(pending = pending, session = project(state.confirmed?.session, pending))
    }

    /** Snapshot vom Telefon übernehmen, sofern er neuer ist. */
    fun onSnapshot(state: WatchWorkoutState, snapshot: WorkoutSnapshot): WatchWorkoutState {
        if (!WearProtocol.isCompatible(snapshot.protocolVersion)) {
            return if (state.incompatibleProtocol) state else state.copy(incompatibleProtocol = true)
        }
        val lastVersion = state.confirmed?.version
        if (lastVersion != null && snapshot.version <= lastVersion) return state

        val ackIds = snapshot.acknowledgedCommandIds
        val ownIds = state.pending.mapTo(HashSet()) { it.commandId }
        val newNotices = snapshot.acks.filter { it.result != AckResult.APPLIED && it.commandId in ownIds }
        val pending = state.pending.filterNot { it.commandId in ackIds }
        val remembered = (state.acknowledgedIds + ackIds.filterNot { it in state.acknowledgedIds })
            .takeLast(MAX_REMEMBERED_ACKS)
        return state.copy(
            confirmed = snapshot,
            pending = pending,
            acknowledgedIds = remembered,
            notices = state.notices + newNotices,
            incompatibleProtocol = false,
            session = project(snapshot.session, pending),
        )
    }

    fun dismissNotices(state: WatchWorkoutState): WatchWorkoutState =
        if (state.notices.isEmpty()) state else state.copy(notices = emptyList())

    /**
     * Ein einzelnes Kommando auf [session] anwenden, mit den Zeitstempeln aus dem Snapshot als
     * Feld-Uhren. null heißt, die Session ist danach beendet ([Finish], [Discard]).
     * Auch für das Telefon nutzbar, um dieselben Regeln vorab zu prüfen.
     */
    fun applyCommand(session: SessionSnapshot, command: WearCommand): SessionSnapshot? =
        step(session, command, HashMap())

    /** Gewinnt ein Kommando von [issuedAt] gegen einen Feldstand von [fieldUpdatedAt]? */
    fun commandWins(issuedAt: Long, fieldUpdatedAt: Long): Boolean = issuedAt >= fieldUpdatedAt

    /** Ansicht = Basis-Snapshot + offene Kommandos in (issuedAt, commandId)-Reihenfolge. */
    internal fun project(base: SessionSnapshot?, pending: List<WearCommand>): SessionSnapshot? {
        var current = base ?: return null
        val clocks = HashMap<String, FieldClock>()
        for (command in pending.sortedWith(commandOrder)) {
            current = step(current, command, clocks) ?: return null
        }
        return current
    }

    private data class FieldClock(val weightKg: Long, val reps: Long, val completed: Long)

    private fun step(session: SessionSnapshot, command: WearCommand, clocks: MutableMap<String, FieldClock>): SessionSnapshot? {
        if (command is SessionCommand && command.sessionId != session.sessionId) return session
        return when (command) {
            is StartWorkout -> session
            is CompleteSet -> completeSet(session, command, clocks)
            is UpdateSet -> updateSet(session, command, clocks)
            is AdjustRest -> adjustRest(session, command)
            is SkipRest -> session.copy(restEndsAt = null)
            is Finish, is Discard -> null
            // Betrifft nur das Telefon; die Ansicht der Uhr bleibt unverändert.
            is HeartRateSummary -> session
        }
    }

    private fun completeSet(session: SessionSnapshot, command: CompleteSet, clocks: MutableMap<String, FieldClock>): SessionSnapshot {
        var restEndsAt = session.restEndsAt
        val updated = session.updateSet(command.setId) { exercise, set ->
            val clock = clocks.clockFor(set)
            if (!commandWins(command.issuedAt, clock.completed)) return@updateSet set
            clocks[set.setId] = clock.copy(completed = command.issuedAt)
            val rest = exercise.restSeconds
            if (command.completed && rest != null && rest > 0) {
                restEndsAt = command.issuedAt + rest * 1_000L
            }
            set.copy(
                isCompleted = command.completed,
                completedAt = if (command.completed) command.issuedAt else null,
            )
        }
        return updated.copy(restEndsAt = restEndsAt)
    }

    private fun updateSet(session: SessionSnapshot, command: UpdateSet, clocks: MutableMap<String, FieldClock>): SessionSnapshot =
        session.updateSet(command.setId) { _, set ->
            var clock = clocks.clockFor(set)
            var result = set
            val weight = command.weightKg
            if (weight != null && weight.isFinite() && weight >= 0.0 && commandWins(command.issuedAt, clock.weightKg)) {
                result = result.copy(weightKg = weight)
                clock = clock.copy(weightKg = command.issuedAt)
            }
            val reps = command.reps
            if (reps != null && reps >= 0 && commandWins(command.issuedAt, clock.reps)) {
                result = result.copy(reps = reps)
                clock = clock.copy(reps = command.issuedAt)
            }
            clocks[set.setId] = clock
            result
        }

    private fun adjustRest(session: SessionSnapshot, command: AdjustRest): SessionSnapshot {
        val endsAt = session.restEndsAt ?: return session
        val adjusted = endsAt + command.deltaSeconds * 1_000L
        return session.copy(restEndsAt = adjusted.takeIf { it > command.issuedAt })
    }

    private fun MutableMap<String, FieldClock>.clockFor(set: SetSnapshot): FieldClock =
        getOrPut(set.setId) { FieldClock(set.weightUpdatedAt(), set.repsUpdatedAt(), set.completedUpdatedAt()) }

    private inline fun SessionSnapshot.updateSet(
        setId: String,
        transform: (ExerciseSnapshot, SetSnapshot) -> SetSnapshot,
    ): SessionSnapshot {
        val exerciseIndex = exercises.indexOfFirst { exercise -> exercise.sets.any { it.setId == setId } }
        if (exerciseIndex < 0) return this
        val exercise = exercises[exerciseIndex]
        val sets = exercise.sets.map { if (it.setId == setId) transform(exercise, it) else it }
        return copy(exercises = exercises.toMutableList().also { it[exerciseIndex] = exercise.copy(sets = sets) })
    }
}
