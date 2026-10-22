package app.gympulse.core.health

import android.os.Build
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import app.gympulse.core.data.health.HealthExerciseType
import app.gympulse.core.data.health.HealthReadType
import app.gympulse.core.data.health.HealthSegmentType
import app.gympulse.core.data.health.HealthSleep
import app.gympulse.core.data.health.HealthWeight
import app.gympulse.core.data.health.HealthWorkout
import kotlin.reflect.KClass

/** Übersetzt die plattformneutralen Health-Modelle in Records von Health Connect und zurück. */
internal object HealthRecordMapper {

    private val phoneDevice: Device
        get() = Device(
            type = Device.TYPE_PHONE,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
        )

    /**
     * Trainingseinheit mit Abschnitten je Übung. Abschnitte, deren Typ Health Connect in dieser
     * Trainingsart nicht zulässt (z. B. Laufband im Krafttraining), werden zu
     * `EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT`; ungültige Zeitfenster werden verworfen. Lehnt Health
     * Connect die Abschnitte trotzdem ab, entsteht die Einheit ohne Abschnitte.
     */
    fun toExerciseSessionRecord(workout: HealthWorkout): ExerciseSessionRecord {
        val exerciseType = exerciseType(workout.exerciseType)
        val segments = try {
            exerciseSegments(workout, exerciseType)
        } catch (e: RuntimeException) {
            emptyList()
        }
        if (segments.isNotEmpty()) {
            try {
                return sessionRecord(workout, exerciseType, segments)
            } catch (e: RuntimeException) {
                // Fällt auf die Einheit ohne Abschnitte zurück.
            }
        }
        return sessionRecord(workout, exerciseType, emptyList())
    }

    /** Dieselbe Einheit ohne Abschnitte, z. B. wenn Health Connect die Abschnitte beim Schreiben ablehnt. */
    fun toExerciseSessionRecordWithoutSegments(workout: HealthWorkout): ExerciseSessionRecord =
        sessionRecord(workout, exerciseType(workout.exerciseType), emptyList())

    private fun sessionRecord(
        workout: HealthWorkout,
        exerciseType: Int,
        segments: List<ExerciseSegment>,
    ): ExerciseSessionRecord = ExerciseSessionRecord(
        startTime = workout.startTime,
        startZoneOffset = workout.startZoneOffset,
        endTime = workout.endTime,
        endZoneOffset = workout.endZoneOffset,
        // Samsung Health übernimmt manuell eingetragene Einheiten anderer Apps offenbar nicht;
        // GymPulse zeichnet das Training live auf dem Telefon auf.
        metadata = Metadata.activelyRecorded(
            device = phoneDevice,
            clientRecordId = workout.clientRecordId,
            clientRecordVersion = workout.clientRecordVersion,
        ),
        exerciseType = exerciseType,
        title = workout.title,
        notes = workout.notes,
        segments = segments,
    )

    /**
     * Gültige, zeitlich sortierte Abschnitte innerhalb der Session: ohne leere Zeitfenster, ohne
     * Überlappung (überlappende werden verworfen) und nur mit Typen, die zu [exerciseType] passen.
     */
    fun exerciseSegments(workout: HealthWorkout, exerciseType: Int): List<ExerciseSegment> {
        var cursor = workout.startTime
        return workout.segments
            .sortedBy { it.startTime }
            .filter { segment ->
                val valid = segment.startTime.isBefore(segment.endTime) &&
                    !segment.startTime.isBefore(cursor) &&
                    !segment.endTime.isAfter(workout.endTime)
                if (valid) cursor = segment.endTime
                valid
            }
            .map { segment ->
                val type = segmentType(segment.type).takeIf { isCompatible(it, exerciseType) }
                    ?: ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT
                ExerciseSegment(
                    startTime = segment.startTime,
                    endTime = segment.endTime,
                    segmentType = type,
                    repetitions = segment.repetitions.coerceAtLeast(0),
                )
            }
    }

