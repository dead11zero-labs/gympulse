package app.gympulse.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.designsystem.adaptive.currentWindowWidthClass
import app.gympulse.core.designsystem.component.GymPulseCard
import app.gympulse.core.designsystem.component.GymPulseCardVariant
import app.gympulse.core.designsystem.component.LabelCaps
import app.gympulse.core.designsystem.component.ListRowIcon
import app.gympulse.core.designsystem.component.StatTile
import app.gympulse.core.designsystem.icon.ActionIcons
import app.gympulse.core.designsystem.theme.ExpressiveShapes
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.settings.AppLanguage
import app.gympulse.core.designsystem.theme.AccentColor
import app.gympulse.core.settings.ThemeMode
import app.gympulse.core.settings.WeekStart
import app.gympulse.feature.settings.ai.AiCoachSettingsEntry
import app.gympulse.feature.settings.data.DataSettingsRows
import app.gympulse.feature.settings.wear.WearSettingsEntry
import java.text.NumberFormat
import java.util.Locale

/** Dialoge der Einstellungen; es ist höchstens einer gleichzeitig offen. */
private enum class SettingsDialog { NAME, WEIGHT, HEIGHT, REST_TIMER, WEEKLY_GOAL, WEEK_START, THEME, ACCENT, LANGUAGE }

