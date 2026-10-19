package app.gympulse.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.gympulse.core.model.MeasurementSource
import app.gympulse.core.model.MeasurementType
import app.gympulse.core.model.PersonalRecordType
import app.gympulse.core.model.PhotoPose
import app.gympulse.core.model.ScheduleOverrideType
import app.gympulse.core.model.SessionStatus
import app.gympulse.core.model.SetType
import app.gympulse.core.model.TrackingType

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey override val id: String,
    val name: String?,
    @ColumnInfo(name = "birth_year") val birthYear: Int?,
    @ColumnInfo(name = "height_cm") val heightCm: Double?,
    val sex: String?,
    @ColumnInfo(name = "created_at") override val createdAt: Long,
    @ColumnInfo(name = "updated_at") override val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") override val deletedAt: Long? = null,
) : SyncEntity

@Entity(
    tableName = "body_measurement",
    indices = [Index(value = ["type", "measured_at"])],
)
data class BodyMeasurementEntity(
    @PrimaryKey override val id: String,
    @ColumnInfo(name = "measured_at") val measuredAt: Long,
    val type: MeasurementType,
    val value: Double,
    val source: MeasurementSource,
    @ColumnInfo(name = "hc_record_id") val healthConnectRecordId: String? = null,
    @ColumnInfo(name = "created_at") override val createdAt: Long,
    @ColumnInfo(name = "updated_at") override val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") override val deletedAt: Long? = null,
) : SyncEntity

/** Fortschrittsfotos bleiben lokal und werden nicht synchronisiert. */
@Entity(tableName = "progress_photo", indices = [Index("taken_at")])
data class ProgressPhotoEntity(
    @PrimaryKey override val id: String,
    @ColumnInfo(name = "taken_at") val takenAt: Long,
    @ColumnInfo(name = "local_uri") val localUri: String,
    val pose: PhotoPose,
    val note: String?,
    @ColumnInfo(name = "created_at") override val createdAt: Long,
    @ColumnInfo(name = "updated_at") override val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") override val deletedAt: Long? = null,
) : SyncEntity

@Entity(tableName = "equipment")
data class EquipmentEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val location: String?,
    val notes: String?,
    @ColumnInfo(name = "template_key") val templateKey: String?,
    @ColumnInfo(name = "created_at") override val createdAt: Long,
    @ColumnInfo(name = "updated_at") override val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") override val deletedAt: Long? = null,
) : SyncEntity

@Entity(
    tableName = "equipment_setting",
    foreignKeys = [
        ForeignKey(
            entity = EquipmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["equipment_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("equipment_id")],
)
data class EquipmentSettingEntity(
    @PrimaryKey override val id: String,
    @ColumnInfo(name = "equipment_id") val equipmentId: String,
    val key: String,
    val value: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") override val createdAt: Long,
    @ColumnInfo(name = "updated_at") override val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") override val deletedAt: Long? = null,
) : SyncEntity

@Entity(
    tableName = "exercise",
    foreignKeys = [
        ForeignKey(
            entity = EquipmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["equipment_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["slug"], unique = true), Index("equipment_id")],
)
data class ExerciseEntity(
    @PrimaryKey override val id: String,
    /** Stabiler Schlüssel aus den Seed-Daten; null bei eigenen Übungen. */
    val slug: String?,
    val category: String,
    @ColumnInfo(name = "equipment_type") val equipmentType: String,
    @ColumnInfo(name = "tracking_type") val trackingType: TrackingType,
    @ColumnInfo(name = "bodyweight_factor") val bodyweightFactor: Double?,
    /** Bild-URLs, durch Zeilenumbruch getrennt. */
    @ColumnInfo(name = "image_urls") val imageUrls: String?,
    val notes: String?,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "default_rest_s") val defaultRestSeconds: Int?,
    @ColumnInfo(name = "equipment_id") val equipmentId: String?,
    val source: String?,
    val license: String?,
    @ColumnInfo(name = "created_at") override val createdAt: Long,
    @ColumnInfo(name = "updated_at") override val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") override val deletedAt: Long? = null,
) : SyncEntity

/** Übersetzter Name und Anleitung je Sprache (z. B. "en", "de"). */
@Entity(
    tableName = "exercise_translation",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["exercise_id", "locale"], unique = true), Index("name")],
)
data class ExerciseTranslationEntity(
    @PrimaryKey override val id: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val locale: String,
    val name: String,
    /** Anleitungsschritte, durch Zeilenumbruch getrennt. */
    val instructions: String?,
    @ColumnInfo(name = "created_at") override val createdAt: Long,
    @ColumnInfo(name = "updated_at") override val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") override val deletedAt: Long? = null,
) : SyncEntity

@Entity(
    tableName = "exercise_muscle",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["exercise_id", "muscle"], unique = true), Index("muscle")],
)
data class ExerciseMuscleEntity(
    @PrimaryKey override val id: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    /** Key aus [app.gympulse.core.model.MuscleGroup]. */
    val muscle: String,
    @ColumnInfo(name = "is_primary") val isPrimary: Boolean,
    @ColumnInfo(name = "created_at") override val createdAt: Long,
    @ColumnInfo(name = "updated_at") override val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") override val deletedAt: Long? = null,
) : SyncEntity

@Entity(tableName = "workout_plan")
data class WorkoutPlanEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val description: String?,
    val color: Long?,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") override val createdAt: Long,
    @ColumnInfo(name = "updated_at") override val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") override val deletedAt: Long? = null,
) : SyncEntity

