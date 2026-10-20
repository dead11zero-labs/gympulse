package app.gympulse.feature.calendar

import app.gympulse.core.data.repository.CompletedSessionSummary
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.model.ScheduleOverride
import app.gympulse.core.model.ScheduleOverrideType
import app.gympulse.core.model.ScheduleRule
import app.gympulse.core.model.WorkoutPlan
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private val zone = ZoneId.of("UTC")

    // Mittwoch, 21.10.2026; die Woche beginnt am Montag, 19.10.
    private val now = Instant.parse("2026-10-21T09:30:00Z")
    private val today = LocalDate.of(2026, 10, 21)

    private val time = object : TimeProvider {
        override fun now(): Instant = now
        override fun zone(): ZoneId = zone
    }

    private class FakeGate(enabled: Boolean) : FeatureGate {
        val enabled = MutableStateFlow(enabled)
        override fun isEnabled(feature: Feature): Boolean = enabled.value
        override fun observe(feature: Feature): Flow<Boolean> = enabled
    }

    private class FakeWeekStart(day: DayOfWeek = DayOfWeek.MONDAY) : CalendarWeekStartProvider {
        override val weekStartsOn: MutableStateFlow<DayOfWeek> = MutableStateFlow(day)
    }

    private class FakeDataSource : CalendarDataSource {
        val plans = MutableStateFlow(
            listOf(
                WorkoutPlan("push", "Push Day", null, null, false, emptyList()),
                WorkoutPlan("pull", "Pull Day", null, null, false, emptyList()),
            ),
        )
        val rules = MutableStateFlow<List<ScheduleRule>>(emptyList())
        val overrides = MutableStateFlow<List<ScheduleOverride>>(emptyList())
        val sessions = MutableStateFlow<List<CompletedSessionSummary>>(emptyList())
        val calls = mutableListOf<String>()

        override fun observePlans(): Flow<List<WorkoutPlan>> = plans

        override fun observeRules(): Flow<List<ScheduleRule>> = rules

        override fun observeOverrides(from: LocalDate, to: LocalDate): Flow<List<ScheduleOverride>> =
            overrides.map { list -> list.filter { it.date >= from && it.date <= to } }

        override fun observeCompletedSessions(from: Instant, to: Instant): Flow<List<CompletedSessionSummary>> =
            sessions.map { list -> list.filter { it.endedAt >= from && it.endedAt < to } }

        override suspend fun setWeekdayPlan(weekday: DayOfWeek, planId: String?) {
            calls += "weekday:$weekday:$planId"
        }

        override suspend fun skipDay(date: LocalDate) {
            calls += "skip:$date"
        }

        override suspend fun moveDay(from: LocalDate, to: LocalDate, planId: String) {
            calls += "move:$from:$to:$planId"
        }

        override suspend fun addPlan(date: LocalDate, planId: String) {
            calls += "add:$date:$planId"
        }

        override suspend fun clearOverride(date: LocalDate) {
            calls += "clear:$date"
        }
    }

    private val data = FakeDataSource()
    private val gate = FakeGate(enabled = true)
    private val weekStart = FakeWeekStart()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = CalendarViewModel(data, gate, weekStart, time)

    private fun kotlinx.coroutines.test.TestScope.collectState(vm: CalendarViewModel): StateFlow<CalendarUiState> {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        return vm.uiState
    }

    private fun rule(planId: String, weekday: DayOfWeek) =
        ScheduleRule("r-$planId", planId, weekday.value, LocalDate.of(2026, 1, 1), null)

    @Test
    fun featureDisabled_showsLockedStateWithoutContent() = runTest {
        gate.enabled.value = false
        data.rules.value = listOf(rule("push", DayOfWeek.MONDAY))
        val state = collectState(viewModel()).value

        assertFalse(state.isLoading)
        assertFalse(state.featureEnabled)
        assertTrue(state.weeks.isEmpty())
        assertTrue(state.weekScheme.isEmpty())
    }

    @Test
    fun featureToggledOnAtRuntime_loadsContent() = runTest {
        gate.enabled.value = false
        val state = collectState(viewModel())
        assertFalse(state.value.featureEnabled)

        gate.enabled.value = true
        assertTrue(state.value.featureEnabled)
        assertEquals(1, state.value.weeks.size)
    }

    @Test
    fun enabled_startsInWeekModeWithTodaySelected() = runTest {
        val state = collectState(viewModel()).value

        assertFalse(state.isLoading)
        assertTrue(state.featureEnabled)
        assertEquals(CalendarMode.WEEK, state.mode)
        assertEquals(today, state.selectedDate)
        assertEquals(LocalDate.of(2026, 10, 19), state.periodStart)
        assertEquals(7, state.weeks.single().size)
        assertEquals(today, state.selectedDay?.date)
        assertEquals(listOf("Push Day", "Pull Day"), state.plans.map { it.name })
    }

    @Test
    fun weekScheme_listsSevenWeekdaysFromWeekStartWithAssignedPlans() = runTest {
        data.rules.value = listOf(rule("push", DayOfWeek.MONDAY), rule("pull", DayOfWeek.WEDNESDAY))
        val scheme = collectState(viewModel()).value.weekScheme

        assertEquals(7, scheme.size)
        assertEquals(DayOfWeek.MONDAY, scheme.first().weekday)
        assertEquals("push", scheme[0].planId)
        assertEquals(null, scheme[1].planId)
        assertEquals("pull", scheme[2].planId)
    }

    @Test
    fun weekStartSunday_reordersWeekAndScheme() = runTest {
        weekStart.weekStartsOn.value = DayOfWeek.SUNDAY
        val state = collectState(viewModel()).value

        assertEquals(LocalDate.of(2026, 10, 18), state.periodStart)
        assertEquals(DayOfWeek.SUNDAY, state.weekScheme.first().weekday)
        assertEquals(DayOfWeek.SUNDAY, state.weeks.single().first().date.dayOfWeek)
    }

    @Test
    fun weekStartChange_isAppliedLive() = runTest {
        val state = collectState(viewModel())
        assertEquals(LocalDate.of(2026, 10, 19), state.value.periodStart)

        weekStart.weekStartsOn.value = DayOfWeek.SUNDAY
        assertEquals(LocalDate.of(2026, 10, 18), state.value.periodStart)
    }

    @Test
    fun scheduleAndOverrides_showUpInDays() = runTest {
        data.rules.value = listOf(rule("push", DayOfWeek.MONDAY), rule("pull", DayOfWeek.WEDNESDAY))
        data.overrides.value = listOf(
            ScheduleOverride("o1", today, null, ScheduleOverrideType.SKIPPED),
            ScheduleOverride("o2", today.plusDays(1), "pull", ScheduleOverrideType.MOVED),
        )
        val days = collectState(viewModel()).value.weeks.single()

        assertEquals(DayStatus.SKIPPED, days[2].status)
        assertEquals(listOf("pull"), days[3].plannedPlanIds)
        assertEquals(ScheduleOverrideType.MOVED, days[3].override)
        assertEquals(DayStatus.MISSED, days[0].status)
    }

    @Test
    fun history_listsCompletedSessionsOnTheirDay() = runTest {
        val end = Instant.parse("2026-10-20T18:00:00Z")
        data.sessions.value = listOf(
            CompletedSessionSummary("s1", "Push Day", end.minusSeconds(3600), end, 4200.0),
        )
        val state = collectState(viewModel())
        val tuesday = state.value.weeks.single()[1]

        assertEquals(DayStatus.COMPLETED, tuesday.status)
        assertEquals("s1", tuesday.sessions.single().id)
        assertTrue(state.value.weeks.single().filter { it.date != tuesday.date }.all { it.sessions.isEmpty() })
    }

    @Test
    fun monthMode_showsFullGridAndKeepsSelection() = runTest {
        val vm = viewModel()
        val state = collectState(vm)
        vm.setMode(CalendarMode.MONTH)

        assertEquals(CalendarMode.MONTH, state.value.mode)
        assertEquals(5, state.value.weeks.size)
        assertEquals(LocalDate.of(2026, 10, 1), state.value.periodStart)
        assertEquals(today, state.value.selectedDate)
        assertFalse(state.value.weeks.first().first().inPeriod)
    }

    @Test
    fun monthMode_loadsSessionsAcrossTheGrid() = runTest {
        val end = Instant.parse("2026-10-02T18:00:00Z")
        data.sessions.value = listOf(CompletedSessionSummary("s1", null, end.minusSeconds(60), end, 0.0))
        val vm = viewModel()
        val state = collectState(vm)
        vm.setMode(CalendarMode.MONTH)

        val day = state.value.weeks.flatten().first { it.date == LocalDate.of(2026, 10, 2) }
        assertEquals(DayStatus.COMPLETED, day.status)
    }

    @Test
    fun weekNavigation_movesByWeekAndSelectsTodayOnlyWhenVisible() = runTest {
        val vm = viewModel()
        val state = collectState(vm)

        vm.next()
        assertEquals(LocalDate.of(2026, 10, 26), state.value.periodStart)
        assertEquals(LocalDate.of(2026, 10, 26), state.value.selectedDate)

        vm.previous()
        assertEquals(LocalDate.of(2026, 10, 19), state.value.periodStart)
        assertEquals(today, state.value.selectedDate)

        vm.previous()
        assertEquals(LocalDate.of(2026, 10, 12), state.value.periodStart)
        assertEquals(LocalDate.of(2026, 10, 12), state.value.selectedDate)
    }

    @Test
    fun monthNavigation_movesByMonthAndSelectsFirstDay() = runTest {
        val vm = viewModel()
        val state = collectState(vm)
        vm.setMode(CalendarMode.MONTH)

        vm.next()
        assertEquals(LocalDate.of(2026, 11, 1), state.value.periodStart)
        assertEquals(LocalDate.of(2026, 11, 1), state.value.selectedDate)

        vm.previous()
        vm.previous()
        assertEquals(LocalDate.of(2026, 9, 1), state.value.periodStart)
        assertEquals(LocalDate.of(2026, 9, 1), state.value.selectedDate)
    }

    @Test
    fun goToToday_resetsPeriodAndSelection() = runTest {
        val vm = viewModel()
        val state = collectState(vm)
        vm.next()
        vm.next()
        vm.goToToday()

        assertEquals(LocalDate.of(2026, 10, 19), state.value.periodStart)
        assertEquals(today, state.value.selectedDate)
    }

    @Test
    fun selectDate_inAdjacentMonth_switchesMonth() = runTest {
        val vm = viewModel()
        val state = collectState(vm)
        vm.setMode(CalendarMode.MONTH)
        vm.selectDate(LocalDate.of(2026, 11, 1))

        assertEquals(LocalDate.of(2026, 11, 1), state.value.periodStart)
        assertEquals(LocalDate.of(2026, 11, 1), state.value.selectedDate)
    }

    @Test
    fun selectDate_inWeekMode_onlyChangesSelection() = runTest {
        val vm = viewModel()
        val state = collectState(vm)
        vm.selectDate(LocalDate.of(2026, 10, 23))

        assertEquals(LocalDate.of(2026, 10, 19), state.value.periodStart)
        assertEquals(LocalDate.of(2026, 10, 23), state.value.selectedDate)
        assertNotNull(state.value.selectedDay)
    }

    @Test
    fun actions_areForwardedToDataSource() = runTest {
        val vm = viewModel()
        collectState(vm)
        vm.setWeekdayPlan(DayOfWeek.FRIDAY, "push")
        vm.setWeekdayPlan(DayOfWeek.FRIDAY, null)
        vm.skipDay(today)
        vm.moveDay(today, today.plusDays(1), "pull")
        vm.addPlan(today.plusDays(2), "push")
        vm.clearOverride(today)

        assertEquals(
            listOf(
                "weekday:FRIDAY:push",
                "weekday:FRIDAY:null",
                "skip:2026-10-21",
                "move:2026-10-21:2026-10-22:pull",
                "add:2026-10-23:push",
                "clear:2026-10-21",
            ),
            data.calls,
        )
    }
}
