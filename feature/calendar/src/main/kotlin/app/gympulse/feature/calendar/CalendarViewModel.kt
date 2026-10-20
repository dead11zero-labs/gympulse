package app.gympulse.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class PlanOption(val id: String, val name: String)

/** Aktueller Plan eines Wochentags im Wochenschema; null = Ruhetag. */
data class WeekdaySlot(val weekday: DayOfWeek, val planId: String?)

data class CalendarUiState(
    val isLoading: Boolean = true,
    /** False: Premium-Hinweis statt Inhalt. */
    val featureEnabled: Boolean = false,
    val mode: CalendarMode = CalendarMode.WEEK,
    val today: LocalDate,
    val zone: ZoneId = ZoneId.systemDefault(),
    val weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
    /** Montag der Woche bzw. erster Tag des Monats. */
    val periodStart: LocalDate,
    /** Eine Woche in der Wochenansicht, 4 bis 6 Wochen in der Monatsansicht. */
    val weeks: List<List<CalendarDay>> = emptyList(),
    val selectedDate: LocalDate,
    val plans: List<PlanOption> = emptyList(),
    /** Sieben Einträge, beginnend mit dem Wochenstart. */
    val weekScheme: List<WeekdaySlot> = emptyList(),
) {
    val selectedDay: CalendarDay? get() = weeks.flatten().firstOrNull { it.date == selectedDate }

    val planNames: Map<String, String> get() = plans.associate { it.id to it.name }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val data: CalendarDataSource,
    private val featureGate: FeatureGate,
    private val weekStartProvider: CalendarWeekStartProvider,
    private val time: TimeProvider,
) : ViewModel() {

    private data class View(
        val mode: CalendarMode,
        val anchor: LocalDate,
        val selected: LocalDate,
        val weekStartsOn: DayOfWeek,
    )

    private val mode = MutableStateFlow(CalendarMode.WEEK)
    private val anchor = MutableStateFlow(today())
    private val selected = MutableStateFlow(today())

    val uiState: StateFlow<CalendarUiState> = featureGate.observe(Feature.CALENDAR)
        .flatMapLatest { enabled -> if (enabled) enabledState() else flowOf(lockedState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initialState())

    private fun today(): LocalDate = time.now().atZone(time.zone()).toLocalDate()

    private fun initialState(): CalendarUiState =
        CalendarUiState(today = today(), zone = time.zone(), periodStart = today(), selectedDate = today())

    private fun lockedState(): CalendarUiState = initialState().copy(isLoading = false, featureEnabled = false)

    private fun periodWeeks(view: View): List<List<LocalDate>> = when (view.mode) {
        CalendarMode.WEEK -> listOf(weekDates(weekStartOf(view.anchor, view.weekStartsOn)))
        CalendarMode.MONTH -> monthWeeks(YearMonth.from(view.anchor), view.weekStartsOn)
    }

    private fun enabledState(): Flow<CalendarUiState> =
        combine(mode, anchor, selected, weekStartProvider.weekStartsOn) { m, a, s, ws -> View(m, a, s, ws) }
            .flatMapLatest { view ->
                val zone = time.zone()
                val weeks = periodWeeks(view)
                val first = weeks.first().first()
                val last = weeks.last().last()
                combine(
                    data.observePlans(),
                    data.observeRules(),
                    data.observeOverrides(first, last),
                    data.observeCompletedSessions(
                        first.atStartOfDay(zone).toInstant(),
                        last.plusDays(1).atStartOfDay(zone).toInstant(),
                    ),
                ) { plans, rules, overrides, sessions ->
                    val today = today()
                    val weekStart = weekStartOf(today, view.weekStartsOn)
                    CalendarUiState(
                        isLoading = false,
                        featureEnabled = true,
                        mode = view.mode,
                        today = today,
                        zone = zone,
                        weekStartsOn = view.weekStartsOn,
                        periodStart = if (view.mode == CalendarMode.MONTH) YearMonth.from(view.anchor).atDay(1) else first,
                        weeks = buildCalendarWeeks(
                            weeks = weeks,
                            today = today,
                            zone = zone,
                            rules = rules,
                            overrides = overrides,
                            sessions = sessions,
                            period = if (view.mode == CalendarMode.MONTH) YearMonth.from(view.anchor) else null,
                        ),
                        selectedDate = view.selected,
                        plans = plans.map { PlanOption(it.id, it.name) },
                        weekScheme = weekDates(weekStart).map { date ->
                            WeekdaySlot(date.dayOfWeek, currentPlanForWeekday(rules, date.dayOfWeek, today))
                        },
                    )
                }
            }

    fun setMode(newMode: CalendarMode) {
        if (mode.value == newMode) return
        mode.value = newMode
        anchor.value = selected.value
    }

    fun previous() = shift(-1)

    fun next() = shift(1)

    fun goToToday() {
        val today = today()
        anchor.value = today
        selected.value = today
    }

    fun selectDate(date: LocalDate) {
        selected.value = date
        // Ein Tag aus dem Nachbarmonat wechselt den angezeigten Monat.
        if (mode.value == CalendarMode.MONTH && YearMonth.from(date) != YearMonth.from(anchor.value)) {
            anchor.value = date
        }
    }

    private fun shift(direction: Long) {
        val newAnchor = when (mode.value) {
            CalendarMode.WEEK -> anchor.value.plusWeeks(direction)
            CalendarMode.MONTH -> YearMonth.from(anchor.value).plusMonths(direction).atDay(1)
        }
        anchor.value = newAnchor
        val today = today()
        val weekStartsOn = weekStartProvider.weekStartsOn.value
        selected.value = when (mode.value) {
            CalendarMode.WEEK -> {
                val days = weekDates(weekStartOf(newAnchor, weekStartsOn))
                defaultSelection(days, today, days.first())
            }
            CalendarMode.MONTH -> {
                val month = YearMonth.from(newAnchor)
                defaultSelection(List(month.lengthOfMonth()) { month.atDay(it + 1) }, today, month.atDay(1))
            }
        }
    }

    fun setWeekdayPlan(weekday: DayOfWeek, planId: String?) = launch { data.setWeekdayPlan(weekday, planId) }

    fun skipDay(date: LocalDate) = launch { data.skipDay(date) }

    fun moveDay(from: LocalDate, to: LocalDate, planId: String) = launch { data.moveDay(from, to, planId) }

    fun addPlan(date: LocalDate, planId: String) = launch { data.addPlan(date, planId) }

    fun clearOverride(date: LocalDate) = launch { data.clearOverride(date) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
