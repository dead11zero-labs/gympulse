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

/** Datentypen, die GymPulse aus Health Connect lesen kann. */
enum class HealthReadType {
    WEIGHT,
    HEART_RATE,
    STEPS,
    ACTIVE_CALORIES,

    /** Nur mit eigenem Unterschalter ([app.gympulse.core.featuregate.Feature.HEALTH_SLEEP]). */
    SLEEP,
}

/**
 * Aggregierte Aktivitätswerte eines Zeitraums, aus allen Apps, die nach Health Connect schreiben
 * (z. B. Samsung Health oder Smartwatch-Apps). Felder sind null, wenn keine Daten vorliegen oder
 * die Berechtigung fehlt.
 */
data class HealthActivity(
    val averageHeartRateBpm: Long? = null,
    val maxHeartRateBpm: Long? = null,
    val steps: Long? = null,
    val activeCaloriesKcal: Double? = null,
)

/** Eine Schlafphase aus Health Connect. */
data class HealthSleep(
    val start: Instant,
    val end: Instant,
)

/**
 * Werte aus Health Connect im Zeitfenster eines Workouts, für die Zusammenfassung.
 *
 * @param sleepMinutes Dauer der letzten Schlafphase vor dem Workout, nur mit Schlaf-Unterschalter.
 */
data class WorkoutHealthMetrics(
    val averageHeartRateBpm: Long? = null,
    val maxHeartRateBpm: Long? = null,
    val steps: Long? = null,
    val activeCaloriesKcal: Double? = null,
    val sleepMinutes: Long? = null,
) {
    val isEmpty: Boolean
        get() = averageHeartRateBpm == null && maxHeartRateBpm == null && steps == null &&
            activeCaloriesKcal == null && sleepMinutes == null
}

/** Schritte für heute und die laufende Woche, oder warum es keine gibt. Wirft nie. */
sealed interface HealthSteps {
    /** Health Connect ist auf diesem Gerät nicht nutzbar. */
    data object Unavailable : HealthSteps

    /** Health Connect ist in GymPulse aus oder die Kernberechtigungen fehlen. */
    data object Disabled : HealthSteps

    /** Health Connect ist aktiv, aber die Lese-Berechtigung für Schritte fehlt. */
    data object PermissionMissing : HealthSteps

    /** Health Connect hat die Anfrage abgelehnt oder gedrosselt; später erneut versuchen. */
    data object Unreadable : HealthSteps

    /** Schritte seit Mitternacht ([today]) und seit Wochenbeginn ([week]), jeweils bis jetzt. */
    data class Available(val today: Long, val week: Long) : HealthSteps
}