@Entity(
    tableName = "plan_exercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("plan_id"), Index("exercise_id")],
)
data class PlanExerciseEntity(
    @PrimaryKey override val id: String,
    @ColumnInfo(name = "plan_id") val planId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "superset_group") val supersetGroup: Int?,
    @ColumnInfo(name = "target_sets") val targetSets: Int,
    @ColumnInfo(name = "target_reps_min") val targetRepsMin: Int?,
    @ColumnInfo(name = "target_reps_max") val targetRepsMax: Int?,
    @ColumnInfo(name = "target_weight_kg") val targetWeightKg: Double?,
    @ColumnInfo(name = "rest_s") val restSeconds: Int?,
    @ColumnInfo(name = "created_at") override val createdAt: Long,
    @ColumnInfo(name = "updated_at") override val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") override val deletedAt: Long? = null,
) : SyncEntity

/** Wochenschema: Plan an einem ISO-Wochentag (1 = Montag). */
@Entity(
    tableName = "schedule_rule",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("plan_id"), Index("weekday")],
)
data class ScheduleRuleEntity(
    @PrimaryKey override val id: String,
    @ColumnInfo(name = "plan_id") val planId: String,
    val weekday: Int,
    /** ISO-Datum (yyyy-MM-dd). */
    @ColumnInfo(name = "valid_from") val validFrom: String,
    @ColumnInfo(name = "valid_until") val validUntil: String?,
    @ColumnInfo(name = "created_at") override val createdAt: Long,
    @ColumnInfo(name = "updated_at") override val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") override val deletedAt: Long? = null,
) : SyncEntity

/** Ausnahme vom Wochenschema an einem bestimmten Tag. */
@Entity(
    tableName = "schedule_override",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("date"), Index("plan_id")],
)
data class ScheduleOverrideEntity(
    @PrimaryKey override val id: String,
    /** ISO-Datum (yyyy-MM-dd). */
    val date: String,
    @ColumnInfo(name = "plan_id") val planId: String?,
    val type: ScheduleOverrideType,
    @ColumnInfo(name = "created_at") override val createdAt: Long,
    @ColumnInfo(name = "updated_at") override val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") override val deletedAt: Long? = null,
) : SyncEntity

@Entity(
    tableName = "workout_session",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("plan_id"), Index("status"), Index("ended_at")],
)
data class WorkoutSessionEntity(
    @PrimaryKey override val id: String,
    @ColumnInfo(name = "plan_id") val planId: String?,
    /** Planname zum Zeitpunkt des Trainings, damit die Historie lesbar bleibt. */
    val title: String?,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    val status: SessionStatus,
    /** Körpergewicht zu Beginn der Session, Basis für das Volumen bei Körpergewichtsübungen. */
    @ColumnInfo(name = "bodyweight_kg") val bodyweightKg: Double?,
    val rating: Int?,
    val notes: String?,
    /** Cache für Listen; die Wahrheit sind die Sätze. */
    @ColumnInfo(name = "total_volume_kg") val totalVolumeKg: Double?,
    @ColumnInfo(name = "hc_record_id") val healthConnectRecordId: String? = null,
    @ColumnInfo(name = "created_at") override val createdAt: Long,
    @ColumnInfo(name = "updated_at") override val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") override val deletedAt: Long? = null,
) : SyncEntity

@Entity(
    tableName = "session_exercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("session_id"), Index("exercise_id")],
)
data class SessionExerciseEntity(
    @PrimaryKey override val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "superset_group") val supersetGroup: Int?,
    val notes: String?,
    /** Geräteeinstellungen als JSON-Objekt, Momentaufnahme beim Training. */
    @ColumnInfo(name = "equipment_settings_snapshot") val equipmentSettingsSnapshot: String?,
    @ColumnInfo(name = "created_at") override val createdAt: Long,
    @ColumnInfo(name = "updated_at") override val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") override val deletedAt: Long? = null,
) : SyncEntity

@Entity(
    tableName = "set_entry",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("session_exercise_id")],
)
data class SetEntryEntity(
    @PrimaryKey override val id: String,
    @ColumnInfo(name = "session_exercise_id") val sessionExerciseId: String,
    @ColumnInfo(name = "set_index") val setIndex: Int,
    @ColumnInfo(name = "set_type") val setType: SetType,
    @ColumnInfo(name = "weight_kg") val weightKg: Double?,
    val reps: Int?,
    @ColumnInfo(name = "duration_s") val durationSeconds: Int?,
    @ColumnInfo(name = "distance_m") val distanceMeters: Double?,
    val rpe: Double?,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "created_at") override val createdAt: Long,
    @ColumnInfo(name = "updated_at") override val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") override val deletedAt: Long? = null,
) : SyncEntity

@Entity(
    tableName = "personal_record",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["exercise_id", "type"])],
)
data class PersonalRecordEntity(
    @PrimaryKey override val id: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val type: PersonalRecordType,
    val value: Double,
    @ColumnInfo(name = "set_entry_id") val setEntryId: String?,
    @ColumnInfo(name = "achieved_at") val achievedAt: Long,
    @ColumnInfo(name = "created_at") override val createdAt: Long,
    @ColumnInfo(name = "updated_at") override val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") override val deletedAt: Long? = null,
) : SyncEntity
