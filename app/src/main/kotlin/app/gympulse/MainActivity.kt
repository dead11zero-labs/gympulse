package app.gympulse

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.gympulse.core.designsystem.theme.GymPulseTheme
import app.gympulse.core.settings.AppLanguage
import app.gympulse.core.settings.ThemeMode
import app.gympulse.core.settings.UserSettings
import app.gympulse.core.settings.UserSettingsRepository
import app.gympulse.onboarding.OnboardingRoute
import app.gympulse.onboarding.OnboardingViewModel
import app.gympulse.ui.GymPulseApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Einziger Activity-Einstieg. Eine [AppCompatActivity], damit die App-Sprache
 * (AppCompatDelegate.setApplicationLocales) auch unter Android 12 und älter wirkt.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var settingsRepository: UserSettingsRepository

    private val onboardingViewModel: OnboardingViewModel by viewModels()

    /** Einstellungen, sobald geladen; solange null, bleibt der Splash sichtbar. */
    private var loadedSettings: UserSettings? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { loadedSettings == null }
        enableEdgeToEdge()
        lifecycleScope.launch { keepAppLanguageInSync() }
        setContent {
            // Bis die Einstellungen geladen sind, bleibt der Splash stehen (kein Theme-Flackern).
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            loadedSettings = settings
            settings?.let { current ->
                val systemDark = isSystemInDarkTheme()
                val darkTheme = when (current.themeMode) {
                    ThemeMode.SYSTEM -> systemDark
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
                // Systemleisten folgen dem gewählten Theme statt dem des Systems.
                DisposableEffect(darkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                        navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                    )
                    onDispose { }
                }
                GymPulseTheme(darkTheme = darkTheme, dynamicColor = current.dynamicColor, accent = current.accentColor) {
                    if (current.onboardingCompleted) {
                        GymPulseApp()
                    } else {
                        OnboardingRoute(viewModel = onboardingViewModel)
                    }
                }
            }
        }
    }

    /**
     * Beim Start gilt die vom System bzw. AppCompat gespeicherte Sprache (sie kann in den
     * Android-Einstellungen geändert worden sein). Danach wird jede Änderung aus den
     * Einstellungen per AppCompatDelegate angewendet; die Activity wird dabei neu erzeugt.
     */
    private suspend fun keepAppLanguageInSync() {
        val active = AppLanguage.fromLanguageTag(AppCompatDelegate.getApplicationLocales().toLanguageTags())
        if (settingsRepository.settings.first().language != active) {
            settingsRepository.setLanguage(active)
        }
        settingsRepository.settings
            .map { it.language }
            .distinctUntilChanged()
            .collect { language ->
                val desired = LocaleListCompat.forLanguageTags(language.languageTag)
                val current = AppCompatDelegate.getApplicationLocales()
                if (current.toLanguageTags() != desired.toLanguageTags()) {
                    AppCompatDelegate.setApplicationLocales(desired)
                }
            }
    }

    private companion object {
        // Scrim der Navigationsleiste im Edge-to-Edge-Modus (Standardwerte der Activity-Bibliothek).
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
