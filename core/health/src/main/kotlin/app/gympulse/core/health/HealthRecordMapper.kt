package app.gympulse.core.health

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import app.gympulse.core.data.health.HealthExerciseType
import app.gympulse.core.data.health.HealthReadType
import app.gympulse.core.data.health.HealthSleep
import app.gympulse.core.data.health.HealthWeight
import app.gympulse.core.data.health.HealthWorkout
import kotlin.reflect.KClass

/** Übersetzt die plattformneutralen Health-Modelle in Records von Health Connect und zurück. */
internal object HealthRecordMapper {

    fun toExerciseSessionRecord(workout: HealthWorkout): ExerciseSessionRecord = ExerciseSessionRecord(
        startTime = workout.startTime,
        startZoneOffset = workout.startZoneOffset,
        endTime = workout.endTime,
        endZoneOffset = workout.endZoneOffset,
        metadata = Metadata.manualEntry(
            clientRecordId = workout.clientRecordId,
            clientRecordVersion = workout.clientRecordVersion,
        ),
        exerciseType = exerciseType(workout.exerciseType),
        title = workout.title,
        notes = workout.notes,
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
