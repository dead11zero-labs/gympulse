package app.gympulse.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.gympulse.core.database.entity.PlanExerciseEntity
import app.gympulse.core.database.entity.WorkoutPlanEntity
import app.gympulse.core.database.relation.PlanWithExercises
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {
    @Transaction
    @Query("SELECT * FROM workout_plan WHERE deleted_at IS NULL AND is_archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<PlanWithExercises>>

    @Transaction
    @Query("SELECT * FROM workout_plan WHERE id = :id AND deleted_at IS NULL")
    fun observeById(id: String): Flow<PlanWithExercises?>

    @Transaction
    @Query("SELECT * FROM workout_plan WHERE id = :id")
    suspend fun getById(id: String): PlanWithExercises?

    @Upsert
    suspend fun upsertPlan(plan: WorkoutPlanEntity)

    @Upsert
    suspend fun upsertPlanExercises(exercises: List<PlanExerciseEntity>)

    @Query("UPDATE plan_exercise SET deleted_at = :now, updated_at = :now WHERE plan_id = :planId AND id NOT IN (:keepIds)")
    suspend fun softDeletePlanExercisesExcept(planId: String, keepIds: List<String>, now: Long)

    @Transaction
    suspend fun savePlan(plan: WorkoutPlanEntity, exercises: List<PlanExerciseEntity>, now: Long) {
        upsertPlan(plan)
        upsertPlanExercises(exercises)
        softDeletePlanExercisesExcept(plan.id, exercises.map { it.id }, now)
    }

    @Query("UPDATE workout_plan SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
