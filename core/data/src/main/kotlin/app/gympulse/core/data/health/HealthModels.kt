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
    /** Ein Abschnitt je Übung, zeitlich sortiert, ohne Überlappung und innerhalb von Start/Ende. */
    val segments: List<HealthWorkoutSegment> = emptyList(),
)

/**
 * Abschnitt einer Trainingseinheit, z. B. alle Sätze Bankdrücken.
 *
 * @param repetitions Summe der Wiederholungen aller abgeschlossenen Sätze, 0 ohne Wiederholungen.
 */
data class HealthWorkoutSegment(
    val startTime: Instant,
    val endTime: Instant,
    val type: HealthSegmentType,
    val repetitions: Int,
)

/**
 * Übungsart eines Abschnitts. Entspricht den `EXERCISE_SEGMENT_TYPE_*`-Konstanten von Health Connect
 * (connect-client 1.1.0); die Implementierung in `core:health` bildet sie ab. Liegestütze und Dips
 * kennt Health Connect nicht, sie landen bei [UNKNOWN].
 */
enum class HealthSegmentType {
    UNKNOWN,
    OTHER_WORKOUT,
    WEIGHTLIFTING,
    STRETCHING,
    ARM_CURL,
    BACK_EXTENSION,
    BALL_SLAM,
    BARBELL_SHOULDER_PRESS,
    BENCH_PRESS,
    BENCH_SIT_UP,
    BURPEE,
    CRUNCH,
    DEADLIFT,
    DOUBLE_ARM_TRICEPS_EXTENSION,
    DUMBBELL_FRONT_RAISE,
    DUMBBELL_LATERAL_RAISE,
    DUMBBELL_ROW,
    DUMBBELL_TRICEPS_EXTENSION_TWO_ARM,
    FRONT_RAISE,
    HIP_THRUST,
    HULA_HOOP,
    JUMPING_JACK,
    JUMP_ROPE,
    KETTLEBELL_SWING,
    LATERAL_RAISE,
    LAT_PULL_DOWN,
    LEG_CURL,
    LEG_EXTENSION,
    LEG_PRESS,
    LEG_RAISE,
    LUNGE,
    MOUNTAIN_CLIMBER,
    PLANK,
    PULL_UP,
    SHOULDER_PRESS,
    SINGLE_ARM_TRICEPS_EXTENSION,
    SIT_UP,
    SQUAT,
    UPPER_TWIST,

    // Ausdauer; in Krafttrainings-Sessions lässt Health Connect diese Typen nicht zu.
    BIKING,
    BIKING_STATIONARY,
    ELLIPTICAL,
    ROWING_MACHINE,
    RUNNING,
    RUNNING_TREADMILL,
    STAIR_CLIMBING_MACHINE,
    WALKING,
}

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
 */
data class WorkoutHealthMetrics(
    val averageHeartRateBpm: Long? = null,
    val maxHeartRateBpm: Long? = null,
    val steps: Long? = null,
    val activeCaloriesKcal: Double? = null,
) {
    val isEmpty: Boolean
        get() = averageHeartRateBpm == null && maxHeartRateBpm == null && steps == null &&
            activeCaloriesKcal == null
}

/** Schlafdauer der letzten Nacht, oder warum es keine gibt. Wirft nie. */
sealed interface HealthLastNightSleep {
    /** Health Connect ist auf diesem Gerät nicht nutzbar. */
    data object Unavailable : HealthLastNightSleep

    /** Health Connect oder der Schlaf-Unterschalter ist in GymPulse aus bzw. die Kernberechtigungen fehlen. */
    data object Disabled : HealthLastNightSleep

    /** Anbindung und Schlaf-Unterschalter sind aktiv, aber die Lese-Berechtigung für Schlaf fehlt. */
    data object PermissionMissing : HealthLastNightSleep

    /** Health Connect hat die Anfrage abgelehnt oder gedrosselt; später erneut versuchen. */
    data object Unreadable : HealthLastNightSleep

    /** Alles aktiv, aber für die letzte Nacht liegt keine Schlafphase vor. */
    data object NoData : HealthLastNightSleep

    /** Dauer der jüngsten Schlafphase der letzten 24 Stunden in Minuten (immer größer als 0). */
    data class Available(val minutes: Long) : HealthLastNightSleep
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
