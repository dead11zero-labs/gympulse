package app.gympulse.feature.plans

import app.gympulse.core.model.Exercise
import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.PlanExercise
import app.gympulse.core.model.TrackingType
import app.gympulse.core.model.WorkoutPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanDraftTest {

    private fun item(id: String, group: Int? = null) =
        DraftExercise(id = id, exerciseId = "ex-$id", name = id.uppercase(), supersetGroup = group)

    private fun draft(vararg items: DraftExercise) =
        PlanDraft(planId = "plan", name = "Push", items = items.toList())

    private fun PlanDraft.ids() = items.map { it.id }

    private fun PlanDraft.groups() = items.map { it.supersetGroup }

    private fun exercise(id: String, name: String, tracking: TrackingType = TrackingType.WEIGHT_REPS) = Exercise(
        id = id,
        slug = null,
        name = name,
        category = ExerciseCategory.STRENGTH,
        equipmentType = EquipmentType.BARBELL,
        trackingType = tracking,
        bodyweightFactor = null,
        primaryMuscles = emptyList(),
        secondaryMuscles = emptyList(),
        instructions = emptyList(),
        imageUrls = emptyList(),
        notes = null,
        isCustom = false,
        isFavorite = false,
        defaultRestSeconds = null,
        equipmentId = null,
    )

    // --- Reihenfolge ---

    @Test
    fun move_swapsNeighboursAndIgnoresOutOfRange() {
        val d = draft(item("a"), item("b"), item("c"))

        assertEquals(listOf("b", "a", "c"), d.move("a", 1).ids())
        assertEquals(listOf("a", "c", "b"), d.move("c", -1).ids())
        assertEquals(d, d.move("a", -1))
        assertEquals(d, d.move("c", 1))
        assertEquals(d, d.move("unknown", 1))
    }

    @Test
    fun remove_dropsItemAndDissolvesLonelyGroup() {
        val d = draft(item("a", 1), item("b", 1), item("c"))

        val result = d.remove("b")

        assertEquals(listOf("a", "c"), result.ids())
        assertEquals(listOf(null, null), result.groups())
    }

    // --- Supersätze ---

    @Test
    fun link_createsGroupOfTwoNeighbours() {
        val d = draft(item("a"), item("b"), item("c"))

        val result = d.toggleLinkWithNext("a")

        assertEquals(listOf(1, 1, null), result.groups())
    }

    @Test
    fun link_lastItemHasNoNextAndStaysUnchanged() {
        val d = draft(item("a"), item("b"))

        assertEquals(d, d.toggleLinkWithNext("b"))
    }

    @Test
    fun link_extendsExistingGroup() {
        val d = draft(item("a", 1), item("b", 1), item("c")).toggleLinkWithNext("b")

        assertEquals(listOf(1, 1, 1), d.groups())
    }

    @Test
    fun link_mergesTwoGroups() {
        val d = draft(item("a", 1), item("b", 1), item("c", 2), item("d", 2)).toggleLinkWithNext("b")

        assertEquals(listOf(1, 1, 1, 1), d.groups())
    }

    @Test
    fun unlink_splitsGroupAtBoundary() {
        val d = draft(item("a", 1), item("b", 1), item("c", 1), item("d", 1)).toggleLinkWithNext("b")

        assertEquals(listOf(1, 1, 2, 2), d.groups())
    }

    @Test
    fun unlink_pairDissolvesCompletely() {
        val d = draft(item("a", 1), item("b", 1), item("c")).toggleLinkWithNext("a")

        assertEquals(listOf(null, null, null), d.groups())
    }

    @Test
    fun unlink_leavingSingleMemberDropsIt() {
        val d = draft(item("a", 1), item("b", 1), item("c", 1)).toggleLinkWithNext("a")

        assertEquals(listOf(null, 1, 1), d.groups())
    }

    @Test
    fun move_outOfGroupReleasesNonContiguousMembers() {
        // A1 A2 B -> A1 B A2: die Gruppe wäre nicht mehr zusammenhängend
        val d = draft(item("a", 1), item("b", 1), item("c")).move("b", 1)

        assertEquals(listOf("a", "c", "b"), d.ids())
        assertEquals(listOf(null, null, null), d.groups())
    }

    @Test
    fun move_insideGroupKeepsGroup() {
        val d = draft(item("a", 1), item("b", 1), item("c", 1)).move("a", 1)

        assertEquals(listOf("b", "a", "c"), d.ids())
        assertEquals(listOf(1, 1, 1), d.groups())
    }

    @Test
    fun normalize_renumbersGroupsInOrder() {
        val normalized = normalizeGroups(listOf(item("a", 5), item("b", 5), item("c"), item("d", 9), item("e", 9)))

        assertEquals(listOf(1, 1, null, 2, 2), normalized.map { it.supersetGroup })
    }

    @Test
    fun labels_useLetterPerGroupAndNumberPerMember() {
        val items = listOf(item("a", 1), item("b", 1), item("c"), item("d", 2), item("e", 2), item("f", 2))

        val labels = supersetLabels(items)

        assertEquals(mapOf("a" to "A1", "b" to "A2", "d" to "B1", "e" to "B2", "f" to "B3"), labels)
    }

    // --- Hinzufügen ---

    @Test
    fun addExercises_appendsWithDefaultsInGivenOrder() {
        var counter = 0
        val d = draft(item("a")).addExercises(
            listOf(exercise("e1", "Bench"), exercise("e2", "Plank", TrackingType.DURATION)),
        ) { "new-${counter++}" }

        assertEquals(listOf("a", "new-0", "new-1"), d.ids())
        assertEquals("Bench", d.items[1].name)
        assertEquals("3", d.items[1].sets)
        assertEquals("8", d.items[1].repsMin)
        assertEquals("12", d.items[1].repsMax)
        assertEquals("", d.items[2].repsMin)
        assertEquals("", d.items[2].repsMax)
    }

    // --- Validierung ---

    @Test
    fun validate_flagsMissingNameAndExercises() {
        val result = PlanDraft(planId = "p", name = "  ").validate()

        assertTrue(result.nameBlank)
        assertTrue(result.noExercises)
        assertFalse(result.isValid)
    }

    @Test
    fun validate_acceptsDefaults() {
        assertTrue(draft(item("a")).validate().isValid)
    }

    @Test
    fun validate_checksSetsRepsWeightAndRest() {
        val bad = item("a").copy(sets = "0", repsMin = "12", repsMax = "8", weight = "abc", rest = "9999")

        val errors = draft(bad).validate().itemErrors.getValue("a")

        assertTrue(errors.sets)
        assertTrue(errors.reps)
        assertTrue(errors.weight)
        assertTrue(errors.rest)
    }

    @Test
    fun validate_allowsEmptyOptionalFieldsAndCommaDecimal() {
        val ok = item("a").copy(repsMin = "", repsMax = "", weight = "62,5", rest = "")

        assertFalse(draft(ok).validate().itemErrors.getValue("a").any)
    }

    @Test
    fun validate_emptySetsIsError() {
        assertTrue(draft(item("a").copy(sets = "")).validate().itemErrors.getValue("a").sets)
    }

    // --- Umwandlung ---

    @Test
    fun toPlan_mapsFieldsAndAssignsSortOrder() {
        val d = PlanDraft(
            planId = "plan",
            name = "  Push  ",
            description = "  ",
            items = listOf(
                item("a", 1).copy(sets = "4", repsMin = "6", repsMax = "8", weight = "62,5", rest = "120"),
                item("b", 1).copy(repsMin = "", repsMax = ""),
            ),
        )

        val plan = d.toPlanOrNull()

        assertNotNull(plan)
        plan!!
        assertEquals("Push", plan.name)
        assertNull(plan.description)
        assertEquals(listOf(0, 1), plan.exercises.map { it.sortOrder })
        val first = plan.exercises[0]
        assertEquals("ex-a", first.exerciseId)
        assertEquals(1, first.supersetGroup)
        assertEquals(4, first.targetSets)
        assertEquals(6, first.targetRepsMin)
        assertEquals(8, first.targetRepsMax)
        assertEquals(62.5, first.targetWeightKg!!, 0.0001)
        assertEquals(120, first.restSeconds)
        val second = plan.exercises[1]
        assertNull(second.targetRepsMin)
        assertNull(second.targetWeightKg)
        assertNull(second.restSeconds)
    }

    @Test
    fun toPlan_returnsNullWhenInvalid() {
        assertNull(PlanDraft(planId = "p").toPlanOrNull())
    }

    @Test
    fun from_roundTripsThroughToPlan() {
        val plan = WorkoutPlan(
            id = "p1",
            name = "Pull",
            description = "Back day",
            color = 7L,
            isArchived = false,
            exercises = listOf(
                PlanExercise("i2", "e2", 1, 1, 3, 8, 12, null, null),
                PlanExercise("i1", "e1", 0, 1, 5, 5, 5, 100.0, 180),
                PlanExercise("i3", "e3", 2, null, 2, null, null, null, 60),
            ),
        )

        val draft = PlanDraft.from(plan, mapOf("e1" to "Row", "e2" to "Curl"))
        val back = draft.toPlanOrNull()

        assertEquals(listOf("i1", "i2", "i3"), draft.ids())
        assertEquals("Row", draft.items[0].name)
        assertNull(draft.items[2].name)
        assertEquals("100", draft.items[0].weight)
        assertEquals(plan.copy(exercises = plan.exercises.sortedBy { it.sortOrder }), back)
    }

    @Test
    fun dirty_isFalseForUnchangedDraftAndTrueAfterEdit() {
        val initial = draft(item("a"))
        val state = PlanEditorUiState(isLoading = false, draft = initial, initial = initial)

        assertFalse(state.isDirty)
        assertTrue(state.copy(draft = initial.copy(name = "Other")).isDirty)
    }

    // --- Eingabe-Filter ---

    @Test
    fun sanitize_filtersInput() {
        assertEquals("12", sanitizeDigits("1a2b", 3))
        assertEquals("123", sanitizeDigits("12345", 3))
        assertEquals("62,5", sanitizeDecimal("6x2,5"))
        assertEquals("62,53", sanitizeDecimal("62,5.3"))
    }

    @Test
    fun parseDecimal_acceptsCommaAndPoint() {
        assertEquals(62.5, parseDecimal("62,5")!!, 0.0001)
        assertEquals(62.5, parseDecimal(" 62.5 ")!!, 0.0001)
        assertNull(parseDecimal("."))
        assertNull(parseDecimal(""))
    }

    @Test
    fun formatDecimal_dropsTrailingZero() {
        assertEquals("100", formatDecimal(100.0))
        assertEquals("62.5", formatDecimal(62.5))
    }

    @Test
    fun filterExercises_matchesNameIgnoringCase() {
        val list = listOf(exercise("1", "Bench Press"), exercise("2", "Squat"), exercise("3", "Incline BENCH"))

        assertEquals(listOf("1", "3"), filterExercises(list, " bench ").map { it.id })
        assertEquals(3, filterExercises(list, "").size)
        assertTrue(filterExercises(list, "zzz").isEmpty())
    }
}
