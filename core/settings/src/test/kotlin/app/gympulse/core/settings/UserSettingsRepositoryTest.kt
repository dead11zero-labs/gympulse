package app.gympulse.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UserSettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: UserSettingsRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        file = File(tmp.root, "settings.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        repository = DataStoreUserSettingsRepository(dataStore)
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun defaults_whenNothingStored() = runTest {
        val settings = repository.settings.first()

        assertEquals(90, settings.restTimerSeconds)
        assertEquals(WeekStart.MONDAY, settings.weekStart)
        assertEquals(3, settings.weeklyGoal)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(true, settings.dynamicColor)
        assertEquals(AppLanguage.SYSTEM, settings.language)
        assertEquals(false, settings.onboardingCompleted)
    }

    @Test
    fun setters_roundTrip() = runTest {
        repository.setRestTimerSeconds(120)
        repository.setWeekStart(WeekStart.SUNDAY)
        repository.setWeeklyGoal(5)
        repository.setThemeMode(ThemeMode.DARK)
        repository.setDynamicColor(false)
        repository.setLanguage(AppLanguage.GERMAN)
        repository.setOnboardingCompleted(true)

        assertEquals(
            UserSettings(
                120, WeekStart.SUNDAY, 5, ThemeMode.DARK,
                dynamicColor = false, language = AppLanguage.GERMAN, onboardingCompleted = true,
            ),
            repository.settings.first(),
        )
        assertEquals(120, repository.restTimerSeconds.first())
        assertEquals(WeekStart.SUNDAY, repository.weekStart.first())
        assertEquals(5, repository.weeklyGoal.first())
    }

    @Test
    fun setters_coerceOutOfRangeValues() = runTest {
        repository.setRestTimerSeconds(1)
        assertEquals(10, repository.restTimerSeconds.first())
        repository.setRestTimerSeconds(100_000)
        assertEquals(600, repository.restTimerSeconds.first())
        repository.setWeeklyGoal(0)
        assertEquals(1, repository.weeklyGoal.first())
        repository.setWeeklyGoal(99)
        assertEquals(14, repository.weeklyGoal.first())
    }

    @Test
    fun unknownStoredEnumNames_fallBackToDefaults() = runTest {
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                set(stringPreferencesKey("theme_mode"), "SEPIA")
                set(stringPreferencesKey("week_start"), "FRIDAY")
            }
        }

        val settings = repository.settings.first()

        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(WeekStart.MONDAY, settings.weekStart)
    }

    @Test
    fun language_fromLanguageTag() {
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromLanguageTag("de"))
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromLanguageTag("de-DE"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("en_US"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag("fr"))
    }
}