@Composable
internal fun SettingsScreen(
    onOpenBodyMetrics: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onOpenCloudBackup: () -> Unit,
    onOpenWear: () -> Unit,
    onOpenAiCoach: () -> Unit,
    onOpenAbout: () -> Unit,
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
                .padding(horizontal = currentWindowWidthClass().horizontalMargin, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SettingsSectionHeader(stringResource(R.string.settings_section_profile))
                ProfileHero(
                    name = profile.name ?: notSet,
                    weight = profile.bodyWeightKg?.let(::formatNumber),
                    height = profile.heightCm?.let(::formatNumber),
                    onEditName = { dialog = SettingsDialog.NAME },
                    onEditWeight = { dialog = SettingsDialog.WEIGHT },
                    onEditHeight = { dialog = SettingsDialog.HEIGHT },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_training)) {
                SettingsRow(
                    headline = stringResource(R.string.settings_rest_timer),
                    supporting = stringResource(R.string.settings_value_seconds, settings.restTimerSeconds),
                    icon = Icons.Filled.Notifications,
                    onClick = { dialog = SettingsDialog.REST_TIMER },
                )
                SettingsDivider(withIcon = true)
                SettingsRow(
                    headline = stringResource(R.string.settings_week_start),
                    supporting = weekStartLabel(settings.weekStart),
                    icon = Icons.Filled.DateRange,
                    onClick = { dialog = SettingsDialog.WEEK_START },
                )
                SettingsDivider(withIcon = true)
                SettingsRow(
                    headline = stringResource(R.string.settings_weekly_goal),
                    supporting = stringResource(R.string.settings_value_per_week, settings.weeklyGoal),
                    icon = Icons.Filled.CheckCircle,
                    onClick = { dialog = SettingsDialog.WEEKLY_GOAL },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_appearance)) {
                SettingsRow(
                    headline = stringResource(R.string.settings_theme),
                    supporting = themeLabel(settings.themeMode),
                    icon = Icons.Filled.Create,
                    onClick = { dialog = SettingsDialog.THEME },
                )
                SettingsDivider(withIcon = true)
                SettingsRow(
                    headline = stringResource(R.string.settings_dynamic_color),
                    supporting = stringResource(
                        if (dynamicColorSupported) {
                            R.string.settings_dynamic_color_summary
                        } else {
                            R.string.settings_dynamic_color_unavailable
                        },
                    ),
                    icon = Icons.Filled.Star,
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
                SettingsDivider(withIcon = true)
                SettingsRow(
                    headline = stringResource(R.string.settings_accent_color),
                    supporting = stringResource(
                        if (settings.dynamicColor && dynamicColorSupported) {
                            R.string.settings_accent_color_overridden
                        } else {
                            accentLabelRes(settings.accentColor)
                        },
                    ),
                    icon = Icons.Filled.Favorite,
                    trailing = { AccentSwatch(settings.accentColor) },
                    onClick = { dialog = SettingsDialog.ACCENT },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_language)) {
                SettingsRow(
                    headline = stringResource(R.string.settings_language),
                    supporting = languageLabel(settings.language),
                    icon = Icons.Filled.Place,
                    onClick = { dialog = SettingsDialog.LANGUAGE },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_body)) {
                SettingsRow(
                    headline = stringResource(R.string.settings_body_measurements),
                    supporting = stringResource(R.string.settings_body_measurements_summary),
                    icon = Icons.Filled.Face,
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = onOpenBodyMetrics,
                )
            }

            SettingsSection(stringResource(R.string.settings_section_health)) {
                SettingsLinkRow(
                    headline = stringResource(R.string.settings_health_connect),
                    supporting = stringResource(R.string.settings_health_connect_entry_summary),
                    icon = ActionIcons.HealthAndSafety,
                    onClick = onOpenHealthConnect,
                )
            }

            WearSettingsEntry(onClick = onOpenWear)

            AiCoachSettingsEntry(onClick = onOpenAiCoach)

            SettingsSection(stringResource(R.string.settings_section_data)) {
                DataSettingsRows()
            }

            SettingsSection(stringResource(R.string.settings_section_cloud)) {
                SettingsLinkRow(
                    headline = stringResource(R.string.cloud_backup),
                    supporting = stringResource(R.string.cloud_backup_entry_summary),
                    icon = ActionIcons.CloudSync,
                    onClick = onOpenCloudBackup,
                )
            }

            SettingsSection(stringResource(R.string.settings_section_about)) {
                SettingsRow(
                    headline = stringResource(R.string.settings_version),
                    supporting = appVersion(LocalContext.current),
                    icon = Icons.Filled.Info,
                )
                SettingsDivider(withIcon = true)
                SettingsRow(
                    headline = stringResource(R.string.settings_licenses),
                    supporting = stringResource(R.string.settings_licenses_summary),
                    icon = Icons.AutoMirrored.Filled.List,
                    onClick = onOpenAbout,
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
        SettingsDialog.ACCENT -> ChoiceDialog(
            title = stringResource(R.string.settings_accent_color),
            options = AccentColor.entries,
            selected = settings.accentColor,
            label = { stringResource(accentLabelRes(it)) },
            onSelect = { viewModel.setAccentColor(it); dismiss() },
            onDismiss = dismiss,
            leading = { AccentSwatch(it) },
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

/** Profilkopf: Name groß auf tonaler Fläche, darunter Gewicht und Größe als Kennzahlen. */
@Composable
private fun ProfileHero(
    name: String,
    weight: String?,
    height: String?,
    onEditName: () -> Unit,
    onEditWeight: () -> Unit,
    onEditHeight: () -> Unit,
) {
    val noValue = stringResource(R.string.settings_not_set)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.gutter)) {
        GymPulseCard(
            modifier = Modifier.fillMaxWidth(),
            variant = GymPulseCardVariant.Primary,
            shape = ExpressiveShapes.extraLargeIncreased,
            onClick = onEditName,
            contentPadding = PaddingValues(Spacing.m),
        ) {
            Row(
                modifier = Modifier.defaultMinSize(minHeight = Sizing.minTouchTarget),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                ListRowIcon(
                    icon = Icons.Filled.Person,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    size = Sizing.iconLarge + Spacing.l,
                )
                Column(modifier = Modifier.weight(1f)) {
                    LabelCaps(
                        text = stringResource(R.string.settings_name),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.gutter)) {
            StatTile(
                value = weight ?: noValue,
                unit = weight?.let { stringResource(R.string.settings_unit_kg) },
                label = stringResource(R.string.settings_body_weight),
                onClick = onEditWeight,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = height ?: noValue,
                unit = height?.let { stringResource(R.string.settings_unit_cm) },
                label = stringResource(R.string.settings_height),
                onClick = onEditHeight,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun SettingsSection(title: String, content: @Composable () -> Unit) {
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

private fun accentLabelRes(accent: AccentColor): Int = when (accent) {
    AccentColor.MINT -> R.string.settings_accent_mint
    AccentColor.BLUE -> R.string.settings_accent_blue
    AccentColor.VIOLET -> R.string.settings_accent_violet
    AccentColor.PINK -> R.string.settings_accent_pink
    AccentColor.ORANGE -> R.string.settings_accent_orange
}

/** Farbpunkt der Akzentfarbe; der Name steht daneben, daher keine eigene Beschreibung. */
@Composable
private fun AccentSwatch(accent: AccentColor) {
    Box(
        modifier = Modifier
            .size(Sizing.statusDot * 2)
            .background(color = accent.swatch, shape = CircleShape),
    )
}

/** Sprachen heißen in ihrer eigenen Sprache, damit man sie auch bei fremder Oberfläche findet. */
@Composable
private fun languageLabel(language: AppLanguage): String {
    if (language == AppLanguage.SYSTEM) return stringResource(R.string.settings_language_system)
    val locale = Locale.forLanguageTag(language.languageTag)
    return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
}

/** Zahl in der Sprache des Geräts, ohne Tausendertrenner, höchstens zwei Nachkommastellen. */
internal fun formatNumber(value: Double): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        isGroupingUsed = false
        maximumFractionDigits = 2
    }.format(value)

@Composable
internal fun appVersion(context: Context): String = remember(context) {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    info.versionName.orEmpty()
}
