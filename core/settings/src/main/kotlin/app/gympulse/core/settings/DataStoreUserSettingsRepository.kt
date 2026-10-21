package app.gympulse.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

class DataStoreUserSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : UserSettingsRepository {

    override val settings: Flow<UserSettings> = dataStore.data
        // Eine unlesbare Datei darf die App nicht blockieren: dann gelten die Standardwerte.
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it.toSettings() }
        .distinctUntilChanged()

    override val restTimerSeconds: Flow<Int> = settings.map { it.restTimerSeconds }.distinctUntilChanged()
    override val weekStart: Flow<WeekStart> = settings.map { it.weekStart }.distinctUntilChanged()
    override val weeklyGoal: Flow<Int> = settings.map { it.weeklyGoal }.distinctUntilChanged()

    override suspend fun setRestTimerSeconds(seconds: Int) {
        dataStore.edit { it[Keys.REST_TIMER_SECONDS] = seconds.coerceIn(SettingsLimits.REST_TIMER_SECONDS) }
    }

    override suspend fun setWeekStart(weekStart: WeekStart) {
        dataStore.edit { it[Keys.WEEK_START] = weekStart.name }
    }

    override suspend fun setWeeklyGoal(sessionsPerWeek: Int) {
        dataStore.edit { it[Keys.WEEKLY_GOAL] = sessionsPerWeek.coerceIn(SettingsLimits.WEEKLY_GOAL) }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    private object Keys {
        val REST_TIMER_SECONDS = intPreferencesKey("rest_timer_seconds")
        val WEEK_START = stringPreferencesKey("week_start")
        val WEEKLY_GOAL = intPreferencesKey("weekly_goal")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LANGUAGE = stringPreferencesKey("language")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    private fun Preferences.toSettings() = UserSettings(
        restTimerSeconds = (this[Keys.REST_TIMER_SECONDS] ?: SettingsLimits.DEFAULT_REST_TIMER_SECONDS)
            .coerceIn(SettingsLimits.REST_TIMER_SECONDS),
        weekStart = enumOrDefault(this[Keys.WEEK_START], WeekStart.MONDAY),
        weeklyGoal = (this[Keys.WEEKLY_GOAL] ?: SettingsLimits.DEFAULT_WEEKLY_GOAL)
            .coerceIn(SettingsLimits.WEEKLY_GOAL),
        themeMode = enumOrDefault(this[Keys.THEME_MODE], ThemeMode.DARK),
        dynamicColor = this[Keys.DYNAMIC_COLOR] ?: false,
        language = enumOrDefault(this[Keys.LANGUAGE], AppLanguage.SYSTEM),
        onboardingCompleted = this[Keys.ONBOARDING_COMPLETED] ?: false,
    )
}

private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default
