package app.gympulse.feature.dashboard

import android.text.format.DateFormat
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.data.ui.labelRes
import app.gympulse.core.designsystem.adaptive.WindowWidthClass
import app.gympulse.core.designsystem.component.GymPulseCard
import app.gympulse.core.designsystem.component.GymPulseCardVariant
import app.gympulse.core.designsystem.component.StatTile
import app.gympulse.core.designsystem.component.StatTrend
import app.gympulse.core.designsystem.component.TrendDirection
import app.gympulse.core.designsystem.component.TrendLabel
import app.gympulse.core.designsystem.theme.ExpressiveShapes
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.designsystem.theme.metricLarge
import app.gympulse.core.designsystem.theme.metricUnit
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private const val MAX_MUSCLES = 8
private const val MAX_TOP_EXERCISES = 5
private const val PERCENT = 100
private const val COUNT_UP_MILLIS = 800

@Composable
internal fun WeekAnalysisScreen(
    modifier: Modifier = Modifier,
    viewModel: WeekAnalysisViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WeekAnalysisContent(
        state = state,
        onPreviousWeek = viewModel::showPreviousWeek,
        onNextWeek = viewModel::showNextWeek,
        onSelectTrendWeek = viewModel::selectTrendWeek,
        modifier = modifier,
    )
}

