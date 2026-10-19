package app.gympulse.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.data.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Wochenvolumen für das Balkendiagramm. */
data class WeeklyVolumeUi(
    val weekStart: LocalDate,
    /** Sieben Werte in kg, beginnend mit [weekStart]. */
    val dailyKg: List<Double>,
    val totalKg: Double,
    val previousTotalKg: Double,
    /** Veränderung zur Vorwoche in Prozent, null ohne Vergleichswert. */
    val changePercent: Int?,
    /** Index des heutigen Tages in [dailyKg]. */
    val todayIndex: Int,
) {
    val isEmpty: Boolean get() = totalKg <= 0.0
}

data class WeeklyProgressUi(
    val completed: Int,
    val goal: Int,
    val fraction: Float,
    val streakWeeks: Int,
) {
    val goalReached: Boolean get() = goal > 0 && completed >= goal
}

data class RecentSessionUi(
    val id: String,
    /** Planname, null bei freiem Workout. */
    val title: String?,
    val date: LocalDate,
    val durationMinutes: Long,
    val volumeKg: Double,
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val today: LocalDate,
    val zone: ZoneId,
    val greeting: Greeting,
    val weeklyVolume: WeeklyVolumeUi? = null,
    val weeklyProgress: WeeklyProgressUi? = null,
    val recentSessions: List<RecentSessionUi> = emptyList(),
    val activeWorkout: ActiveWorkout? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val data: DashboardDataSource,
    private val goalProvider: WeeklyGoalProvider,
    private val weekStartProvider: WeekStartProvider,
    private val time: TimeProvider,
) : ViewModel() {

    private data class WeekWidgets(val volume: WeeklyVolumeUi, val progress: WeeklyProgressUi)

    private val weekWidgets: Flow<WeekWidgets> = weekStartProvider.weekStartsOn.flatMapLatest { weekStartsOn ->
        buildWeekWidgets(weekStartsOn)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        weekWidgets,
        data.observeRecentSessions(RECENT_LIMIT),
        data.observeActiveWorkout(),
    ) { widgets, recent, active ->
        val zone = time.zone()
        val now = time.now().atZone(zone)
        DashboardUiState(
            isLoading = false,
            today = now.toLocalDate(),
            zone = zone,
            greeting = greetingFor(now.hour),
            weeklyVolume = widgets.volume,
            weeklyProgress = widgets.progress,
            recentSessions = recent.map { session ->
                RecentSessionUi(
                    id = session.id,
                    title = session.title,
                    date = session.endedAt.atZone(zone).toLocalDate(),
                    durationMinutes = Duration.between(session.startedAt, session.endedAt).toMinutes().coerceAtLeast(0),
                    volumeKg = session.volumeKg,
                )
            },
            activeWorkout = active,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initialState())

    private fun initialState(): DashboardUiState {
        val now = time.now().atZone(time.zone())
        return DashboardUiState(today = now.toLocalDate(), zone = time.zone(), greeting = greetingFor(now.hour))
    }

    private fun buildWeekWidgets(weekStartsOn: DayOfWeek): Flow<WeekWidgets> {
        val zone = time.zone()
        val today = time.now().atZone(zone).toLocalDate()
        val weekStart = weekStartOf(today, weekStartsOn)
        val historyFrom = weekStart.minusWeeks(HISTORY_WEEKS).atStartOfDay(zone).toInstant()
        val historyTo = weekStart.plusWeeks(1).atStartOfDay(zone).toInstant()
        return combine(
            data.observeWeeklyVolume(0, weekStartsOn),
            data.observeWeeklyVolume(-1, weekStartsOn),
            data.observeSessionEndTimes(historyFrom, historyTo),
            goalProvider.weeklyGoal,
        ) { current, previous, endTimes, goal ->
            val perWeek = sessionsPerWeek(endTimes, zone, weekStartsOn)
            val completed = perWeek[weekStart] ?: 0
            WeekWidgets(
                volume = WeeklyVolumeUi(
                    weekStart = current.weekStart,
                    dailyKg = current.dailyVolumeKg,
                    totalKg = current.totalKg,
                    previousTotalKg = previous.totalKg,
                    changePercent = percentChange(current.totalKg, previous.totalKg),
                    todayIndex = (today.toEpochDay() - current.weekStart.toEpochDay()).toInt(),
                ),
                progress = WeeklyProgressUi(
                    completed = completed,
                    goal = goal,
                    fraction = goalProgress(completed, goal),
                    streakWeeks = weeklyStreak(perWeek, weekStart, goal),
                ),
            )
        }
    }

    private companion object {
        const val RECENT_LIMIT = 5
        const val HISTORY_WEEKS = 52L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
