package app.gympulse.core.wearprotocol

import app.gympulse.core.model.Ids
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Kommando von der Uhr ans Telefon, verschickt als urgent DataItem unter `/cmd/<commandId>`.
 *
 * Das Telefon wendet jedes Kommando höchstens einmal an (verarbeitete [commandId]s werden gemerkt)
 * und quittiert es im nächsten [WorkoutSnapshot]. [issuedAt] ist die Uhrzeit der Uhr beim Auslösen
 * (Unix-Millisekunden) und entscheidet Konflikte am selben Satz.
 */
@Serializable
sealed interface WearCommand {
    val commandId: String
    val issuedAt: Long
}

/** Kommando, das sich auf eine laufende Session bezieht. Passt [sessionId] nicht, wird es ignoriert. */
@Serializable
sealed interface SessionCommand : WearCommand {
    val sessionId: String
}

/** Kommando, das einen bestimmten Satz ändert. */
@Serializable
sealed interface SetCommand : SessionCommand {
    val setId: String
}

/** Satz abhaken ([completed] = true) oder das Häkchen zurücknehmen. Abhaken startet die Pause. */
@Serializable
@SerialName("complete_set")
data class CompleteSet(
    override val commandId: String,
    override val issuedAt: Long,
    override val sessionId: String,
    override val setId: String,
    val completed: Boolean = true,
) : SetCommand

/** Gewicht (kg) und/oder Wiederholungen setzen; null heißt „unverändert“. */
@Serializable
@SerialName("update_set")
data class UpdateSet(
    override val commandId: String,
    override val issuedAt: Long,
    override val sessionId: String,
    override val setId: String,
    val weightKg: Double? = null,
    val reps: Int? = null,
) : SetCommand

/** Workout starten; [planId] null startet ein freies Workout. */
@Serializable
@SerialName("start_workout")
data class StartWorkout(
    override val commandId: String,
    override val issuedAt: Long,
    val planId: String? = null,
) : WearCommand

/** Laufende Pause um [deltaSeconds] verlängern (positiv) oder verkürzen (negativ). */
@Serializable
@SerialName("adjust_rest")
data class AdjustRest(
    override val commandId: String,
    override val issuedAt: Long,
    override val sessionId: String,
    val deltaSeconds: Int,
) : SessionCommand

@Serializable
@SerialName("skip_rest")
data class SkipRest(
    override val commandId: String,
    override val issuedAt: Long,
    override val sessionId: String,
) : SessionCommand

/** Workout beenden; Zusammenfassung, PR-Erkennung und Export laufen am Telefon. */
@Serializable
@SerialName("finish")
data class Finish(
    override val commandId: String,
    override val issuedAt: Long,
    override val sessionId: String,
) : SessionCommand

@Serializable
@SerialName("discard")
data class Discard(
    override val commandId: String,
    override val issuedAt: Long,
    override val sessionId: String,
) : SessionCommand

/**
 * Puls-Zusammenfassung der Uhr für die beendete Session [sessionId]: Durchschnitt und Maximum in
 * Schlägen pro Minute. Wird erst nach dem Workout gesendet (kein Live-Puls ans Telefon) und braucht
 * keine laufende Session am Telefon, deshalb kein [SessionCommand].
 */
@Serializable
@SerialName("heart_rate_summary")
data class HeartRateSummary(
    override val commandId: String,
    override val issuedAt: Long,
    val sessionId: String,
    val averageBpm: Int,
    val maxBpm: Int,
    val sampleCount: Int,
) : WearCommand {
    /** Werte in einem glaubwürdigen Bereich, Durchschnitt nicht über dem Maximum. */
    val isPlausible: Boolean
        get() = averageBpm in HeartRateLimits.MIN_BPM..HeartRateLimits.MAX_BPM &&
            maxBpm in averageBpm..HeartRateLimits.MAX_BPM &&
            sampleCount > 0
}

/** Gültiger Bereich für einzelne Pulswerte (Schläge pro Minute); alles außerhalb gilt als Messfehler. */
object HeartRateLimits {
    const val MIN_BPM = 30
    const val MAX_BPM = 250
}

/** Hülle mit Protokollversion, so wie ein Kommando auf dem Draht liegt. */
@Serializable
data class CommandEnvelope(
    val protocolVersion: Int = WearProtocol.VERSION,
    val command: WearCommand,
)

/** Fabrik mit frischer UUID (Version 7) und aktueller Zeit; für Tests sind beide überschreibbar. */
object WearCommands {
    fun completeSet(sessionId: String, setId: String, completed: Boolean = true, now: Long = System.currentTimeMillis()) =
        CompleteSet(Ids.newId(now), now, sessionId, setId, completed)

    fun updateSet(sessionId: String, setId: String, weightKg: Double? = null, reps: Int? = null, now: Long = System.currentTimeMillis()) =
        UpdateSet(Ids.newId(now), now, sessionId, setId, weightKg, reps)

    fun startWorkout(planId: String?, now: Long = System.currentTimeMillis()) =
        StartWorkout(Ids.newId(now), now, planId)

    fun adjustRest(sessionId: String, deltaSeconds: Int, now: Long = System.currentTimeMillis()) =
        AdjustRest(Ids.newId(now), now, sessionId, deltaSeconds)

    fun skipRest(sessionId: String, now: Long = System.currentTimeMillis()) =
        SkipRest(Ids.newId(now), now, sessionId)

    fun finish(sessionId: String, now: Long = System.currentTimeMillis()) =
        Finish(Ids.newId(now), now, sessionId)

    fun discard(sessionId: String, now: Long = System.currentTimeMillis()) =
        Discard(Ids.newId(now), now, sessionId)

    fun heartRateSummary(
        sessionId: String,
        averageBpm: Int,
        maxBpm: Int,
        sampleCount: Int,
        now: Long = System.currentTimeMillis(),
    ) = HeartRateSummary(Ids.newId(now), now, sessionId, averageBpm, maxBpm, sampleCount)
}