    fun segmentType(type: HealthSegmentType): Int = when (type) {
        HealthSegmentType.UNKNOWN -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_UNKNOWN
        HealthSegmentType.OTHER_WORKOUT -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT
        HealthSegmentType.WEIGHTLIFTING -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING
        HealthSegmentType.STRETCHING -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_STRETCHING
        HealthSegmentType.ARM_CURL -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL
        HealthSegmentType.BACK_EXTENSION -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_BACK_EXTENSION
        HealthSegmentType.BALL_SLAM -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_BALL_SLAM
        HealthSegmentType.BARBELL_SHOULDER_PRESS -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_BARBELL_SHOULDER_PRESS
        HealthSegmentType.BENCH_PRESS -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS
        HealthSegmentType.BENCH_SIT_UP -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_SIT_UP
        HealthSegmentType.BURPEE -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_BURPEE
        HealthSegmentType.CRUNCH -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH
        HealthSegmentType.DEADLIFT -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT
        HealthSegmentType.DOUBLE_ARM_TRICEPS_EXTENSION ->
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_DOUBLE_ARM_TRICEPS_EXTENSION
        HealthSegmentType.DUMBBELL_FRONT_RAISE -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_FRONT_RAISE
        HealthSegmentType.DUMBBELL_LATERAL_RAISE -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_LATERAL_RAISE
        HealthSegmentType.DUMBBELL_ROW -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_ROW
        HealthSegmentType.DUMBBELL_TRICEPS_EXTENSION_TWO_ARM ->
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_TRICEPS_EXTENSION_TWO_ARM
        HealthSegmentType.FRONT_RAISE -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_FRONT_RAISE
        HealthSegmentType.HIP_THRUST -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIP_THRUST
        HealthSegmentType.HULA_HOOP -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_HULA_HOOP
        HealthSegmentType.JUMPING_JACK -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_JUMPING_JACK
        HealthSegmentType.JUMP_ROPE -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_JUMP_ROPE
        HealthSegmentType.KETTLEBELL_SWING -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_KETTLEBELL_SWING
        HealthSegmentType.LATERAL_RAISE -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LATERAL_RAISE
        HealthSegmentType.LAT_PULL_DOWN -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LAT_PULL_DOWN
        HealthSegmentType.LEG_CURL -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_CURL
        HealthSegmentType.LEG_EXTENSION -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_EXTENSION
        HealthSegmentType.LEG_PRESS -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_PRESS
        HealthSegmentType.LEG_RAISE -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_RAISE
        HealthSegmentType.LUNGE -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE
        HealthSegmentType.MOUNTAIN_CLIMBER -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_MOUNTAIN_CLIMBER
        HealthSegmentType.PLANK -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK
        HealthSegmentType.PULL_UP -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP
        HealthSegmentType.SHOULDER_PRESS -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS
        HealthSegmentType.SINGLE_ARM_TRICEPS_EXTENSION ->
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_SINGLE_ARM_TRICEPS_EXTENSION
        HealthSegmentType.SIT_UP -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_SIT_UP
        HealthSegmentType.SQUAT -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT
        HealthSegmentType.UPPER_TWIST -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_UPPER_TWIST
        HealthSegmentType.BIKING -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_BIKING
        HealthSegmentType.BIKING_STATIONARY -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_BIKING_STATIONARY
        HealthSegmentType.ELLIPTICAL -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_ELLIPTICAL
        HealthSegmentType.ROWING_MACHINE -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_ROWING_MACHINE
        HealthSegmentType.RUNNING -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING
        HealthSegmentType.RUNNING_TREADMILL -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING_TREADMILL
        HealthSegmentType.STAIR_CLIMBING_MACHINE -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_STAIR_CLIMBING_MACHINE
        HealthSegmentType.WALKING -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_WALKING
    }

    /**
     * Ob Health Connect den Abschnittstyp in der Trainingsart zulässt. Nachbau der internen Regel
     * `ExerciseSegment.isSegmentTypeCompatibleWithSessionType` (in connect-client 1.1.0 nicht
     * öffentlich), beschränkt auf die Trainingsarten, die GymPulse schreibt.
     */
    fun isCompatible(segmentType: Int, exerciseType: Int): Boolean = when {
        exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT -> true
        segmentType in UNIVERSAL_SEGMENTS -> true
        exerciseType in STRENGTH_SESSION_TYPES -> segmentType in STRENGTH_SEGMENTS
        else -> false
    }

    private val UNIVERSAL_SEGMENTS = setOf(
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_PAUSE,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_STRETCHING,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_UNKNOWN,
    )

    private val STRENGTH_SESSION_TYPES = setOf(
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
        ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS,
    )

    private val STRENGTH_SEGMENTS = setOf(
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_BACK_EXTENSION,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_BALL_SLAM,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_BARBELL_SHOULDER_PRESS,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_SIT_UP,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_BURPEE,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_DOUBLE_ARM_TRICEPS_EXTENSION,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_CURL_LEFT_ARM,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_CURL_RIGHT_ARM,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_FRONT_RAISE,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_LATERAL_RAISE,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_ROW,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_TRICEPS_EXTENSION_LEFT_ARM,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_TRICEPS_EXTENSION_RIGHT_ARM,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_TRICEPS_EXTENSION_TWO_ARM,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_FORWARD_TWIST,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_FRONT_RAISE,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIP_THRUST,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_HULA_HOOP,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_JUMP_ROPE,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_JUMPING_JACK,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_KETTLEBELL_SWING,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_LATERAL_RAISE,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_LAT_PULL_DOWN,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_CURL,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_EXTENSION,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_PRESS,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_RAISE,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_MOUNTAIN_CLIMBER,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_PUNCH,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_SINGLE_ARM_TRICEPS_EXTENSION,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_SIT_UP,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_UPPER_TWIST,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING,
    )

    fun exerciseType(type: HealthExerciseType): Int = when (type) {
        HealthExerciseType.STRENGTH_TRAINING -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        HealthExerciseType.WEIGHTLIFTING -> ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING
        HealthExerciseType.CALISTHENICS -> ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS
        HealthExerciseType.STRETCHING -> ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING
        HealthExerciseType.OTHER_WORKOUT -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
    }

    fun toHealthWeight(record: WeightRecord): HealthWeight =
        HealthWeight(measuredAt = record.time, weightKg = record.weight.inKilograms)

    fun toHealthSleep(record: SleepSessionRecord): HealthSleep = HealthSleep(
        start = record.startTime,
        end = record.endTime,
    )

    /** Record-Klasse von Health Connect je lesbarem Datentyp. */
    fun recordType(type: HealthReadType): KClass<out Record> = when (type) {
        HealthReadType.WEIGHT -> WeightRecord::class
        HealthReadType.HEART_RATE -> HeartRateRecord::class
        HealthReadType.STEPS -> StepsRecord::class
        HealthReadType.ACTIVE_CALORIES -> ActiveCaloriesBurnedRecord::class
        HealthReadType.SLEEP -> SleepSessionRecord::class
    }
}
