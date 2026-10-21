package app.gympulse.feature.calendar

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import app.gympulse.core.designsystem.component.StatusChip
import java.time.temporal.WeekFields
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.data.repository.CompletedSessionSummary
import app.gympulse.core.designsystem.adaptive.currentWindowWidthClass
import app.gympulse.core.designsystem.component.GymPulseCard
import app.gympulse.core.designsystem.component.GymPulseCardVariant
import app.gympulse.core.designsystem.component.ListRow
import app.gympulse.core.designsystem.component.ListRowIcon
import app.gympulse.core.designsystem.component.SectionHeader
import app.gympulse.core.designsystem.theme.ExpressiveShapes
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.model.ScheduleOverrideType
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Anzahl Tage, die beim Verschieben zur Auswahl stehen. */
private const val MOVE_TARGET_DAYS = 14

/** Aktionen des Kalenders, vom ViewModel gebunden. */
internal class CalendarActions(
    val onModeChange: (CalendarMode) -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onToday: () -> Unit,
    val onSelectDate: (LocalDate) -> Unit,
    val onSetWeekdayPlan: (DayOfWeek, String?) -> Unit,
    val onSkipDay: (LocalDate) -> Unit,
    val onMoveDay: (from: LocalDate, to: LocalDate, planId: String) -> Unit,
    val onAddPlan: (LocalDate, String) -> Unit,
    val onClearOverride: (LocalDate) -> Unit,
)

@Composable
internal fun CalendarScreen(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        CalendarActions(
            onModeChange = viewModel::setMode,
            onPrevious = viewModel::previous,
            onNext = viewModel::next,
            onToday = viewModel::goToToday,
            onSelectDate = viewModel::selectDate,
            onSetWeekdayPlan = viewModel::setWeekdayPlan,
            onSkipDay = viewModel::skipDay,
            onMoveDay = viewModel::moveDay,
            onAddPlan = viewModel::addPlan,
            onClearOverride = viewModel::clearOverride,
        )
    }
    CalendarContent(state = state, actions = actions, modifier = modifier)
}

@Composable
internal fun CalendarContent(
    state: CalendarUiState,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.padding(Spacing.xl))
            !state.featureEnabled -> PremiumNotice(
                modifier = Modifier
                    .widthIn(max = Spacing.maxContentWidth)
                    .padding(Spacing.screenMargin),
            )
            else -> CalendarBody(state = state, actions = actions)
        }
    }
}

