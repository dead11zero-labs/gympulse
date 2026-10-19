package app.gympulse.feature.library

import app.gympulse.core.model.MuscleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseListViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeLibraryRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository.exercises.value = listOf(
            testLibraryExercise(testExercise("bench", "Bench Press", primary = listOf(MuscleGroup.CHEST), favorite = true)),
            testLibraryExercise(testExercise("squat", "Squat", primary = listOf(MuscleGroup.QUADRICEPS))),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startsLoadingThenShowsAllExercises() = runTest {
        val vm = ExerciseListViewModel(repository)
        assertTrue(vm.uiState.value.isLoading)
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.totalCount)
        assertEquals(listOf("bench", "squat"), state.exercises.map { it.id })
    }

    @Test
    fun searchIsDebounced() = runTest {
        val vm = ExerciseListViewModel(repository)
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }

        vm.setQuery("squ")
        assertEquals("squ", vm.query.value)
        assertEquals(2, vm.uiState.value.exercises.size)

        advanceTimeBy(ExerciseListViewModel.QUERY_DEBOUNCE_MILLIS + 1)
        assertEquals(listOf("squat"), vm.uiState.value.exercises.map { it.id })
    }

    @Test
    fun facetsFilterImmediately() = runTest {
        val vm = ExerciseListViewModel(repository)
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }

        vm.setMuscle(MuscleGroup.CHEST)
        assertEquals(listOf("bench"), vm.uiState.value.exercises.map { it.id })

        vm.setMuscle(null)
        vm.toggleFavoritesOnly()
        assertEquals(listOf("bench"), vm.uiState.value.exercises.map { it.id })
    }

    @Test
    fun noMatchKeepsTotalCountAndClearFiltersResets() = runTest {
        val vm = ExerciseListViewModel(repository)
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }

        vm.setMuscle(MuscleGroup.NECK)
        assertTrue(vm.uiState.value.exercises.isEmpty())
        assertEquals(2, vm.uiState.value.totalCount)

        vm.setQuery("x")
        vm.clearFilters()
        assertEquals("", vm.query.value)
        assertFalse(vm.uiState.value.filter.isActive)
        assertEquals(2, vm.uiState.value.exercises.size)
    }

    @Test
    fun emptyLibraryHasZeroTotal() = runTest {
        repository.exercises.value = emptyList()
        val vm = ExerciseListViewModel(repository)
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }

        assertFalse(vm.uiState.value.isLoading)
        assertEquals(0, vm.uiState.value.totalCount)
    }

    @Test
    fun localeChangeReloadsExercises() = runTest {
        val vm = ExerciseListViewModel(repository)
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }

        vm.setLocale("de")
        assertEquals("de", repository.requestedLocales.last())
    }
}
