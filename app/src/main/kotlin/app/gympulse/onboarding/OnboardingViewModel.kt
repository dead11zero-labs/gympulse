package app.gympulse.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gympulse.core.settings.SettingsLimits
import app.gympulse.core.settings.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Wochenziele, die im Onboarding zur Auswahl stehen (Feintuning später in den Einstellungen). */
val OnboardingGoalOptions: IntRange = 1..7

/** Hält die Auswahl des Onboardings und speichert beim Abschluss Wochenziel und Abschluss-Flag. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: UserSettingsRepository,
) : ViewModel() {

    private val _weeklyGoal = MutableStateFlow(SettingsLimits.DEFAULT_WEEKLY_GOAL)
    val weeklyGoal: StateFlow<Int> = _weeklyGoal.asStateFlow()

    fun selectWeeklyGoal(sessionsPerWeek: Int) {
        _weeklyGoal.value = sessionsPerWeek.coerceIn(OnboardingGoalOptions)
    }

    /** Letzte Seite: gewähltes Ziel speichern und Onboarding beenden. */
    fun complete() {
        viewModelScope.launch {
            settingsRepository.setWeeklyGoal(_weeklyGoal.value)
            settingsRepository.setOnboardingCompleted(true)
        }
    }

    /** Überspringen: Standardziel bleibt, das Onboarding erscheint nicht erneut. */
    fun skip() {
        viewModelScope.launch { settingsRepository.setOnboardingCompleted(true) }
    }
}
