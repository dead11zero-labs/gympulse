package app.gympulse.core.data.health

import java.time.Instant
import java.time.ZoneOffset

/** Ob Health Connect auf diesem Gerät nutzbar ist. */
enum class HealthAvailability {
    /** Client verfügbar, Berechtigungen können angefragt werden. */
    AVAILABLE,

    /** Android 9 bis 13 ohne installierte Health-Connect-App. */
    NOT_INSTALLED,

    /** Health Connect ist installiert, aber zu alt. */
    UPDATE_REQUIRED,

    /** Gerät bzw. Profil unterstützt Health Connect nicht. */
    UNSUPPORTED,
}

/** Trainingsart in Health Connect; die Implementierung bildet sie auf die Record-Konstanten ab. */
enum class HealthExerciseType { STRENGTH_TRAINING, WEIGHTLIFTING, CALISTHENICS, STRETCHING, OTHER_WORKOUT }

/**
 * Plattformneutrale Abbildung einer abgeschlossenen Session als Health-Connect-Trainingseinheit.
 *
 * [clientRecordId] ist die Session-ID; zusammen mit [clientRecordVersion] überschreibt ein erneuter
 * Export denselben Eintrag, statt ein Duplikat anzulegen.
 */
data class HealthWorkout(
    val clientRecordId: String,
    val clientRecordVersion: Long,
    val title: String?,
    val notes: String?,
    val startTime: Instant,
    val startZoneOffset: ZoneOffset?,
    val endTime: Instant,
    val endZoneOffset: ZoneOffset?,
    val exerciseType: HealthExerciseType,
)

/** Ein Körpergewicht aus Health Connect, immer in kg. */
data class HealthWeight(
    val measuredAt: Instant,
    val weightKg: Double,
)
