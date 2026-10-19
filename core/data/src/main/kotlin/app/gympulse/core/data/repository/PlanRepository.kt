package app.gympulse.core.data.repository

import app.gympulse.core.data.mapper.toModel
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.database.dao.PlanDao
import app.gympulse.core.database.entity.PlanExerciseEntity
import app.gympulse.core.database.entity.WorkoutPlanEntity
import app.gympulse.core.model.WorkoutPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PlanRepository @Inject constructor(
    private val planDao: PlanDao,
    private val time: TimeProvider,
) {
    fun observePlans(): Flow<List<WorkoutPlan>> = planDao.observeActive().map { list -> list.map { it.toModel() } }

    fun observePlan(id: String): Flow<WorkoutPlan?> = planDao.observeById(id).map { it?.toModel() }

    /** Speichert Plan und Übungsliste; entfernte Übungen werden soft-gelöscht. */
    suspend fun save(plan: WorkoutPlan) {
        val now = time.now().toEpochMilli()
        val existing = planDao.getById(plan.id)
        val createdAt = existing?.plan?.createdAt ?: now
        val existingExercises = existing?.exercises.orEmpty().associateBy { it.id }
        planDao.savePlan(
            plan = WorkoutPlanEntity(
                id = plan.id,
                name = plan.name,
                description = plan.description,
                color = plan.color,
                isArchived = plan.isArchived,
                createdAt = createdAt,
                updatedAt = now,
            ),
            exercises = plan.exercises.mapIndexed { index, item ->
                PlanExerciseEntity(
                    id = item.id,
                    planId = plan.id,
                    exerciseId = item.exerciseId,
                    sortOrder = index,
                    supersetGroup = item.supersetGroup,
                    targetSets = item.targetSets,
                    targetRepsMin = item.targetRepsMin,
                    targetRepsMax = item.targetRepsMax,
                    targetWeightKg = item.targetWeightKg,
                    restSeconds = item.restSeconds,
                    createdAt = existingExercises[item.id]?.createdAt ?: now,
                    updatedAt = now,
                )
            },
            now = now,
        )
    }

    suspend fun delete(id: String) = planDao.softDelete(id, time.now().toEpochMilli())
}
