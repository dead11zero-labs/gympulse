package app.gympulse.feature.library.data

import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MuscleGroup
import app.gympulse.feature.library.testExercise
import app.gympulse.feature.library.testLibraryExercise
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseFilterTest {
    private val bench = testLibraryExercise(
        testExercise("bench", "Bankdrücken", primary = listOf(MuscleGroup.CHEST), secondary = listOf(MuscleGroup.TRICEPS), favorite = true),
        "Barbell Bench Press",
    )
    private val deadlift = testLibraryExercise(
        testExercise("dead", "Kreuzheben", primary = listOf(MuscleGroup.LOWER_BACK), category = ExerciseCategory.POWERLIFTING),
        "Deadlift",
    )
    private val curl = testLibraryExercise(
        testExercise("curl", "Bizeps-Curl", equipmentType = EquipmentType.DUMBBELL, primary = listOf(MuscleGroup.BICEPS)),
    )
    private val all = listOf(bench, deadlift, curl)

    private fun ids(filter: ExerciseFilter) = filterExercises(all, filter).map { it.id }

    @Test
    fun emptyFilterKeepsEverythingInOrder() {
        assertEquals(listOf("bench", "dead", "curl"), ids(ExerciseFilter()))
    }

    @Test
    fun searchIgnoresCaseAndUmlauts() {
        assertEquals(listOf("bench"), ids(ExerciseFilter(query = "BANKDRUCKEN")))
        assertEquals(listOf("bench"), ids(ExerciseFilter(query = "bankdrücken")))
    }

    @Test
    fun searchFindsOtherLanguageNames() {
        assertEquals(listOf("dead"), ids(ExerciseFilter(query = "deadlift")))
    }

    @Test
    fun allSearchWordsMustMatch() {
        assertEquals(listOf("bench"), ids(ExerciseFilter(query = "barbell press")))
        assertEquals(emptyList<String>(), ids(ExerciseFilter(query = "barbell curl")))
    }

    @Test
    fun blankQueryIsIgnored() {
        assertEquals(3, ids(ExerciseFilter(query = "   ")).size)
    }

    @Test
    fun muscleFilterUsesPrimaryMusclesOnly() {
        assertEquals(listOf("bench"), ids(ExerciseFilter(muscle = MuscleGroup.CHEST)))
        assertEquals(emptyList<String>(), ids(ExerciseFilter(muscle = MuscleGroup.TRICEPS)))
    }

    @Test
    fun equipmentCategoryAndFavoriteFilters() {
        assertEquals(listOf("curl"), ids(ExerciseFilter(equipmentType = EquipmentType.DUMBBELL)))
        assertEquals(listOf("dead"), ids(ExerciseFilter(category = ExerciseCategory.POWERLIFTING)))
        assertEquals(listOf("bench"), ids(ExerciseFilter(favoritesOnly = true)))
    }

    @Test
    fun filtersCombine() {
        val filter = ExerciseFilter(query = "kreuz", category = ExerciseCategory.POWERLIFTING, muscle = MuscleGroup.LOWER_BACK)
        assertEquals(listOf("dead"), ids(filter))
        assertEquals(emptyList<String>(), ids(filter.copy(favoritesOnly = true)))
    }

    @Test
    fun normalizeHandlesSharpSAndAccents() {
        assertEquals("fusshebe", normalizeForSearch("Fußhebe"))
        assertEquals("cafe", normalizeForSearch("Café"))
    }

    @Test
    fun isActiveReflectsQueryAndFacets() {
        assertEquals(false, ExerciseFilter().isActive)
        assertEquals(true, ExerciseFilter(query = "a").isActive)
        assertEquals(true, ExerciseFilter(favoritesOnly = true).isActive)
    }

    @Test
    fun parsesDecimalsWithCommaAndPoint() {
        assertEquals(0.64, parseDecimalOrNull("0,64")!!, 0.0001)
        assertEquals(0.64, parseDecimalOrNull(" 0.64 ")!!, 0.0001)
        assertEquals(null, parseDecimalOrNull("abc"))
        assertEquals(null, parseIntOrNull("1.5"))
    }
}
