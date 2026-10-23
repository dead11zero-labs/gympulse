package app.gympulse.feature.dashboard

import app.gympulse.core.data.health.HealthLastNightSleep
import app.gympulse.core.data.health.HealthSleepReader
import app.gympulse.core.data.health.HealthSteps
import app.gympulse.core.data.health.HealthStepsReader
import app.gympulse.core.data.repository.CompletedSessionSummary
import app.gympulse.core.data.repository.WeeklyVolume
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.insightcards.InsightsEmptyReason
import app.gympulse.core.insightcards.InsightsUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val zone = ZoneId.of("UTC")

    // Mittwoch, 21.10.2026, 09:30 UTC; die Woche beginnt am Montag, 19.10.
    private val now = Instant.parse("2026-10-21T09:30:00Z")
    private val monday = LocalDate.of(2026, 10, 19)

    private val time = object : TimeProvider {
        override fun now(): Instant = now
        override fun zone(): ZoneId = zone
    }

    private class FakeDataSource : DashboardDataSource {
        val current = MutableStateFlow(WeeklyVolume(LocalDate.of(2026, 10, 19), List(7) { 0.0 }))
        val previous = MutableStateFlow(WeeklyVolume(LocalDate.of(2026, 10, 12), List(7) { 0.0 }))
        val endTimes = MutableStateFlow<List<Instant>>(emptyList())
        val recent = MutableStateFlow<List<CompletedSessionSummary>>(emptyList())
        val active = MutableStateFlow<ActiveWorkout?>(null)
        val insights = MutableStateFlow(InsightsUi.Loading)

        override fun observeWeeklyVolume(weekOffset: Long, weekStartsOn: DayOfWeek): Flow<WeeklyVolume> =
            if (weekOffset == 0L) current else previous

        override fun observeSessionEndTimes(from: Instant, to: Instant): Flow<List<Instant>> =
            endTimes.map { list -> list.filter { it >= from && it < to } }

        override fun observeRecentSessions(limit: Int): Flow<List<CompletedSessionSummary>> = recent

        override fun observeActiveWorkout(): Flow<ActiveWorkout?> = active

        override fun observeInsights(): Flow<InsightsUi> = insights
    }

    private class FakeSettings(goal: Int = 3) : WeeklyGoalProvider, WeekStartProvider {
        override val weeklyGoal: StateFlow<Int> = MutableStateFlow(goal)
        override val weekStartsOn: StateFlow<DayOfWeek> = MutableStateFlow(DayOfWeek.MONDAY)
    }

    private class FakeSleepReader(var result: HealthLastNightSleep = HealthLastNightSleep.Disabled) : HealthSleepReader {
        override suspend fun lastNight(): HealthLastNightSleep = result
    }

    private val data = FakeDataSource()
    private var stepsResult: HealthSteps = HealthSteps.Unavailable
    private val stepsReader = HealthStepsReader { stepsResult }
    private val sleepReader = FakeSleepReader()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(goal: Int = 3) = DashboardViewModel(data, FakeSettings(goal), FakeSettings(goal), stepsReader, sleepReader, time)

    private fun end(date: LocalDate): Instant = date.atTime(18, 0).atZone(zone).toInstant()

    @Test
    fun initialStateIsLoadingWithDateAndGreeting() {
        val state = viewModel().uiState.value
        assertTrue(state.isLoading)
        assertEquals(LocalDate.of(2026, 10, 21), state.today)
        assertEquals(Greeting.MORNING, state.greeting)
    }

    @Test
    fun emptyDatabaseYieldsEmptyWidgets() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.weeklyVolume!!.isEmpty)
        assertNull(state.weeklyVolume!!.changePercent)
        assertEquals(0, state.weeklyProgress!!.completed)
        assertEquals(0, state.weeklyProgress!!.streakWeeks)
        assertTrue(state.recentSessions.isEmpty())
        assertNull(state.activeWorkout)
    }

    @Test
    fun weeklyVolumeComparesWithPreviousWeek() = runTest {
        data.current.value = WeeklyVolume(monday, listOf(1000.0, 0.0, 120.0, 0.0, 0.0, 0.0, 0.0))
        data.previous.value = WeeklyVolume(monday.minusWeeks(1), listOf(500.0, 500.0, 0.0, 0.0, 0.0, 0.0, 0.0))
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val volume = vm.uiState.value.weeklyVolume!!
        assertEquals(1120.0, volume.totalKg, 0.001)
        assertEquals(1000.0, volume.previousTotalKg, 0.001)
        assertEquals(12, volume.changePercent)
        assertEquals(2, volume.todayIndex)
        assertFalse(volume.isEmpty)
    }

    @Test
    fun progressCountsSessionsOfCurrentWeekAndStreak() = runTest {
        data.endTimes.value = listOf(
            end(monday), // aktuelle Woche
            end(monday.plusDays(1)),
            end(monday.minusWeeks(1)), // Vorwoche: 3 Trainings
            end(monday.minusWeeks(1).plusDays(2)),
            end(monday.minusWeeks(1).plusDays(4)),
        )
        val vm = viewModel(goal = 3)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val progress = vm.uiState.value.weeklyProgress!!
        assertEquals(2, progress.completed)
        assertEquals(3, progress.goal)
        assertEquals(2f / 3f, progress.fraction, 0.0001f)
        assertFalse(progress.goalReached)
        // Laufende Woche offen, Vorwoche erfüllt.
        assertEquals(1, progress.streakWeeks)
    }

    @Test
    fun reachingGoalCountsCurrentWeekInStreak() = runTest {
        data.endTimes.value = listOf(end(monday), end(monday.plusDays(1)), end(monday.plusDays(2)))
        val vm = viewModel(goal = 3)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val progress = vm.uiState.value.weeklyProgress!!
        assertTrue(progress.goalReached)
        assertEquals(1f, progress.fraction, 0.0001f)
        assertEquals(1, progress.streakWeeks)
    }

    @Test
    fun recentSessionsAreMappedWithLocalDateAndDuration() = runTest {
        data.recent.value = listOf(
            CompletedSessionSummary(
                id = "s1",
                title = "Push Day",
                startedAt = Instant.parse("2026-10-20T17:00:00Z"),
                endedAt = Instant.parse("2026-10-20T18:15:00Z"),
                volumeKg = 5400.0,
            ),
        )
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val recent = vm.uiState.value.recentSessions.single()
        assertEquals("Push Day", recent.title)
        assertEquals(LocalDate.of(2026, 10, 20), recent.date)
        assertEquals(75L, recent.durationMinutes)
        assertEquals(5400.0, recent.volumeKg, 0.001)
    }

    @Test
    fun sleepIsHiddenByDefaultAndShowsLastNightWhenAvailable() = runTest {
        val hidden = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { hidden.uiState.collect {} }
        assertEquals(HealthLastNightSleep.Disabled, hidden.uiState.value.sleep)

        sleepReader.result = HealthLastNightSleep.Available(minutes = 425)
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(HealthLastNightSleep.Available(425), vm.uiState.value.sleep)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun sleepIsReReadOnRefresh() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(HealthLastNightSleep.Disabled, vm.uiState.value.sleep)

        sleepReader.result = HealthLastNightSleep.Available(minutes = 480)
        vm.refreshHealth()
        assertEquals(HealthLastNightSleep.Available(480), vm.uiState.value.sleep)

        sleepReader.result = HealthLastNightSleep.PermissionMissing
        vm.refreshHealth()
        assertEquals(HealthLastNightSleep.PermissionMissing, vm.uiState.value.sleep)
    }

    @Test
    fun activeWorkoutIsExposedAndClearedWhenFinished() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertNull(vm.uiState.value.activeWorkout)

        data.active.value = ActiveWorkout("a1", "Leg Day", now)
        assertNotNull(vm.uiState.value.activeWorkout)
        assertEquals("Leg Day", vm.uiState.value.activeWorkout!!.title)

        data.active.value = null
        assertNull(vm.uiState.value.activeWorkout)
    }

    @Test
    fun stepsAreHiddenUntilHealthConnectDeliversThem() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(HealthSteps.Unavailable, vm.uiState.value.steps)

        stepsResult = HealthSteps.Available(today = 4_200, week = 31_000)
        vm.refreshHealth()
        assertEquals(HealthSteps.Available(today = 4_200, week = 31_000), vm.uiState.value.steps)

        stepsResult = HealthSteps.PermissionMissing
        vm.refreshHealth()
        assertEquals(HealthSteps.PermissionMissing, vm.uiState.value.steps)
    }

    @Test
    fun insightsAreExposedAndFollowTheDataSource() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value.insights.isEmpty)
        assertNull(vm.uiState.value.insights.emptyReason)

        data.insights.value = InsightsUi(emptyReason = InsightsEmptyReason.NOT_ENOUGH_DATA)
        assertEquals(InsightsEmptyReason.NOT_ENOUGH_DATA, vm.uiState.value.insights.emptyReason)
    }
}
