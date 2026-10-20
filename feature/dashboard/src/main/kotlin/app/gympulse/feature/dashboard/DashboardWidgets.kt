package app.gympulse.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import app.gympulse.core.designsystem.component.EmptyState
import app.gympulse.core.designsystem.component.GymPulseCard
import app.gympulse.core.designsystem.component.GymPulseCardVariant
import app.gympulse.core.designsystem.component.ListGroup
import app.gympulse.core.designsystem.component.ListRow
import app.gympulse.core.designsystem.component.ListRowDivider
import app.gympulse.core.designsystem.component.ListRowIcon
import app.gympulse.core.designsystem.component.ProgressRing
import app.gympulse.core.designsystem.component.SectionHeader
import app.gympulse.core.designsystem.component.StatTrend
import app.gympulse.core.designsystem.component.TrendDirection
import app.gympulse.core.designsystem.component.TrendLabel
import app.gympulse.core.designsystem.theme.ExpressiveShapes
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.designsystem.theme.metricLarge
import app.gympulse.core.designsystem.theme.metricMedium
import app.gympulse.core.designsystem.theme.metricUnit
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import kotlin.math.max

private const val BAR_WIDTH_FRACTION = 0.5f

/** Karte "Wochenvolumen": große Summe in kg, Trend zur Vorwoche und Balken je Tag. */
@Composable
internal fun WeeklyVolumeCard(
    volume: WeeklyVolumeUi?,
    modifier: Modifier = Modifier,
) {
    GymPulseCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        variant = GymPulseCardVariant.Filled,
        shape = ExpressiveShapes.largeIncreased,
        verticalSpacing = Spacing.s,
    ) {
        Text(
            text = stringResource(R.string.feature_dashboard_volume_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (volume != null) {
            val kgFormat = remember { NumberFormat.getIntegerInstance() }
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    text = kgFormat.format(volume.totalKg),
                    style = MaterialTheme.typography.metricLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = stringResource(R.string.feature_dashboard_unit_kg),
                    style = MaterialTheme.typography.metricUnit,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            val change = volume.changePercent
            if (change == null) {
                Text(
                    text = stringResource(R.string.feature_dashboard_volume_no_comparison),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TrendLabel(trend = volumeTrend(change))
            }
            if (volume.isEmpty) {
                Text(
                    text = stringResource(R.string.feature_dashboard_volume_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                WeeklyVolumeChart(volume = volume, formatKg = { kgFormat.format(it) })
            }
        }
    }
}

@Composable
private fun volumeTrend(changePercent: Int): StatTrend {
    val signed = if (changePercent > 0) "+$changePercent %" else "$changePercent %"
    val direction = when {
        changePercent > 0 -> TrendDirection.Up
        changePercent < 0 -> TrendDirection.Down
        else -> TrendDirection.Flat
    }
    return StatTrend(
        label = stringResource(R.string.feature_dashboard_volume_vs_last_week, signed),
        direction = direction,
    )
}

/**
 * Schlankes Balkendiagramm aus Canvas. Jede Spalte zeigt Wert als Text, Balken und Wochentag;
 * für Screenreader wird das gesamte Diagramm als eine Beschreibung vorgelesen.
 */
@Composable
private fun WeeklyVolumeChart(
    volume: WeeklyVolumeUi,
    formatKg: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val maxKg = volume.dailyKg.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    val dayNames = volume.dailyKg.indices.map { index ->
        volume.weekStart.plusDays(index.toLong()).dayOfWeek
    }
    val noValue = stringResource(R.string.feature_dashboard_volume_chart_no_value)
    val dayDescriptions = dayNames.mapIndexed { index, day ->
        stringResource(
            R.string.feature_dashboard_volume_chart_day,
            day.getDisplayName(TextStyle.FULL, locale),
            formatKg(volume.dailyKg[index]),
        )
    }
    val description = stringResource(
        R.string.feature_dashboard_volume_chart_description,
        formatKg(volume.totalKg),
        dayDescriptions.joinToString(", "),
    )
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerLow

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Spacing.xxl * 3)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        dayNames.forEachIndexed { index, day ->
            val value = volume.dailyKg[index]
            val isToday = index == volume.todayIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (value > 0.0) formatCompactKg(value, locale) else noValue,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xxs),
                ) {
                    val barWidth = size.width * BAR_WIDTH_FRACTION
                    val left = (size.width - barWidth) / 2f
                    val corner = CornerRadius(barWidth / 2f)
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(left, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = corner,
                    )
                    if (value > 0.0) {
                        val barHeight = max(size.height * (value / maxKg).toFloat(), barWidth)
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(left, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = corner,
                        )
                    }
                }
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Karte "Wochenfortschritt": Ring mit erledigten Trainings, Wochenziel und Streak. */
@Composable
internal fun WeeklyProgressCard(
    progress: WeeklyProgressUi?,
    modifier: Modifier = Modifier,
) {
    GymPulseCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        variant = if (progress?.goalReached == true) GymPulseCardVariant.Tertiary else GymPulseCardVariant.Filled,
        shape = ExpressiveShapes.largeIncreased,
        verticalSpacing = Spacing.s,
    ) {
        Text(
            text = stringResource(R.string.feature_dashboard_progress_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (progress != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                val reached = progress.goalReached
                ProgressRing(
                    progress = progress.fraction,
                    color = if (reached) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    trackColor = if (reached) {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentDescription = stringResource(
                        R.string.feature_dashboard_progress_description,
                        progress.completed,
                        progress.goal,
                    ),
                ) {
                    Text(
                        text = stringResource(
                            R.string.feature_dashboard_progress_ring_value,
                            progress.completed,
                            progress.goal,
                        ),
                        style = MaterialTheme.typography.metricMedium,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(
                        text = if (reached) {
                            stringResource(R.string.feature_dashboard_progress_goal_reached)
                        } else {
                            val remaining = (progress.goal - progress.completed).coerceAtLeast(0)
                            pluralStringResource(R.plurals.feature_dashboard_progress_remaining, remaining, remaining)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = if (progress.streakWeeks > 0) {
                            pluralStringResource(
                                R.plurals.feature_dashboard_progress_streak,
                                progress.streakWeeks,
                                progress.streakWeeks,
                            )
                        } else {
                            stringResource(R.string.feature_dashboard_progress_streak_none)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/** Karte "Workout fortsetzen" für eine laufende Session. */
@Composable
internal fun ActiveWorkoutCard(
    workout: ActiveWorkout,
    zone: ZoneId,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val startedAt = remember(workout.startedAt, zone) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(workout.startedAt.atZone(zone))
    }
    GymPulseCard(
        modifier = modifier.fillMaxWidth(),
        variant = GymPulseCardVariant.Primary,
        shape = ExpressiveShapes.extraLargeIncreased,
        contentPadding = PaddingValues(Spacing.l),
    ) {
        Text(
            text = stringResource(R.string.feature_dashboard_active_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(
                R.string.feature_dashboard_active_subtitle,
                workout.title ?: stringResource(R.string.feature_dashboard_free_workout),
                startedAt,
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(Spacing.xs))
        Button(
            onClick = onResume,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(stringResource(R.string.feature_dashboard_resume_workout))
        }
    }
}

/** Liste "Zuletzt trainiert" auf einer tonalen Fläche, mit Leerzustand. */
@Composable
internal fun RecentSessionsSection(
    sessions: List<RecentSessionUi>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        SectionHeader(
            title = stringResource(R.string.feature_dashboard_recent_title),
            contentPadding = PaddingValues(vertical = Spacing.xs),
        )
        if (sessions.isEmpty()) {
            GymPulseCard(variant = GymPulseCardVariant.Outlined, modifier = Modifier.fillMaxWidth()) {
                EmptyState(
                    icon = Icons.Filled.DateRange,
                    title = stringResource(R.string.feature_dashboard_recent_empty_title),
                    message = stringResource(R.string.feature_dashboard_recent_empty),
                )
            }
        } else {
            val kgFormat = remember { NumberFormat.getIntegerInstance() }
            ListGroup(modifier = Modifier.fillMaxWidth()) {
                sessions.forEachIndexed { index, session ->
                    if (index > 0) ListRowDivider()
                    RecentSessionRow(session = session, today = today, kgFormat = kgFormat)
                }
            }
        }
    }
}

@Composable
private fun RecentSessionRow(
    session: RecentSessionUi,
    today: LocalDate,
    kgFormat: NumberFormat,
) {
    ListRow(
        headline = session.title ?: stringResource(R.string.feature_dashboard_free_workout),
        supportingText = stringResource(
            R.string.feature_dashboard_recent_details,
            durationText(session.durationMinutes),
            kgFormat.format(session.volumeKg),
        ),
        leadingContent = { ListRowIcon(Icons.Filled.DateRange) },
        trailingText = relativeDateText(session.date, today),
    )
}

@Composable
private fun relativeDateText(date: LocalDate, today: LocalDate): String {
    val daysAgo = ChronoUnit.DAYS.between(date, today).toInt()
    return when {
        daysAgo <= 0 -> stringResource(R.string.feature_dashboard_date_today)
        daysAgo == 1 -> stringResource(R.string.feature_dashboard_date_yesterday)
        daysAgo < DAYS_IN_WEEK -> pluralStringResource(R.plurals.feature_dashboard_date_days_ago, daysAgo, daysAgo)
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(date)
    }
}

@Composable
private fun durationText(totalMinutes: Long): String {
    val hours = (totalMinutes / MINUTES_PER_HOUR).toInt()
    val minutes = (totalMinutes % MINUTES_PER_HOUR).toInt()
    return if (hours > 0) {
        stringResource(R.string.feature_dashboard_duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.feature_dashboard_duration_minutes, minutes)
    }
}

private const val DAYS_IN_WEEK = 7
private const val MINUTES_PER_HOUR = 60L
