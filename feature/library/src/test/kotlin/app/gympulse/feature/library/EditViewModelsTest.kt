package app.gympulse.feature.library

import androidx.lifecycle.SavedStateHandle
import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.EquipmentTemplate
import app.gympulse.core.model.EquipmentTemplateSetting
import app.gympulse.core.model.MuscleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditViewModelsTest {
    private val repository = FakeLibraryRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun exerciseVm(id: String? = null) =
        ExerciseEditViewModel(SavedStateHandle(mapOf("exerciseId" to id)), repository)

    private fun equipmentVm(id: String? = null, template: String? = null) = EquipmentEditViewModel(
        SavedStateHandle(mapOf("equipmentId" to id, "templateKey" to template)),
        repository,
    )

    @Test
    fun blankNameIsRejected() = runTest {
        val vm = exerciseVm()
        vm.save()
        assertTrue(vm.state.value.showErrors)
        assertTrue(repository.savedExercises.isEmpty())
        assertFalse(vm.state.value.saved)
    }

    @Test
    fun savesNewCustomExercise() = runTest {
        val vm = exerciseVm()
        vm.setName("  My Row ")
        vm.setEquipmentType(EquipmentType.BODYWEIGHT)
        vm.setBodyweightFactor("0,5")
        vm.setDefaultRestSeconds("75")
        vm.setNotes("")
        vm.togglePrimary(MuscleGroup.LATS)
        vm.save()

        val draft = repository.savedExercises.single()
        assertNull(draft.id)
        assertEquals("My Row", draft.name)
        assertEquals(0.5, draft.bodyweightFactor!!, 0.0001)
        assertEquals(75, draft.defaultRestSeconds)
        assertNull(draft.notes)
        assertEquals(setOf(MuscleGroup.LATS), draft.primaryMuscles)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun invalidNumbersBlockSaving() = runTest {
        val vm = exerciseVm()
        vm.setName("X")
        vm.setEquipmentType(EquipmentType.BODYWEIGHT)
        vm.setBodyweightFactor("5")
        vm.save()
        assertTrue(vm.state.value.bodyweightFactorInvalid)
        vm.setBodyweightFactor("")
        vm.setDefaultRestSeconds("-3")
        vm.save()
        assertTrue(vm.state.value.restInvalid)
        assertTrue(repository.savedExercises.isEmpty())
    }

    @Test
    fun muscleIsEitherPrimaryOrSecondary() = runTest {
        val vm = exerciseVm()
        vm.togglePrimary(MuscleGroup.CHEST)
        vm.toggleSecondary(MuscleGroup.CHEST)
        assertTrue(vm.state.value.primaryMuscles.isEmpty())
        assertEquals(setOf(MuscleGroup.CHEST), vm.state.value.secondaryMuscles)
        vm.togglePrimary(MuscleGroup.CHEST)
        assertTrue(vm.state.value.secondaryMuscles.isEmpty())
    }

    @Test
    fun customExerciseIsLoadedAndSavedWithId() = runTest {
        repository.exercises.value = listOf(
            testLibraryExercise(testExercise("c1", "Mine", custom = true, primary = listOf(MuscleGroup.CHEST), defaultRestSeconds = 60)),
        )
        val vm = exerciseVm("c1")
        assertEquals("Mine", vm.state.value.name)
        assertEquals("60", vm.state.value.defaultRestSeconds)
        vm.setName("Mine 2")
        vm.save()
        assertEquals("c1", repository.savedExercises.single().id)
        assertEquals("Mine 2", repository.savedExercises.single().name)
    }

    @Test
    fun seedExerciseOnlyUpdatesUserFields() = runTest {
        repository.exercises.value = listOf(testLibraryExercise(testExercise("seed", "Squat")))
        val vm = exerciseVm("seed")
        assertFalse(vm.state.value.isCustom)
        vm.setName("Hacked")
        vm.setNotes("Keep knees out")
        vm.setDefaultRestSeconds("120")
        vm.setEquipmentId("m1")
        vm.save()

        assertTrue(repository.savedExercises.isEmpty())
        assertEquals(UserFieldUpdate("seed", "Keep knees out", 120, "m1"), repository.userFieldUpdates.single())
    }

    @Test
    fun missingExerciseIsReported() = runTest {
        val vm = exerciseVm("nope")
        assertTrue(vm.state.value.notFound)
    }

    @Test
    fun equipmentFromTemplatePrefillsSettings() = runTest {
        repository.templateList = listOf(
            EquipmentTemplate(
                key = "leg_press",
                equipmentType = EquipmentType.MACHINE,
                name = "Leg press",
                muscleGroups = emptyList(),
                settingKeys = listOf(EquipmentTemplateSetting("seat", "Seat position", "e.g. 5")),
            ),
        )
        val vm = equipmentVm(template = "leg_press")
        val state = vm.state.value
        assertEquals("Leg press", state.name)
        assertEquals("leg_press", state.templateKey)
        assertEquals("Seat position", state.settings.single().key)
        assertEquals("e.g. 5", state.settings.single().hint)
        assertTrue(state.isNew)
    }

    @Test
    fun equipmentNeedsNameAndSavesSettings() = runTest {
        val vm = equipmentVm()
        vm.save()
        assertTrue(vm.state.value.showErrors)
        assertTrue(repository.savedEquipment.isEmpty())

        vm.setName("Leg press")
        vm.addSetting()
        vm.addSetting()
        val rows = vm.state.value.settings
        vm.setSettingKey(rows[0].rowId, "Seat height")
        vm.setSettingValue(rows[0].rowId, "4")
        vm.removeSetting(rows[1].rowId)
        vm.save()

        val draft = repository.savedEquipment.single()
        assertNull(draft.id)
        assertEquals(listOf("Seat height" to "4"), draft.settings.map { it.key to it.value })
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun existingEquipmentLoadsLinkedExercisesAndCanBeDeleted() = runTest {
        repository.equipment.value = listOf(testEquipment("m1", "Press", listOf("Seat" to "3")))
        repository.exercises.value = listOf(
            testLibraryExercise(testExercise("e1", "Leg Press", equipmentId = "m1")),
            testLibraryExercise(testExercise("e2", "Curl")),
        )
        val vm = equipmentVm("m1")
        val state = vm.state.value
        assertEquals("Press", state.name)
        assertEquals("m1-s0", state.settings.single().id)
        assertEquals(listOf("e1"), state.linkedExercises.map { it.id })

        vm.delete()
        assertEquals(listOf("m1"), repository.deletedEquipment)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun templateFilterMatchesNames() {
        val templates = listOf(
            EquipmentTemplate("a", EquipmentType.MACHINE, "Beinpresse", emptyList(), emptyList()),
            EquipmentTemplate("b", EquipmentType.CABLE, "Kabelzug", emptyList(), emptyList()),
        )
        assertEquals(listOf("a"), filterTemplates(templates, "BEIN").map { it.key })
        assertEquals(2, filterTemplates(templates, " ").size)
    }
}
