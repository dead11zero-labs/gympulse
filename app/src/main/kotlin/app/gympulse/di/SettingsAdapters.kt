package app.gympulse.di

import app.gympulse.core.settings.SettingsLimits
import app.gympulse.core.settings.UserSettingsRepository
import app.gympulse.feature.calendar.CalendarWeekStartProvider
import app.gympulse.feature.dashboard.WeekStartProvider
import app.gympulse.feature.dashboard.WeeklyGoalProvider
import app.gympulse.core.workout.timer.RestTimerSettings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

/** Pausendauer aus den Einstellungen (Feature-Modul kennt nur [RestTimerSettings]). */
class UserRestTimerSettings @Inject constructor(
    repository: UserSettingsRepository,
) : RestTimerSettings {
    override val restSeconds: Flow<Int> = repository.restTimerSeconds
}

/**
 * Wochenziel und Wochenstart aus den Einstellungen. Die Provider verlangen [StateFlow]; bis der
 * erste Wert aus dem DataStore kommt, gelten die Standardwerte.
 */
@Singleton
class UserDashboardSettings @Inject constructor(
    repository: UserSettingsRepository,
) : WeeklyGoalProvider, WeekStartProvider, CalendarWeekStartProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val weeklyGoal: StateFlow<Int> = repository.weeklyGoal
        .stateIn(scope, SharingStarted.Eagerly, SettingsLimits.DEFAULT_WEEKLY_GOAL)

    override val weekStartsOn: StateFlow<DayOfWeek> = repository.weekStart
        .map { it.dayOfWeek }
        .stateIn(scope, SharingStarted.Eagerly, DayOfWeek.MONDAY)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsAdaptersModule {
    @Binds
    abstract fun bindRestTimerSettings(impl: UserRestTimerSettings): RestTimerSettings

    @Binds
    abstract fun bindWeeklyGoalProvider(impl: UserDashboardSettings): WeeklyGoalProvider

    @Binds
    abstract fun bindWeekStartProvider(impl: UserDashboardSettings): WeekStartProvider

    @Binds
    abstract fun bindCalendarWeekStartProvider(impl: UserDashboardSettings): CalendarWeekStartProvider
}
