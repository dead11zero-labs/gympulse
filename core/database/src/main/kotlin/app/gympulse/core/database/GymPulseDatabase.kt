package app.gympulse.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import app.gympulse.core.database.dao.BodyDao
import app.gympulse.core.database.dao.EquipmentDao
import app.gympulse.core.database.dao.ExerciseDao
import app.gympulse.core.database.dao.PersonalRecordDao
import app.gympulse.core.database.dao.PlanDao
import app.gympulse.core.database.dao.ScheduleDao
import app.gympulse.core.database.dao.SessionDao
import app.gympulse.core.database.entity.BodyMeasurementEntity
import app.gympulse.core.database.entity.EquipmentEntity
import app.gympulse.core.database.entity.EquipmentSettingEntity
import app.gympulse.core.database.entity.ExerciseEntity
import app.gympulse.core.database.entity.ExerciseMuscleEntity
import app.gympulse.core.database.entity.ExerciseTranslationEntity
import app.gympulse.core.database.entity.PersonalRecordEntity
import app.gympulse.core.database.entity.PlanExerciseEntity
import app.gympulse.core.database.entity.ProgressPhotoEntity
import app.gympulse.core.database.entity.ScheduleOverrideEntity
import app.gympulse.core.database.entity.ScheduleRuleEntity
import app.gympulse.core.database.entity.SessionExerciseEntity
import app.gympulse.core.database.entity.SetEntryEntity
import app.gympulse.core.database.entity.UserProfileEntity
import app.gympulse.core.database.entity.WorkoutPlanEntity
import app.gympulse.core.database.entity.WorkoutSessionEntity

/**
 * Lokale Datenbank, einzige Datenquelle der App (offline-first).
 *
 * Migrationen: Ab der ersten veröffentlichten Version wird jede Schemaänderung als
 * `@AutoMigration` oder manuelle `Migration` hinterlegt; destruktive Migrationen sind tabu,
 * weil Nutzerdaten sonst verloren gehen. Die exportierten Schemas liegen in `schemas/`.
 */
@Database(
    entities = [
        UserProfileEntity::class,
        BodyMeasurementEntity::class,
        ProgressPhotoEntity::class,
        EquipmentEntity::class,
        EquipmentSettingEntity::class,
        ExerciseEntity::class,
        ExerciseTranslationEntity::class,
        ExerciseMuscleEntity::class,
        WorkoutPlanEntity::class,
        PlanExerciseEntity::class,
        ScheduleRuleEntity::class,
        ScheduleOverrideEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SetEntryEntity::class,
        PersonalRecordEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class GymPulseDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun equipmentDao(): EquipmentDao
    abstract fun planDao(): PlanDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun sessionDao(): SessionDao
    abstract fun bodyDao(): BodyDao
    abstract fun personalRecordDao(): PersonalRecordDao

    companion object {
        const val NAME = "gympulse.db"
    }
}
