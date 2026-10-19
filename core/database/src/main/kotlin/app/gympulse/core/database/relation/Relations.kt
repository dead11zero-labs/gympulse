package app.gympulse.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import app.gympulse.core.database.entity.EquipmentEntity
import app.gympulse.core.database.entity.EquipmentSettingEntity
import app.gympulse.core.database.entity.ExerciseEntity
import app.gympulse.core.database.entity.ExerciseMuscleEntity
import app.gympulse.core.database.entity.ExerciseTranslationEntity
import app.gympulse.core.database.entity.PlanExerciseEntity
import app.gympulse.core.database.entity.SessionExerciseEntity
import app.gympulse.core.database.entity.SetEntryEntity
import app.gympulse.core.database.entity.WorkoutPlanEntity
import app.gympulse.core.database.entity.WorkoutSessionEntity

// Hinweis: @Relation filtert keine Soft-Deletes. Die Mapper in core:data
// verwerfen Kindzeilen mit gesetztem deletedAt.

data class ExerciseWithDetails(
    @Embedded val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "exercise_id")
    val translations: List<ExerciseTranslationEntity>,
    @Relation(parentColumn = "id", entityColumn = "exercise_id")
    val muscles: List<ExerciseMuscleEntity>,
)

data class EquipmentWithSettings(
    @Embedded val equipment: EquipmentEntity,
    @Relation(parentColumn = "id", entityColumn = "equipment_id")
    val settings: List<EquipmentSettingEntity>,
)

data class PlanWithExercises(
    @Embedded val plan: WorkoutPlanEntity,
    @Relation(parentColumn = "id", entityColumn = "plan_id")
    val exercises: List<PlanExerciseEntity>,
)

data class SessionExerciseWithSets(
    @Embedded val sessionExercise: SessionExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "session_exercise_id")
    val sets: List<SetEntryEntity>,
)

data class SessionWithExercises(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(
        entity = SessionExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "session_id",
    )
    val exercises: List<SessionExerciseWithSets>,
)

/** Ergebnis der Volumen-Abfrage pro Session. */
data class SessionVolumeRow(
    val sessionId: String,
    val endedAt: Long,
    val volumeKg: Double,
)
