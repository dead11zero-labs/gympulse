package app.gympulse.core.settings

import app.gympulse.core.designsystem.theme.AccentColor
import kotlinx.coroutines.flow.Flow

/**
 * Zentrale, kleine API für App-Einstellungen. Andere Module (Workout, Dashboard) injizieren nur
 * dieses Interface.
 *
 * - Lesen: [settings] liefert immer einen Wert (Standardwerte, solange nichts gespeichert ist) und
 *   emittiert bei jeder Änderung neu. Die Einzel-Flows sind Bequemlichkeit und emittieren nur bei
 *   echter Änderung ihres Werts.
 * - Schreiben: suspend-Setter; Zahlenwerte werden auf [SettingsLimits] begrenzt.
 */
interface UserSettingsRepository {
    val settings: Flow<UserSettings>

    /** Standard-Pausendauer in Sekunden (Standard 90). */
    val restTimerSeconds: Flow<Int>

    /** Wochenstart (Standard Montag). */
    val weekStart: Flow<WeekStart>

    /** Wochenziel: Trainings pro Woche (Standard 3). */
    val weeklyGoal: Flow<Int>

    suspend fun setRestTimerSeconds(seconds: Int)
    suspend fun setWeekStart(weekStart: WeekStart)
    suspend fun setWeeklyGoal(sessionsPerWeek: Int)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setAccentColor(accent: AccentColor)
    suspend fun setLanguage(language: AppLanguage)
    suspend fun setOnboardingCompleted(completed: Boolean)
}
