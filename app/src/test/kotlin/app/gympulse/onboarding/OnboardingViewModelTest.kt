package app.gympulse.onboarding

import app.gympulse.core.settings.AppLanguage
import app.gympulse.core.settings.ThemeMode
import app.gympulse.core.settings.UserSettings
import app.gympulse.core.settings.UserSettingsRepository
import app.gympulse.core.settings.WeekStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private class FakeRepository : UserSettingsRepository {
        val state = MutableStateFlow(UserSettings())
        override val settings: Flow<UserSettings> = state
        override val restTimerSeconds: Flow<Int> = state.map { it.restTimerSeconds }
        override val weekStart: Flow<WeekStart> = state.map { it.weekStart }
        override val weeklyGoal: Flow<Int> = state.map { it.weeklyGoal }

        override suspend fun setRestTimerSeconds(seconds: Int) = update { copy(restTimerSeconds = seconds) }
        override suspend fun setWeekStart(weekStart: WeekStart) = update { copy(weekStart = weekStart) }
        override suspend fun setWeeklyGoal(sessionsPerWeek: Int) = update { copy(weeklyGoal = sessionsPerWeek) }
        override suspend fun setThemeMode(mode: ThemeMode) = update { copy(themeMode = mode) }
        override suspend fun setDynamicColor(enabled: Boolean) = update { copy(dynamicColor = enabled) }
        override suspend fun setLanguage(language: AppLanguage) = update { copy(language = language) }
        override suspend fun setOnboardingCompleted(completed: Boolean) = update { copy(onboardingCompleted = completed) }

        private fun update(change: UserSettings.() -> UserSettings) {
            state.value = state.value.change()
        }
    }

    private lateinit var repository: FakeRepository
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeRepository()
        viewModel = OnboardingViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startsWithDefaultGoal() {
        assertEquals(3, viewModel.weeklyGoal.value)
        assertFalse(repository.state.value.onboardingCompleted)
    }

    @Test
    fun selectWeeklyGoal_coercesToOptions() {
        viewModel.selectWeeklyGoal(5)
        assertEquals(5, viewModel.weeklyGoal.value)
        viewModel.selectWeeklyGoal(0)
        assertEquals(1, viewModel.weeklyGoal.value)
        viewModel.selectWeeklyGoal(50)
        assertEquals(7, viewModel.weeklyGoal.value)
    }

    @Test
    fun complete_savesGoalAndFlag() {
        viewModel.selectWeeklyGoal(4)
        viewModel.complete()

        assertEquals(4, repository.state.value.weeklyGoal)
        assertTrue(repository.state.value.onboardingCompleted)
    }

    @Test
    fun skip_setsFlagAndKeepsGoal() {
        viewModel.selectWeeklyGoal(6)
        viewModel.skip()

        assertEquals(3, repository.state.value.weeklyGoal)
        assertTrue(repository.state.value.onboardingCompleted)
    }
}
