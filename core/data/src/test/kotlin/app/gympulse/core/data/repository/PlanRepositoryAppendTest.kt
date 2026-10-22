package app.gympulse.core.data.repository

import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.database.dao.PlanDao
import app.gympulse.core.database.entity.PlanExerciseEntity
import app.gympulse.core.database.entity.WorkoutPlanEntity
import app.gympulse.core.database.relation.PlanWithExercises
import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.TrackingType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PlanRepositoryAppendTest {
    private class FakePlanDao : PlanDao {
        val plans = MutableStateFlow<List<WorkoutPlanEntity>>(emptyList())
        val items = MutableStateFlow<List<PlanExerciseEntity>>(emptyList())

        private fun joined(): List<PlanWithExercises> =
            plans.value.map { plan -> PlanWithExercises(plan, items.value.filter { it.planId == plan.id }) }

        override fun observeActive(): Flow<List<PlanWithExercises>> =
            plans.map { joined().filter { it.plan.deletedAt == null && !it.plan.isArchived } }

        override fun observeById(id: String): Flow<PlanWithExercises?> =
            plans.map { joined().firstOrNull { it.plan.id == id && it.plan.deletedAt == null } }

        override suspend fun getById(id: String): PlanWithExercises? = joined().firstOrNull { it.plan.id == id }

        override suspend fun upsertPlan(plan: WorkoutPlanEntity) {
            plans.value = plans.value.filterNot { it.id == plan.id } + plan
        }

        override suspend fun upsertPlanExercises(exercises: List<PlanExerciseEntity>) {
            val ids = exercises.map { it.id }.toSet()
            items.value = items.value.filterNot { it.id in ids } + exercises
        }

        override suspend fun softDeletePlanExercisesExcept(planId: String, keepIds: List<String>, now: Long) {
            items.value = items.value.map {
                if (it.planId == planId && it.id !in keepIds) it.copy(deletedAt = now, updatedAt = now) else it
            }
        }

        override suspend fun softDelete(id: String, now: Long) {
            plans.value = plans.value.map { if (it.id == id) it.copy(deletedAt = now, updatedAt = now) else it }
        }
    }

    private val time = object : TimeProvider {
        override fun now(): Instant = Instant.parse("2026-10-21T09:30:00Z")
        override fun zone(): ZoneId = ZoneId.of("UTC")
    }
    private val dao = FakePlanDao()
    private val repo = PlanRepository(dao, time)

    private fun exercise(id: String, tracking: TrackingType) = Exercise(
        id = id, slug = id, name = id, category = ExerciseCategory.STRENGTH, equipmentType = EquipmentType.BARBELL,
        trackingType = tracking, bodyweightFactor = null, primaryMuscles = emptyList(), secondaryMuscles = emptyList(),
        instructions = emptyList(), imageUrls = emptyList(), notes = null, isCustom = false, isFavorite = false,
        defaultRestSeconds = null, equipmentId = null,
    )

    private fun plan(id: String, deletedAt: Long? = null) =
        WorkoutPlanEntity(id, "Plan $id", null, null, false, 1L, 1L, deletedAt)

    private fun item(id: String, planId: String, sortOrder: Int) = PlanExerciseEntity(
        id = id, planId = planId, exerciseId = "x$sortOrder", sortOrder = sortOrder, supersetGroup = null,
        targetSets = 4, targetRepsMin = 5, targetRepsMax = 5, targetWeightKg = 60.0, restSeconds = 90,
        createdAt = 1L, updatedAt = 1L,
    )

    @Test
    fun appendsToEndWithEditorDefaults() = runTest {
        dao.plans.value = listOf(plan("p1"))
        dao.items.value = listOf(item("i1", "p1", 0), item("i2", "p1", 1))

        assertTrue(repo.appendExercise("p1", exercise("bench", TrackingType.WEIGHT_REPS)))

        val exercises = repo.observePlan("p1").first()!!.exercises
        assertEquals(3, exercises.size)
        assertEquals(listOf("x0", "x1", "bench"), exercises.map { it.exerciseId })
        val added = exercises.last()
        assertEquals(2, added.sortOrder)
        assertEquals(PlanExerciseDefaults.SETS, added.targetSets)
        assertEquals(PlanExerciseDefaults.REPS_MIN, added.targetRepsMin)
        assertEquals(PlanExerciseDefaults.REPS_MAX, added.targetRepsMax)
        assertNull(added.targetWeightKg)
        assertNull(added.restSeconds)
        // Vorhandene Übungen bleiben unverändert.
        assertEquals(4, exercises.first().targetSets)
    }

    @Test
    fun durationExerciseHasNoRepTargets() = runTest {
        dao.plans.value = listOf(plan("p1"))
        assertTrue(repo.appendExercise("p1", exercise("plank", TrackingType.DURATION)))
        val added = dao.items.value.single { it.deletedAt == null }
        assertNull(added.targetRepsMin)
        assertNull(added.targetRepsMax)
    }

    @Test
    fun unknownOrDeletedPlanIsRejected() = runTest {
        dao.plans.value = listOf(plan("gone", deletedAt = 5L))
        assertFalse(repo.appendExercise("gone", exercise("bench", TrackingType.REPS)))
        assertFalse(repo.appendExercise("missing", exercise("bench", TrackingType.REPS)))
        assertTrue(dao.items.value.isEmpty())
    }
}
