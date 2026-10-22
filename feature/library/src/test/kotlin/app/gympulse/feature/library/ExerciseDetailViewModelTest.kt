package app.gympulse.feature.library

import androidx.lifecycle.SavedStateHandle
import app.gympulse.core.insightcards.InsightsEmptyReason
import app.gympulse.core.insightcards.InsightsUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
class ExerciseDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeLibraryRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository.exercises.value = listOf(
            testLibraryExercise(testExercise("bench", "Bench Press", favorite = true)),
            testLibraryExercise(testExercise("squat", "Squat")),
        )
        repository.plans.value = listOf(testPlan("p1", "Push", exerciseCount = 2), testPlan("p2", "Legs"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(id: String) = ExerciseDetailViewModel(SavedStateHandle(mapOf("exerciseId" to id)), repository)

    @Test
    fun heartIsOffUntilLoadedAndForNonFavorites() = runTest {
        val vm = vm("squat")
        // Vor dem Laden (und ohne Übung) darf das Herz nie als Favorit erscheinen.
        assertFalse(vm.uiState.value.isFavorite)
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }

        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isFavorite)
    }

    @Test
    fun heartIsOnOnlyForFavorites() = runTest {
        val vm = vm("bench")
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value.isFavorite)
    }

    @Test
    fun toggleFavoriteFlipsHeartBothWays() = runTest {
        val vm = vm("squat")
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }

        vm.toggleFavorite()
        assertEquals(listOf("squat" to true), repository.favoriteCalls)
        assertTrue(vm.uiState.value.isFavorite)

        vm.toggleFavorite()
        assertEquals("squat" to false, repository.favoriteCalls.last())
        assertFalse(vm.uiState.value.isFavorite)
    }

    @Test
    fun exposesPlansForTheSheet() = runTest {
        val vm = vm("squat")
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        assertEquals(listOf("p1", "p2"), vm.uiState.value.plans.map { it.id })

        repository.plans.value = emptyList()
        assertTrue(vm.uiState.value.plans.isEmpty())
    }

    @Test
    fun addToPlanDelegatesAndReportsPlanName() = runTest {
        val vm = vm("squat")
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }

        vm.addToPlan("p2")
        assertEquals(listOf("p2" to "squat"), repository.planAdds)
        assertEquals(PlanAddResult.Added("Legs"), vm.uiState.value.planAddResult)

        vm.consumePlanAddResult()
        assertNull(vm.uiState.value.planAddResult)
    }

    @Test
    fun addToUnknownPlanIsIgnored() = runTest {
        val vm = vm("squat")
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }

        vm.addToPlan("deleted-plan")
        assertTrue(repository.planAdds.isEmpty())
        assertNull(vm.uiState.value.planAddResult)
    }

    @Test
    fun addToPlanFailureIsReportedWithoutCrash() = runTest {
        val vm = vm("squat")
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        repository.planAddFails = true

        vm.addToPlan("p1")
        assertEquals(PlanAddResult.Failed, vm.uiState.value.planAddResult)
    }

    @Test
    fun insightsFollowTheRepository() = runTest {
        val vm = vm("bench")
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value.insights.isEmpty)
        assertNull(vm.uiState.value.insights.emptyReason)

        repository.insights.value = InsightsUi(emptyReason = InsightsEmptyReason.NOT_ENOUGH_DATA)
        assertEquals(InsightsEmptyReason.NOT_ENOUGH_DATA, vm.uiState.value.insights.emptyReason)
    }
}