/** Hinweis, wenn [app.gympulse.core.featuregate.Feature.CALENDAR] aus ist. */
@Composable
internal fun PremiumNotice(modifier: Modifier = Modifier) {
    GymPulseCard(
        modifier = modifier.fillMaxWidth(),
        variant = GymPulseCardVariant.Tertiary,
        shape = ExpressiveShapes.extraLargeIncreased,
        contentPadding = PaddingValues(Spacing.l),
        verticalSpacing = Spacing.s,
    ) {
        ListRowIcon(
            icon = Icons.Filled.Lock,
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        )
        Text(
            text = stringResource(R.string.feature_calendar_premium_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.feature_calendar_premium_body),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private sealed interface CalendarDialog {
    data class WeekdayPlan(val weekday: DayOfWeek) : CalendarDialog
    data class AddPlan(val date: LocalDate) : CalendarDialog
    data class MoveTarget(val from: LocalDate, val planId: String) : CalendarDialog
}

@Composable
private fun CalendarBody(state: CalendarUiState, actions: CalendarActions) {
    val locale = LocalConfiguration.current.locales[0]
    var dialog by remember { mutableStateOf<CalendarDialog?>(null) }

    val margin = currentWindowWidthClass().horizontalMargin
    LazyColumn(
        modifier = Modifier
            .widthIn(max = Spacing.maxContentWidth)
            .fillMaxSize(),
        contentPadding = PaddingValues(horizontal = margin, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item { PeriodHeader(state, actions, locale) }
        item {
            if (state.mode == CalendarMode.WEEK) {
                WeekList(state, actions, locale)
            } else {
                MonthGrid(state, actions, locale)
            }
        }
        item {
            state.selectedDay?.let { day ->
                DayDetailCard(
                    day = day,
                    state = state,
                    locale = locale,
                    onSkip = { actions.onSkipDay(day.date) },
                    onMove = { planId -> dialog = CalendarDialog.MoveTarget(day.date, planId) },
                    onClear = { actions.onClearOverride(day.date) },
                )
            }
        }
        item {
            val selected = state.selectedDay
            if (selected != null && !selected.date.isBefore(state.today)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = { dialog = CalendarDialog.AddPlan(selected.date) },
                        modifier = Modifier.heightIn(min = Sizing.minTouchTarget),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.feature_calendar_action_add))
                    }
                }
            }
        }
        item {
            WeekSchemeCard(
                state = state,
                locale = locale,
                onEdit = { weekday -> dialog = CalendarDialog.WeekdayPlan(weekday) },
            )
        }
    }

    when (val current = dialog) {
        null -> Unit
        is CalendarDialog.WeekdayPlan -> PlanPickerDialog(
            title = stringResource(
                R.string.feature_calendar_pick_plan_for,
                current.weekday.getDisplayName(TextStyle.FULL, locale),
            ),
            plans = state.plans,
            selectedId = state.weekScheme.firstOrNull { it.weekday == current.weekday }?.planId,
            noneLabel = stringResource(R.string.feature_calendar_rest_day),
            onSelect = { planId ->
                actions.onSetWeekdayPlan(current.weekday, planId)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is CalendarDialog.AddPlan -> PlanPickerDialog(
            title = stringResource(R.string.feature_calendar_add_plan_title),
            plans = state.plans,
            selectedId = null,
            noneLabel = null,
            onSelect = { planId ->
                if (planId != null) actions.onAddPlan(current.date, planId)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is CalendarDialog.MoveTarget -> MoveTargetDialog(
            from = current.from,
            locale = locale,
            onSelect = { target ->
                actions.onMoveDay(current.from, target, current.planId)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun PeriodHeader(state: CalendarUiState, actions: CalendarActions, locale: Locale) {
    val title = periodTitle(state, locale)
    val subtitle = periodSubtitle(state, locale)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onPrevious) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.feature_calendar_previous),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            IconButton(onClick = actions.onNext) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.feature_calendar_next),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                val modes = CalendarMode.entries
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { actions.onModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    ) {
                        Text(
                            text = stringResource(
                                if (mode == CalendarMode.WEEK) {
                                    R.string.feature_calendar_mode_week
                                } else {
                                    R.string.feature_calendar_mode_month
                                },
                            ),
                        )
                    }
                }
            }
            FilledTonalButton(onClick = actions.onToday) {
                Text(stringResource(R.string.feature_calendar_today))
            }
        }
    }
}

/** Wochenansicht: Kalenderwoche ("KW 43"), Monatsansicht: Monat und Jahr. */
@Composable
private fun periodTitle(state: CalendarUiState, locale: Locale): String =
    if (state.mode == CalendarMode.MONTH) {
        DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(state.periodStart)
    } else {
        val first = state.weeks.firstOrNull()?.firstOrNull()?.date ?: state.periodStart
        stringResource(
            R.string.feature_calendar_week_number,
            first.get(WeekFields.of(locale).weekOfWeekBasedYear()),
        )
    }

/** Datumsbereich unter der Kalenderwoche; die Monatsansicht braucht keinen. */
private fun periodSubtitle(state: CalendarUiState, locale: Locale): String? =
    if (state.mode == CalendarMode.MONTH) {
        null
    } else {
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        val first = state.weeks.firstOrNull()?.firstOrNull()?.date ?: state.periodStart
        "${formatter.format(first)} – ${formatter.format(first.plusDays(6))}"
    }

@Composable
private fun statusColor(status: DayStatus): Color = when (status) {
    DayStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    DayStatus.PLANNED -> MaterialTheme.colorScheme.tertiary
    DayStatus.MISSED -> MaterialTheme.colorScheme.error
    DayStatus.SKIPPED -> MaterialTheme.colorScheme.outline
    DayStatus.REST -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun statusLabel(status: DayStatus): String = stringResource(
    when (status) {
        DayStatus.COMPLETED -> R.string.feature_calendar_status_completed
        DayStatus.PLANNED -> R.string.feature_calendar_status_planned
        DayStatus.MISSED -> R.string.feature_calendar_status_missed
        DayStatus.SKIPPED -> R.string.feature_calendar_status_skipped
        DayStatus.REST -> R.string.feature_calendar_rest_day
    },
)

private fun statusIcon(status: DayStatus): ImageVector? = when (status) {
    DayStatus.COMPLETED -> Icons.Filled.CheckCircle
    DayStatus.PLANNED -> Icons.Filled.DateRange
    DayStatus.MISSED -> Icons.Filled.Warning
    DayStatus.SKIPPED -> Icons.Filled.Close
    DayStatus.REST -> null
}

@Composable
private fun planSummary(day: CalendarDay, planNames: Map<String, String>): String {
    if (day.plannedPlanIds.isEmpty()) return statusLabel(day.status)
    val unknown = stringResource(R.string.feature_calendar_unknown_plan)
    return day.plannedPlanIds.joinToString(", ") { planNames[it] ?: unknown }
}

@Composable
private fun WeekList(state: CalendarUiState, actions: CalendarActions, locale: Locale) {
    val names = state.planNames
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        state.weeks.firstOrNull().orEmpty().forEach { day ->
            val isSelected = day.date == state.selectedDate
            val isRest = day.status == DayStatus.REST && day.plannedPlanIds.isEmpty() && day.sessions.isEmpty()
            val minutes = day.sessions.sumOf { sessionMinutes(it) }
            val volume = day.sessions.sumOf { it.volumeKg }
            GymPulseCard(
                onClick = { actions.onSelectDate(day.date) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { selected = isSelected },
                variant = when {
                    isSelected -> GymPulseCardVariant.Level2
                    isRest -> GymPulseCardVariant.Outlined
                    else -> GymPulseCardVariant.Level1
                },
                contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = Sizing.minTouchTarget),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    DayBadge(
                        label = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                        number = day.date.dayOfMonth.toString(),
                        isToday = day.isToday,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(
                                text = planSummary(day, names),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isRest) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (minutes > 0) {
                                StatusChip(
                                    text = stringResource(R.string.feature_calendar_duration_min, minutes),
                                    active = false,
                                )
                            }
                        }
                        if (day.sessions.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.feature_calendar_status_volume,
                                    statusLabel(day.status),
                                    String.format(locale, "%,.0f", volume),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor(day.status),
                            )
                        } else if (day.plannedPlanIds.isNotEmpty()) {
                            Text(
                                text = statusLabel(day.status),
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor(day.status),
                            )
                        }
                    }
                    val icon = statusIcon(day.status)
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = statusColor(day.status),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(Sizing.statusDot)
                                .background(color = MaterialTheme.colorScheme.outlineVariant, shape = CircleShape),
                        )
                    }
                }
            }
        }
    }
}