@Composable
internal fun WeekAnalysisContent(
    state: WeekAnalysisUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectTrendWeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = Spacing.maxContentWidth)
                .fillMaxSize(),
        ) {
            val widthClass = WindowWidthClass.fromWidth(maxWidth)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = widthClass.horizontalMargin, vertical = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(widthClass.gutter),
            ) {
                Text(
                    text = stringResource(R.string.feature_dashboard_week_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                val selected = state.selected
                if (selected != null) {
                    WeekSelector(
                        state = state,
                        selected = selected,
                        onPreviousWeek = onPreviousWeek,
                        onNextWeek = onNextWeek,
                    )
                    WeekDetails(
                        state = state,
                        selected = selected,
                        isWide = widthClass.isAtLeastMedium,
                        onSelectTrendWeek = onSelectTrendWeek,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekDetails(
    state: WeekAnalysisUiState,
    selected: WeekStats,
    isWide: Boolean,
    onSelectTrendWeek: (Int) -> Unit,
) {
    val previous = state.previous
    val reveal = rememberReveal(selected.weekStart)

    VolumeHero(selected = selected, previous = previous)
    KpiGrid(selected = selected, previous = previous, isWide = isWide)
    if (selected.isEmpty) {
        GymPulseCard(
            modifier = Modifier.fillMaxWidth(),
            variant = GymPulseCardVariant.Level1,
            shape = ExpressiveShapes.largeIncreased,
        ) {
            Text(
                text = stringResource(R.string.feature_dashboard_week_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        DaysCard(selected = selected, reveal = reveal)
        MusclesCard(selected = selected, reveal = reveal)
        SplitCard(selected = selected, reveal = reveal)
    }
    TrendCard(state = state, onSelectTrendWeek = onSelectTrendWeek)
    if (selected.topExercises.isNotEmpty()) TopExercisesCard(selected = selected)
    HighlightsCard(highlights = state.highlights)
}

@Composable
private fun WeekSelector(
    state: WeekAnalysisUiState,
    selected: WeekStats,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPreviousWeek, enabled = state.canGoBack) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.feature_dashboard_week_previous),
            )
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = weekRangeText(selected.weekStart, locale),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (state.isCurrentWeek) {
                Text(
                    text = stringResource(R.string.feature_dashboard_week_this_week),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onNextWeek, enabled = state.canGoForward) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.feature_dashboard_week_next),
            )
        }
    }
}

/** Große Summe der Woche; die Zahl zählt beim Wechsel der Woche hoch. */
@Composable
private fun VolumeHero(selected: WeekStats, previous: WeekStats?) {
    val kgFormat = remember { NumberFormat.getIntegerInstance() }
    val shown = remember { Animatable(0f) }
    LaunchedEffect(selected.volumeKg) {
        shown.animateTo(
            selected.volumeKg.toFloat(),
            animationSpec = tween(COUNT_UP_MILLIS, easing = FastOutSlowInEasing),
        )
    }
    GymPulseCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        variant = GymPulseCardVariant.Primary,
        shape = ExpressiveShapes.largeIncreased,
        verticalSpacing = Spacing.s,
    ) {
        Text(
            text = stringResource(R.string.feature_dashboard_week_total_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                text = kgFormat.format(shown.value.toDouble()),
                style = MaterialTheme.typography.metricLarge,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = stringResource(R.string.feature_dashboard_unit_kg),
                style = MaterialTheme.typography.metricUnit,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }
        val change = previous?.let { percentChange(selected.volumeKg, it.volumeKg) }
        if (change == null) {
            Text(
                text = stringResource(R.string.feature_dashboard_week_no_comparison),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            TrendLabel(
                trend = StatTrend(
                    label = stringResource(R.string.feature_dashboard_week_vs_previous, signedPercent(change)),
                    direction = directionOf(change),
                ),
                onHighlighted = true,
            )
        }
    }
}

@Composable
private fun KpiGrid(selected: WeekStats, previous: WeekStats?, isWide: Boolean) {
    val kgFormat = remember { NumberFormat.getIntegerInstance() }
    val tiles = listOf<@Composable (Modifier) -> Unit>(
        { tileModifier ->
            StatTile(
                value = selected.sessions.toString(),
                label = stringResource(R.string.feature_dashboard_week_kpi_sessions),
                trend = deltaTrend(selected.sessions.toDouble(), previous?.sessions?.toDouble()),
                compact = true,
                modifier = tileModifier,
            )
        },
        { tileModifier ->
            StatTile(
                value = selected.strengthSets.toString(),
                label = stringResource(R.string.feature_dashboard_week_kpi_sets),
                trend = deltaTrend(selected.strengthSets.toDouble(), previous?.strengthSets?.toDouble()),
                compact = true,
                modifier = tileModifier,
            )
        },
        { tileModifier ->
            StatTile(
                value = durationText(selected.durationMinutes),
                label = stringResource(R.string.feature_dashboard_week_kpi_duration),
                trend = deltaTrend(selected.durationMinutes.toDouble(), previous?.durationMinutes?.toDouble()),
                compact = true,
                modifier = tileModifier,
            )
        },
        { tileModifier ->
            StatTile(
                value = kgFormat.format(selected.averageVolumeKg),
                unit = stringResource(R.string.feature_dashboard_unit_kg),
                label = stringResource(R.string.feature_dashboard_week_kpi_average),
                trend = deltaTrend(selected.averageVolumeKg, previous?.averageVolumeKg),
                compact = true,
                modifier = tileModifier,
            )
        },
    )
    val perRow = if (isWide) tiles.size else 2
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.gutter)) {
        tiles.chunked(perRow).forEach { rowTiles ->
            Row(
                modifier = Modifier.height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(Spacing.gutter),
            ) {
                rowTiles.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun DaysCard(selected: WeekStats, reveal: Float) {
    val locale = LocalConfiguration.current.locales[0]
    val kgFormat = remember { NumberFormat.getIntegerInstance() }
    var selectedDay by remember(selected.weekStart) { mutableStateOf<Int?>(null) }
    val days = selected.dailyKg.indices.map { selected.weekStart.plusDays(it.toLong()).dayOfWeek }
    val dayDescriptions = days.mapIndexed { index, day ->
        stringResource(
            R.string.feature_dashboard_volume_chart_day,
            day.getDisplayName(TextStyle.FULL, locale),
            kgFormat.format(selected.dailyKg[index]),
        )
    }
    val description = stringResource(
        R.string.feature_dashboard_week_days_description,
        kgFormat.format(selected.volumeKg),
        dayDescriptions.joinToString(", "),
    )
    ChartCard(title = stringResource(R.string.feature_dashboard_week_days_title)) {
        val day = selectedDay
        Text(
            text = when {
                day == null -> stringResource(R.string.feature_dashboard_week_days_hint)
                selected.dailyKg[day] <= 0.0 -> stringResource(
                    R.string.feature_dashboard_week_day_rest,
                    days[day].getDisplayName(TextStyle.FULL, locale),
                )
                else -> dayDescriptions[day]
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DailyBarsChart(
            dailyKg = selected.dailyKg,
            dayLabels = days.map { it.getDisplayName(TextStyle.NARROW, locale) },
            selectedDay = day,
            reveal = reveal,
            description = description,
            onSelectDay = { tapped -> selectedDay = if (tapped == selectedDay) null else tapped },
        )
    }
}

@Composable
private fun MusclesCard(selected: WeekStats, reveal: Float) {
    val setsFormat = remember {
        NumberFormat.getInstance().apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }
    }
    ChartCard(
        title = stringResource(R.string.feature_dashboard_week_muscles_title),
        subtitle = stringResource(R.string.feature_dashboard_week_muscles_subtitle),
    ) {
        if (selected.muscleSets.isEmpty()) {
            Text(
                text = stringResource(R.string.feature_dashboard_week_muscles_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ChartCard
        }
        val shares = selected.regionShares()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            RegionRing(shares = shares, reveal = reveal)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                BodyRegion.entries.forEach { region ->
                    LegendRow(
                        color = regionColor(region),
                        text = stringResource(
                            R.string.feature_dashboard_week_legend,
                            stringResource(regionLabel(region)),
                            ((shares[region] ?: 0f) * PERCENT).roundToInt(),
                        ),
                    )
                }
            }
        }
        MuscleBars(
            entries = selected.muscleSets.entries
                .sortedByDescending { it.value }
                .take(MAX_MUSCLES)
                .map { MuscleBarEntry(stringResource(it.key.labelRes()), it.value, it.key.region()) },
            formatSets = { setsFormat.format(it) },
            reveal = reveal,
        )
    }
}

@Composable
private fun SplitCard(selected: WeekStats, reveal: Float) {
    val distanceFormat = remember {
        NumberFormat.getInstance().apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }
    }
    val cardioPercent = (selected.cardioShare * PERCENT).roundToInt()
    ChartCard(title = stringResource(R.string.feature_dashboard_week_split_title)) {
        SplitBar(cardioShare = selected.cardioShare, reveal = reveal)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            LegendRow(
                color = MaterialTheme.colorScheme.primary,
                text = stringResource(
                    R.string.feature_dashboard_week_legend,
                    stringResource(R.string.feature_dashboard_week_strength),
                    PERCENT - cardioPercent,
                ),
            )
            LegendRow(
                color = MaterialTheme.colorScheme.tertiary,
                text = stringResource(
                    R.string.feature_dashboard_week_legend,
                    stringResource(R.string.feature_dashboard_week_cardio),
                    cardioPercent,
                ),
            )
        }
        if (selected.cardioSeconds <= 0L && selected.cardioDistanceMeters <= 0.0) {
            Text(
                text = stringResource(R.string.feature_dashboard_week_no_cardio),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier.height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(Spacing.gutter),
            ) {
                StatTile(
                    value = durationText(selected.cardioMinutes),
                    label = stringResource(R.string.feature_dashboard_week_cardio_time),
                    compact = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                if (selected.cardioDistanceMeters > 0.0) {
                    StatTile(
                        value = distanceFormat.format(selected.cardioDistanceMeters / METERS_PER_KM),
                        unit = stringResource(R.string.feature_dashboard_unit_km),
                        label = stringResource(R.string.feature_dashboard_week_cardio_distance),
                        compact = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendCard(state: WeekAnalysisUiState, onSelectTrendWeek: (Int) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val kgFormat = remember { NumberFormat.getIntegerInstance() }
    val formatter = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "Md"), locale)
    }
    val weeks = state.trendWeeks
    val reveal = rememberReveal(weeks.size)
    val dates = weeks.map { formatter.format(it.weekStart) }
    val description = stringResource(
        R.string.feature_dashboard_week_trend_description,
        weeks.mapIndexed { index, week ->
            stringResource(
                R.string.feature_dashboard_week_trend_point,
                dates[index],
                kgFormat.format(week.volumeKg),
            )
        }.joinToString(", "),
    )
    ChartCard(
        title = stringResource(R.string.feature_dashboard_week_trend_title, weeks.size),
        subtitle = stringResource(R.string.feature_dashboard_week_trend_hint),
    ) {
        TrendLineChart(
            values = weeks.map { it.volumeKg },
            // Jede zweite Beschriftung (von der aktuellen Woche aus) plus die gewählte; sonst wird es eng.
            labels = dates.mapIndexed { index, date ->
                if ((weeks.size - 1 - index) % 2 == 0 || index == state.selectedTrendIndex) date else ""
            },
            selectedIndex = state.selectedTrendIndex,
            reveal = reveal,
            description = description,
            onSelect = onSelectTrendWeek,
        )
    }
}

@Composable
private fun TopExercisesCard(selected: WeekStats) {
    val kgFormat = remember { NumberFormat.getIntegerInstance() }
    ChartCard(title = stringResource(R.string.feature_dashboard_week_top_title)) {
        selected.topExercises.take(MAX_TOP_EXERCISES).forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.feature_dashboard_week_top_detail,
                        item.sets,
                        kgFormat.format(item.volumeKg),
                        item.sets,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun HighlightsCard(highlights: WeekHighlights) {
    if (highlights.isEmpty) return
    val locale = LocalConfiguration.current.locales[0]
    GymPulseCard(
        modifier = Modifier.fillMaxWidth(),
        variant = GymPulseCardVariant.Secondary,
        shape = ExpressiveShapes.largeIncreased,
        verticalSpacing = Spacing.xs,
    ) {
        Text(
            text = stringResource(R.string.feature_dashboard_week_highlights_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        highlights.strongestDay?.let { day ->
            Text(
                text = stringResource(
                    R.string.feature_dashboard_week_highlight_day,
                    day.getDisplayName(TextStyle.FULL, locale),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        highlights.topMuscle?.let { muscle ->
            Text(
                text = stringResource(R.string.feature_dashboard_week_highlight_muscle, stringResource(muscle.labelRes())),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        highlights.neglectedMuscle?.let { muscle ->
            Text(
                text = stringResource(R.string.feature_dashboard_week_highlight_neglected, stringResource(muscle.labelRes())),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    GymPulseCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        variant = GymPulseCardVariant.Filled,
        shape = ExpressiveShapes.largeIncreased,
        verticalSpacing = Spacing.s,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}

@Composable
private fun LegendRow(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LegendDot(color = color)
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val METERS_PER_KM = 1000.0

private fun regionLabel(region: BodyRegion): Int = when (region) {
    BodyRegion.UPPER -> R.string.feature_dashboard_week_region_upper
    BodyRegion.LEGS -> R.string.feature_dashboard_week_region_legs
    BodyRegion.CORE -> R.string.feature_dashboard_week_region_core
}

private fun weekRangeText(weekStart: LocalDate, locale: Locale): String {
    val formatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
    return "${formatter.format(weekStart)} – ${formatter.format(weekStart.plusDays(DayOfWeek.entries.size - 1L))}"
}

private fun signedPercent(change: Int): String = if (change > 0) "+$change %" else "$change %"

private fun directionOf(change: Int): TrendDirection = when {
    change > 0 -> TrendDirection.Up
    change < 0 -> TrendDirection.Down
    else -> TrendDirection.Flat
}

/** Prozentuale Veränderung zur Vorwoche als Kachel-Trend; null ohne Vergleichswert. */
private fun deltaTrend(current: Double, previous: Double?): StatTrend? {
    val change = previous?.let { percentChange(current, it) } ?: return null
    return StatTrend(label = signedPercent(change), direction = directionOf(change))
}

@Composable
private fun durationText(minutes: Long): String =
    if (minutes >= MINUTES_PER_HOUR) {
        stringResource(R.string.feature_dashboard_duration_hours_minutes, minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
    } else {
        stringResource(R.string.feature_dashboard_duration_minutes, minutes)
    }

private const val MINUTES_PER_HOUR = 60L
