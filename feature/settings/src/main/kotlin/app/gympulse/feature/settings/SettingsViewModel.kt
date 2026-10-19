package app.gympulse.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.data.repository.ProfileRepository
import app.gympulse.core.data.repository.UserProfile
import app.gympulse.core.settings.AppLanguage
import app.gympulse.core.settings.ThemeMode
import app.gympulse.core.settings.UserSettings
import app.gympulse.core.settings.UserSettingsRepository
import app.gympulse.core.settings.WeekStart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val profile: UserProfile = UserProfile(name = null, heightCm = null, bodyWeightKg = null),
)

@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val settingsRepository: UserSettingsRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        profileRepository.observeProfile(),
    ) { settings, profile -> SettingsUiState(settings, profile) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun setName(name: String?) = viewModelScope.launch {
        profileRepository.updateProfile(name, uiState.value.profile.heightCm)
    }

    fun setHeightCm(heightCm: Double?) = viewModelScope.launch {
        profileRepository.updateProfile(uiState.value.profile.name, heightCm)
    }

    fun setBodyWeightKg(weightKg: Double) = viewModelScope.launch {
        profileRepository.recordBodyWeight(weightKg)
    }

    fun setRestTimerSeconds(seconds: Int) = viewModelScope.launch { settingsRepository.setRestTimerSeconds(seconds) }

    fun setWeekStart(weekStart: WeekStart) = viewModelScope.launch { settingsRepository.setWeekStart(weekStart) }

    fun setWeeklyGoal(sessionsPerWeek: Int) = viewModelScope.launch { settingsRepository.setWeeklyGoal(sessionsPerWeek) }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }

    fun setLanguage(language: AppLanguage) = viewModelScope.launch { settingsRepository.setLanguage(language) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
