package app.gympulse.core.wearprotocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * DTOs, die das Telefon unter WearPaths.ACTIVE_WORKOUT veröffentlicht. Zeitpunkte sind
 * Unix-Zeit in Millisekunden, Gewichte immer kg. Neue Felder bekommen einen Default, damit
 * ältere Gegenstellen sie ignorieren können (Json mit ignoreUnknownKeys).
 */

/**
 * Stand des laufenden Workouts aus Sicht des Telefons (Source of Truth).
 *
 * @property version monoton steigend über alle Sessions hinweg; die Uhr verwirft Snapshots,
 *   deren Version nicht größer ist als die zuletzt gesehene.
 * @property session null, wenn gerade kein Workout läuft.
 * @property acks Quittungen für verarbeitete Kommandos (angewendet oder ignoriert). Das Telefon
 *   hält nur die letzten [WearProtocol.MAX_ACKS] Einträge.
 */
@Serializable
data class WorkoutSnapshot(
    val protocolVersion: Int = WearProtocol.VERSION,
    val version: Long,
    val session: SessionSnapshot?,
    val acks: List<CommandAck> = emptyList(),
) {
    val acknowledgedCommandIds: Set<String> get() = acks.mapTo(LinkedHashSet()) { it.commandId }
}

@Serializable
data class SessionSnapshot(
    val sessionId: String,
    val planId: String? = null,
    /** Planname zum Startzeitpunkt; null bei einem freien Workout. */
    val title: String? = null,
    val startedAt: Long,
    /** Ende der laufenden Satzpause; null, wenn keine Pause läuft. */
    val restEndsAt: Long? = null,
    val exercises: List<ExerciseSnapshot> = emptyList(),
)

@Serializable
data class ExerciseSnapshot(
    val sessionExerciseId: String,
    val exerciseId: String,
    /** Name in der Gerätesprache des Telefons. */
    val name: String,
    val trackingType: WearTrackingType = WearTrackingType.WEIGHT_REPS,
    /** Körpergewichtsübung: [SetSnapshot.weightKg] ist nur das Zusatzgewicht. */
    val isBodyweight: Boolean = false,
    val supersetGroup: Int? = null,
    /** Pause nach einem Satz, wie sie das Telefon beim Abhaken starten würde. */
    val restSeconds: Int? = null,
    val sets: List<SetSnapshot> = emptyList(),
)

@Serializable
data class SetSnapshot(
    val setId: String,
    /** 1-basierte Nummer innerhalb der Aufwärm- bzw. Arbeitssätze. */
    val number: Int,
    val setType: WearSetType = WearSetType.WORKING,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    /** Letzte Änderung des Satzes am Telefon (`updated_at`); Basis der Konfliktauflösung. */
    val updatedAt: Long,
    /**
     * Optional genauere Zeitstempel je Feld. Fehlt ein Wert, gilt [updatedAt]. So kann das Telefon
     * melden, dass es z. B. nur das Gewicht später geändert hat und die Wiederholungen der Uhr bleiben.
     */
    val fieldUpdatedAt: SetFieldTimes? = null,
    /** Passender Satz der letzten abgeschlossenen Session („Vorher“). */
    val previous: PreviousSet? = null,
) {
    fun weightUpdatedAt(): Long = fieldUpdatedAt?.weightKg ?: updatedAt
    fun repsUpdatedAt(): Long = fieldUpdatedAt?.reps ?: updatedAt
    fun completedUpdatedAt(): Long = fieldUpdatedAt?.completed ?: updatedAt
}

@Serializable
data class SetFieldTimes(
    val weightKg: Long? = null,
    val reps: Long? = null,
    val completed: Long? = null,
)

@Serializable
data class PreviousSet(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
)

@Serializable
data class CommandAck(
    val commandId: String,
    val result: AckResult = AckResult.APPLIED,
)

@Serializable
enum class AckResult {
    @SerialName("applied")
    APPLIED,

    /** Die Session ist schon beendet oder verworfen. */
    @SerialName("session_ended")
    SESSION_ENDED,

    /** Das Kommando betrifft eine andere als die laufende Session. */
    @SerialName("session_mismatch")
    SESSION_MISMATCH,

    /** Satz oder Plan existiert nicht (mehr), oder die Werte sind ungültig. */
    @SerialName("invalid")
    INVALID,
}

/** Eigene Wire-Enums, damit Umbenennungen in core:model das Protokoll nicht brechen. */
@Serializable
enum class WearTrackingType {
    @SerialName("weight_reps")
    WEIGHT_REPS,

    @SerialName("reps")
    REPS,

    @SerialName("duration")
    DURATION,

    @SerialName("distance")
    DISTANCE,
}

@Serializable
enum class WearSetType {
    @SerialName("warmup")
    WARMUP,

    @SerialName("working")
    WORKING,

    @SerialName("drop")
    DROP,

    @SerialName("failure")
    FAILURE,
}

/** Pläne für die Auswahl auf der Uhr, veröffentlicht unter [WearPaths.PLANS_SUMMARY]. */
@Serializable
data class PlanSummaryList(
    val protocolVersion: Int = WearProtocol.VERSION,
    val generatedAt: Long,
    val plans: List<PlanSummary> = emptyList(),
)

@Serializable
data class PlanSummary(
    val planId: String,
    val name: String,
    val exerciseCount: Int,
    /** ARGB-Farbe des Plans, falls gesetzt. */
    val color: Long? = null,
)
