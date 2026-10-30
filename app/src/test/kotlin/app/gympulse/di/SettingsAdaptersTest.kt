package app.gympulse.di

import app.gympulse.core.settings.AppLanguage
import app.gympulse.core.settings.ThemeMode
import app.gympulse.core.settings.UserSettings
import app.gympulse.core.settings.UserSettingsRepository
import app.gympulse.core.settings.WeekStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class SettingsAdaptersTest {

    private class FakeRepository : UserSettingsRepository {
        val state = MutableStateFlow(UserSettings())
        override val settings: Flow<UserSettings> = state
        override val restTimerSeconds: Flow<Int> = state.map { it.restTimerSeconds }
        override val weekStart: Flow<WeekStart> = state.map { it.weekStart }
        override val weeklyGoal: Flow<Int> = state.map { it.weeklyGoal }

        override suspend fun setRestTimerSeconds(seconds: Int) = state.update { it.copy(restTimerSeconds = seconds) }
        override suspend fun setWeekStart(weekStart: WeekStart) = state.update { it.copy(weekStart = weekStart) }
        override suspend fun setWeeklyGoal(sessionsPerWeek: Int) = state.update { it.copy(weeklyGoal = sessionsPerWeek) }
        override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }
        override suspend fun setDynamicColor(enabled: Boolean) = state.update { it.copy(dynamicColor = enabled) }
        override suspend fun setAccentColor(accent: app.gympulse.core.designsystem.theme.AccentColor) = state.update { it.copy(accentColor = accent) }
        override suspend fun setLanguage(language: AppLanguage) = state.update { it.copy(language = language) }
        override suspend fun setOnboardingCompleted(completed: Boolean) =
            state.update { it.copy(onboardingCompleted = completed) }

        private fun MutableStateFlow<UserSettings>.update(block: (UserSettings) -> UserSettings) {
            value = block(value)
        }
    }

    @Test
    fun restTimerFollowsRepository() = runTest {
        val repository = FakeRepository()
        val settings = UserRestTimerSettings(repository)
        assertEquals(90, settings.restSeconds.first())
        repository.setRestTimerSeconds(120)
        assertEquals(120, settings.restSeconds.first())
    }

    @Test
    fun dashboardSettingsFollowRepository() = runTest {
        val repository = FakeRepository()
        repository.setWeeklyGoal(5)
        repository.setWeekStart(WeekStart.SUNDAY)
        val settings = UserDashboardSettings(repository)
        assertEquals(5, settings.weeklyGoal.first { it == 5 })
        assertEquals(DayOfWeek.SUNDAY, settings.weekStartsOn.first { it == DayOfWeek.SUNDAY })
    }
}
