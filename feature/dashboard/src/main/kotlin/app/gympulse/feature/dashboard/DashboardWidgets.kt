package app.gympulse.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import app.gympulse.core.designsystem.theme.Spacing
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import kotlin.math.max

private const val BAR_WIDTH_FRACTION = 0.5f

/** Karte "Wochenvolumen": Balken je Tag, Summe in kg und Vergleich zur Vorwoche. */
@Composable
internal fun WeeklyVolumeCard(
    volume: WeeklyVolumeUi?,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                text = stringResource(R.string.feature_dashboard_volume_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (volume != null) {
                val kgFormat = remember { NumberFormat.getIntegerInstance() }
                Text(
                    text = stringResource(
                        R.string.feature_dashboard_volume_total,
                        kgFormat.format(volume.totalKg),
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = comparisonText(volume.changePercent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
}

@Composable
private fun comparisonText(changePercent: Int?): String {
    if (changePercent == null) return stringResource(R.string.feature_dashboard_volume_no_comparison)
    val signed = if (changePercent > 0) "+$changePercent %" else "$changePercent %"
    return stringResource(R.string.feature_dashboard_volume_vs_last_week, signed)
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
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

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
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                text = stringResource(R.string.feature_dashboard_progress_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    ProgressRing(progress)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(
                            text = if (progress.goalReached) {
                                stringResource(R.string.feature_dashboard_progress_goal_reached)
                            } else {
                                val remaining = (progress.goal - progress.completed).coerceAtLeast(0)
                                pluralStringResource(R.plurals.feature_dashboard_progress_remaining, remaining, remaining)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressRing(progress: WeeklyProgressUi, modifier: Modifier = Modifier) {
    val description = stringResource(
        R.string.feature_dashboard_progress_description,
        progress.completed,
        progress.goal,
    )
    Box(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.size(Spacing.xxl * 2),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = Spacing.xs,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Text(
            text = stringResource(
                R.string.feature_dashboard_progress_ring_value,
                progress.completed,
                progress.goal,
            ),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.feature_dashboard_active_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.feature_dashboard_active_subtitle,
                    workout.title ?: stringResource(R.string.feature_dashboard_free_workout),
                    startedAt,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onResume) {
                Text(stringResource(R.string.feature_dashboard_resume_workout))
            }
        }
    }
}

/** Liste "Zuletzt trainiert" mit leerem Zustand. */
@Composable
internal fun RecentSessionsSection(
    sessions: List<RecentSessionUi>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.feature_dashboard_recent_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (sessions.isEmpty()) {
            Text(
                text = stringResource(R.string.feature_dashboard_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val kgFormat = remember { NumberFormat.getIntegerInstance() }
            sessions.forEach { session ->
                RecentSessionCard(session = session, today = today, kgFormat = kgFormat)
            }
        }
    }
}

@Composable
private fun RecentSessionCard(
    session: RecentSessionUi,
    today: LocalDate,
    kgFormat: NumberFormat,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = session.title ?: stringResource(R.string.feature_dashboard_free_workout),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    text = relativeDateText(session.date, today),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(
                    R.string.feature_dashboard_recent_details,
                    durationText(session.durationMinutes),
                    kgFormat.format(session.volumeKg),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
