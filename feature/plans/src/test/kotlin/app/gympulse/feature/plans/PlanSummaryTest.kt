package app.gympulse.feature.plans

import app.gympulse.core.model.PlanExercise
import app.gympulse.core.model.WorkoutPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanSummaryTest {

    private fun exercise(id: String, sets: Int, rest: Int?) = PlanExercise(
        id = id,
        exerciseId = "ex-$id",
        sortOrder = 0,
        supersetGroup = null,
        targetSets = sets,
        targetRepsMin = 8,
        targetRepsMax = 12,
        targetWeightKg = null,
        restSeconds = rest,
    )

    private fun plan(vararg exercises: PlanExercise) = WorkoutPlan(
        id = "p",
        name = "Push",
        description = null,
        color = null,
        isArchived = false,
        exercises = exercises.toList(),
    )

    @Test
    fun summary_countsExercisesAndSets() {
        val summary = plan(exercise("a", 3, 60), exercise("b", 4, null)).summary()

        assertEquals(2, summary.exerciseCount)
        assertEquals(7, summary.totalSets)
    }

    @Test
    fun summary_estimatesMinutesFromSetAndRestTime() {
        // 3 x (45 + 60) + 4 x (45 + 90 Standardpause) = 315 + 540 = 855 s -> 15 min aufgerundet
        val summary = plan(exercise("a", 3, 60), exercise("b", 4, null)).summary()

        assertEquals(15, summary.estimatedMinutes)
    }

    @Test
    fun summary_ofEmptyPlanIsZero() {
        val summary = plan().summary()

        assertEquals(PlanSummary(0, 0, 0), summary)
    }

    @Test
    fun duplicate_getsNewIdsAndKeepsContentAndOrder() {
        var counter = 0
        val original = plan(exercise("a", 3, 60), exercise("b", 4, null))

        val copy = original.duplicate("Push (copy)") { "new-${counter++}" }

        assertEquals("Push (copy)", copy.name)
        assertNotEquals(original.id, copy.id)
        assertEquals(listOf("ex-a", "ex-b"), copy.exercises.map { it.exerciseId })
        assertEquals(original.exercises.map { it.targetSets }, copy.exercises.map { it.targetSets })
        assertTrue(copy.exercises.map { it.id }.intersect(original.exercises.map { it.id }.toSet()).isEmpty())
        assertEquals(3, (listOf(copy.id) + copy.exercises.map { it.id }).toSet().size)
    }
}
