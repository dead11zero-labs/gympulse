package app.gympulse.feature.dashboard

import app.gympulse.core.data.repository.WeekAnalysisData
import app.gympulse.core.data.util.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class WeekAnalysisViewModelTest {
    private val zone = ZoneId.of("UTC")

    // Mittwoch, 21.10.2026; die Woche beginnt am Montag, 19.10.
    private val now = Instant.parse("2026-10-21T09:30:00Z")

    private val time = object : TimeProvider {
        override fun now(): Instant = now
        override fun zone(): ZoneId = zone
    }

    private val dataSource = object : WeekAnalysisDataSource {
        var requestedFrom: Instant? = null
        override fun observe(from: Instant): Flow<WeekAnalysisData> {
            requestedFrom = from
            return MutableStateFlow(WeekAnalysisData(emptyList(), emptyMap()))
        }
    }

    private val weekStart = object : WeekStartProvider {
        override val weekStartsOn: StateFlow<DayOfWeek> = MutableStateFlow(DayOfWeek.MONDAY)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = WeekAnalysisViewModel(dataSource, weekStart, time)

    @Test
    fun startsOnCurrentWeekWithTwelveWeeksLoaded() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(WEEKS_LOADED, state.weeks.size)
        assertEquals(LocalDate.of(2026, 10, 19), state.selected?.weekStart)
        assertTrue(state.isCurrentWeek)
        assertFalse(state.canGoForward)
        assertTrue(state.canGoBack)
        assertEquals(WEEKS_SHOWN, state.trendWeeks.size)
        assertEquals(WEEKS_SHOWN - 1, state.selectedTrendIndex)
        assertEquals(Instant.parse("2026-08-03T00:00:00Z"), dataSource.requestedFrom)
    }

    @Test
    fun navigationStaysWithinShownWeeks() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        repeat(WEEKS_SHOWN + 3) { vm.showPreviousWeek() }
        var state = vm.uiState.value
        assertEquals(0, state.selectedTrendIndex)
        assertFalse(state.canGoBack)
        assertEquals(LocalDate.of(2026, 10, 19).minusWeeks(WEEKS_SHOWN - 1L), state.selected?.weekStart)

        repeat(WEEKS_SHOWN + 3) { vm.showNextWeek() }
        state = vm.uiState.value
        assertTrue(state.isCurrentWeek)
    }

    @Test
    fun selectingTrendPointJumpsToThatWeek() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.selectTrendWeek(WEEKS_SHOWN - 3)
        val state = vm.uiState.value
        assertEquals(WEEKS_SHOWN - 3, state.selectedTrendIndex)
        assertEquals(LocalDate.of(2026, 10, 19).minusWeeks(2), state.selected?.weekStart)
        assertEquals(LocalDate.of(2026, 10, 19).minusWeeks(3), state.previous?.weekStart)
    }
}