private fun sessionMinutes(session: CompletedSessionSummary): Long =
    Duration.between(session.startedAt, session.endedAt).toMinutes().coerceAtLeast(0)

/** Wochentag über Tageszahl; heute als gefülltes Feld in `primary`. */
@Composable
private fun DayBadge(label: String, number: String, isToday: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .width(Sizing.minTouchTarget)
                .padding(vertical = Spacing.xxs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(text = number, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun MonthGrid(state: CalendarUiState, actions: CalendarActions, locale: Locale) {
    val fullDate = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(Sizing.hairline, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                state.weeks.firstOrNull().orEmpty().forEach { day ->
                    Text(
                        text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            state.weeks.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        MonthCell(
                            day = day,
                            isSelected = day.date == state.selectedDate,
                            description = "${fullDate.format(day.date)}, ${statusLabel(day.status)}",
                            onClick = { actions.onSelectDate(day.date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCell(
    day: CalendarDay,
    isSelected: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when {
        day.isToday -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = Sizing.minTouchTarget)
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = if (day.isToday) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = when {
                    day.isToday -> MaterialTheme.colorScheme.onPrimary
                    day.inPeriod -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.outline
                },
            )
            Box(
                modifier = Modifier
                    .size(Spacing.xs)
                    .background(
                        color = when {
                            day.status == DayStatus.REST -> Color.Transparent
                            day.isToday -> MaterialTheme.colorScheme.onPrimary
                            else -> statusColor(day.status)
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun DayDetailCard(
    day: CalendarDay,
    state: CalendarUiState,
    locale: Locale,
    onSkip: () -> Unit,
    onMove: (planId: String) -> Unit,
    onClear: () -> Unit,
) {
    val fullDate = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    val editable = !day.date.isBefore(state.today)
    val movablePlanId = day.plannedPlanIds.firstOrNull()
    val exception = day.override?.let { overrideLabel(it) }
    val supporting = if (day.plannedPlanIds.isNotEmpty() || day.override != null) {
        listOfNotNull(
            statusLabel(day.status).takeIf { day.plannedPlanIds.isNotEmpty() },
            exception,
        ).joinToString(" \u00B7 ")
    } else {
        null
    }
    GymPulseCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        variant = GymPulseCardVariant.Level2,
        contentPadding = PaddingValues(vertical = Spacing.xs),
        verticalSpacing = Spacing.none,
    ) {
        SectionHeader(title = fullDate.format(day.date))
        ListRow(
            headline = planSummary(day, state.planNames),
            supportingText = supporting,
            leadingIcon = statusIcon(day.status) ?: Icons.Filled.DateRange,
        )
        if (day.sessions.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionHeader(title = stringResource(R.string.feature_calendar_history))
            day.sessions.forEach { session ->
                SessionRow(session = session, zone = state.zone, locale = locale)
            }
        }
        if (editable) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (movablePlanId != null && day.override != ScheduleOverrideType.SKIPPED) {
                ActionRow(Icons.Filled.Close, stringResource(R.string.feature_calendar_action_skip), onSkip)
                ActionRow(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    stringResource(R.string.feature_calendar_action_move),
                ) { onMove(movablePlanId) }
            }
            if (day.override != null) {
                ActionRow(Icons.Filled.Refresh, stringResource(R.string.feature_calendar_action_reset), onClear)
            }
        }
    }
}

@Composable
private fun overrideLabel(type: ScheduleOverrideType): String = stringResource(
    when (type) {
        ScheduleOverrideType.SKIPPED -> R.string.feature_calendar_override_skipped
        ScheduleOverrideType.MOVED -> R.string.feature_calendar_override_moved
        ScheduleOverrideType.ADDED -> R.string.feature_calendar_override_added
    },
)

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListRow(
        headline = label,
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        onClick = onClick,
        modifier = Modifier.semantics { role = Role.Button },
    )
}

@Composable
private fun SessionRow(session: CompletedSessionSummary, zone: ZoneId, locale: Locale) {
    val timeFormat = remember(locale, zone) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    }
    val minutes = Duration.between(session.startedAt, session.endedAt).toMinutes().coerceAtLeast(0)
    ListRow(
        headline = session.title ?: stringResource(R.string.feature_calendar_free_workout),
        supportingText = stringResource(
            R.string.feature_calendar_session_meta,
            timeFormat.format(session.endedAt),
            minutes,
            String.format(locale, "%,.0f", session.volumeKg),
        ),
        leadingContent = {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.feature_calendar_status_completed),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

@Composable
private fun WeekSchemeCard(state: CalendarUiState, locale: Locale, onEdit: (DayOfWeek) -> Unit) {
    val names = state.planNames
    val unknown = stringResource(R.string.feature_calendar_unknown_plan)
    val restDay = stringResource(R.string.feature_calendar_rest_day)
    val editLabel = stringResource(R.string.feature_calendar_edit)
    GymPulseCard(
        modifier = Modifier.fillMaxWidth(),
        variant = GymPulseCardVariant.Level1,
        contentPadding = PaddingValues(vertical = Spacing.xs),
        verticalSpacing = Spacing.none,
    ) {
        SectionHeader(
            title = stringResource(R.string.feature_calendar_scheme_title),
            supportingText = stringResource(R.string.feature_calendar_scheme_hint),
        )
        state.weekScheme.forEachIndexed { index, slot ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ListRow(
                headline = slot.weekday.getDisplayName(TextStyle.FULL, locale),
                supportingText = slot.planId?.let { names[it] ?: unknown } ?: restDay,
                trailingContent = {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = editLabel,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { onEdit(slot.weekday) },
                modifier = Modifier.semantics { role = Role.Button },
            )
        }
    }
}

@Composable
private fun PlanPickerDialog(
    title: String,
    plans: List<PlanOption>,
    selectedId: String?,
    noneLabel: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (plans.isEmpty() && noneLabel == null) {
                    Text(stringResource(R.string.feature_calendar_no_plans))
                }
                if (noneLabel != null) {
                    RadioRow(label = noneLabel, selected = selectedId == null) { onSelect(null) }
                }
                plans.forEach { plan ->
                    RadioRow(label = plan.name, selected = plan.id == selectedId) { onSelect(plan.id) }
                }
                if (plans.isEmpty() && noneLabel != null) {
                    Text(
                        text = stringResource(R.string.feature_calendar_no_plans),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_calendar_cancel)) }
        },
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.xxl)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.size(Spacing.s))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun MoveTargetDialog(
    from: LocalDate,
    locale: Locale,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val format = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_calendar_move_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                List(MOVE_TARGET_DAYS) { from.plusDays(it + 1L) }.forEach { target ->
                    ListRow(
                        headline = format.format(target),
                        onClick = { onSelect(target) },
                        modifier = Modifier.semantics { role = Role.Button },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_calendar_cancel)) }
        },
    )
}
