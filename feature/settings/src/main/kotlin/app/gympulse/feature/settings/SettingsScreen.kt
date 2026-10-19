package app.gympulse.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.settings.AppLanguage
import app.gympulse.core.settings.ThemeMode
import app.gympulse.core.settings.WeekStart
import java.text.NumberFormat
import java.util.Locale

/** Dialoge der Einstellungen; es ist höchstens einer gleichzeitig offen. */
private enum class SettingsDialog { NAME, WEIGHT, HEIGHT, REST_TIMER, WEEKLY_GOAL, WEEK_START, THEME, LANGUAGE }

@Composable
internal fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    val dismiss = { dialog = null }

    val settings = state.settings
    val profile = state.profile
    val notSet = stringResource(R.string.settings_not_set)
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = Spacing.maxContentWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            SettingsSection(stringResource(R.string.settings_section_profile)) {
                SettingsRow(
                    headline = stringResource(R.string.settings_name),
                    supporting = profile.name ?: notSet,
                    onClick = { dialog = SettingsDialog.NAME },
                )
                SettingsDivider()
                SettingsRow(
                    headline = stringResource(R.string.settings_body_weight),
                    supporting = profile.bodyWeightKg
                        ?.let { stringResource(R.string.settings_value_kg, formatNumber(it)) }
                        ?: notSet,
                    onClick = { dialog = SettingsDialog.WEIGHT },
                )
                SettingsDivider()
                SettingsRow(
                    headline = stringResource(R.string.settings_height),
                    supporting = profile.heightCm
                        ?.let { stringResource(R.string.settings_value_cm, formatNumber(it)) }
                        ?: notSet,
                    onClick = { dialog = SettingsDialog.HEIGHT },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_training)) {
                SettingsRow(
                    headline = stringResource(R.string.settings_rest_timer),
                    supporting = stringResource(R.string.settings_value_seconds, settings.restTimerSeconds),
                    onClick = { dialog = SettingsDialog.REST_TIMER },
                )
                SettingsDivider()
                SettingsRow(
                    headline = stringResource(R.string.settings_week_start),
                    supporting = weekStartLabel(settings.weekStart),
                    onClick = { dialog = SettingsDialog.WEEK_START },
                )
                SettingsDivider()
                SettingsRow(
                    headline = stringResource(R.string.settings_weekly_goal),
                    supporting = stringResource(R.string.settings_value_per_week, settings.weeklyGoal),
                    onClick = { dialog = SettingsDialog.WEEKLY_GOAL },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_appearance)) {
                SettingsRow(
                    headline = stringResource(R.string.settings_theme),
                    supporting = themeLabel(settings.themeMode),
                    onClick = { dialog = SettingsDialog.THEME },
                )
                SettingsDivider()
                SettingsRow(
                    headline = stringResource(R.string.settings_dynamic_color),
                    supporting = stringResource(
                        if (dynamicColorSupported) {
                            R.string.settings_dynamic_color_summary
                        } else {
                            R.string.settings_dynamic_color_unavailable
                        },
                    ),
                    trailing = {
                        Switch(
                            checked = settings.dynamicColor && dynamicColorSupported,
                            onCheckedChange = viewModel::setDynamicColor,
                            enabled = dynamicColorSupported,
                        )
                    },
                    onClick = if (dynamicColorSupported) {
                        { viewModel.setDynamicColor(!settings.dynamicColor) }
                    } else {
                        null
                    },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_language)) {
                SettingsRow(
                    headline = stringResource(R.string.settings_language),
                    supporting = languageLabel(settings.language),
                    onClick = { dialog = SettingsDialog.LANGUAGE },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_body)) {
                ComingSoonRow(
                    headline = stringResource(R.string.settings_body_measurements),
                    supporting = stringResource(R.string.settings_body_measurements_summary),
                )
            }

            SettingsSection(stringResource(R.string.settings_section_health)) {
                ComingSoonRow(
                    headline = stringResource(R.string.settings_health_connect),
                    supporting = stringResource(R.string.settings_health_connect_summary),
                )
                SettingsDivider()
                ComingSoonRow(
                    headline = stringResource(R.string.settings_samsung_health),
                    supporting = stringResource(R.string.settings_samsung_health_summary),
                )
            }

            SettingsSection(stringResource(R.string.settings_section_data)) {
                ComingSoonRow(
                    headline = stringResource(R.string.settings_export_import),
                    supporting = stringResource(R.string.settings_export_import_summary),
                )
            }

            SettingsSection(stringResource(R.string.settings_section_about)) {
                SettingsRow(
                    headline = stringResource(R.string.settings_version),
                    supporting = appVersion(LocalContext.current),
                )
                SettingsDivider()
                ComingSoonRow(
                    headline = stringResource(R.string.settings_licenses),
                    supporting = stringResource(R.string.settings_licenses_summary),
                )
            }
        }
    }

    when (dialog) {
        null -> Unit
        SettingsDialog.NAME -> ValueInputDialog(
            title = stringResource(R.string.settings_name),
            label = stringResource(R.string.settings_name),
            initialValue = profile.name.orEmpty(),
            errorMessage = stringResource(R.string.settings_error_name),
            keyboardType = KeyboardType.Text,
            validate = InputValidation::name,
            onConfirm = { viewModel.setName(it); dismiss() },
            onDismiss = dismiss,
        )
        SettingsDialog.WEIGHT -> ValueInputDialog(
            title = stringResource(R.string.settings_body_weight),
            label = stringResource(R.string.settings_body_weight),
            initialValue = profile.bodyWeightKg?.let(::formatNumber).orEmpty(),
            errorMessage = stringResource(R.string.settings_error_weight),
            keyboardType = KeyboardType.Decimal,
            validate = InputValidation::weightKg,
            onConfirm = { viewModel.setBodyWeightKg(it); dismiss() },
            onDismiss = dismiss,
            suffix = stringResource(R.string.settings_unit_kg),
            supportingText = stringResource(R.string.settings_body_weight_summary),
        )
        SettingsDialog.HEIGHT -> ValueInputDialog(
            title = stringResource(R.string.settings_height),
            label = stringResource(R.string.settings_height),
            initialValue = profile.heightCm?.let(::formatNumber).orEmpty(),
            errorMessage = stringResource(R.string.settings_error_height),
            keyboardType = KeyboardType.Decimal,
            validate = InputValidation::heightCm,
            onConfirm = { viewModel.setHeightCm(it); dismiss() },
            onDismiss = dismiss,
            suffix = stringResource(R.string.settings_unit_cm),
        )
        SettingsDialog.REST_TIMER -> ValueInputDialog(
            title = stringResource(R.string.settings_rest_timer),
            label = stringResource(R.string.settings_rest_timer),
            initialValue = settings.restTimerSeconds.toString(),
            errorMessage = stringResource(R.string.settings_error_rest_timer),
            keyboardType = KeyboardType.Number,
            validate = InputValidation::restTimerSeconds,
            onConfirm = { viewModel.setRestTimerSeconds(it); dismiss() },
            onDismiss = dismiss,
            suffix = stringResource(R.string.settings_unit_seconds),
        )
        SettingsDialog.WEEKLY_GOAL -> ValueInputDialog(
            title = stringResource(R.string.settings_weekly_goal),
            label = stringResource(R.string.settings_weekly_goal_label),
            initialValue = settings.weeklyGoal.toString(),
            errorMessage = stringResource(R.string.settings_error_weekly_goal),
            keyboardType = KeyboardType.Number,
            validate = InputValidation::weeklyGoal,
            onConfirm = { viewModel.setWeeklyGoal(it); dismiss() },
            onDismiss = dismiss,
        )
        SettingsDialog.WEEK_START -> ChoiceDialog(
            title = stringResource(R.string.settings_week_start),
            options = WeekStart.entries,
            selected = settings.weekStart,
            label = { weekStartLabel(it) },
            onSelect = { viewModel.setWeekStart(it); dismiss() },
            onDismiss = dismiss,
        )
        SettingsDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { themeLabel(it) },
            onSelect = { viewModel.setThemeMode(it); dismiss() },
            onDismiss = dismiss,
        )
        SettingsDialog.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = AppLanguage.entries,
            selected = settings.language,
            label = { languageLabel(it) },
            onSelect = { viewModel.setLanguage(it); dismiss() },
            onDismiss = dismiss,
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsSectionHeader(title)
        SettingsCard(content = content)
    }
}

@Composable
private fun weekStartLabel(weekStart: WeekStart): String = stringResource(
    when (weekStart) {
        WeekStart.MONDAY -> R.string.settings_week_monday
        WeekStart.SUNDAY -> R.string.settings_week_sunday
    },
)

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)

@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.GERMAN -> R.string.settings_language_german
        AppLanguage.ENGLISH -> R.string.settings_language_english
    },
)

/** Zahl in der Sprache des Geräts, ohne Tausendertrenner, höchstens zwei Nachkommastellen. */
private fun formatNumber(value: Double): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        isGroupingUsed = false
        maximumFractionDigits = 2
    }.format(value)

@Composable
private fun appVersion(context: Context): String = remember(context) {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    info.versionName.orEmpty()
}
