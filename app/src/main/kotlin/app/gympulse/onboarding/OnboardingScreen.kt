package app.gympulse.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.R
import app.gympulse.core.designsystem.theme.ExpressiveShapes
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3
private const val PAGE_WELCOME = 0
private const val PAGE_GOAL = 1
private const val PAGE_DONE = 2

/**
 * Kurzes Onboarding beim ersten Start: Willkommen, Wochenziel wählen, los geht's.
 * Der Zustand liegt im [OnboardingViewModel]; [onSkip] und [onFinish] beenden das Onboarding.
 */
@Composable
fun OnboardingScreen(
    weeklyGoal: Int,
    onWeeklyGoalSelected: (Int) -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val isLast = currentPage == PAGE_COUNT - 1

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(modifier = Modifier.safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = Spacing.maxFormWidth).fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizing.minTouchTarget)
                        .padding(horizontal = Spacing.xs),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isLast) {
                        TextButton(onClick = onSkip) { Text(stringResource(R.string.onboarding_skip)) }
                    }
                }

                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    when (page) {
                        PAGE_WELCOME -> OnboardingPage(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            title = stringResource(R.string.onboarding_welcome_title),
                            body = stringResource(R.string.onboarding_welcome_body),
                        )
                        PAGE_GOAL -> OnboardingPage(
                            painter = rememberVectorPainter(Icons.Filled.DateRange),
                            title = stringResource(R.string.onboarding_goal_title),
                            body = stringResource(R.string.onboarding_goal_body),
                        ) {
                            GoalPicker(selected = weeklyGoal, onSelected = onWeeklyGoalSelected)
                        }
                        else -> OnboardingPage(
                            painter = rememberVectorPainter(Icons.Filled.Check),
                            title = stringResource(R.string.onboarding_done_title),
                            body = stringResource(R.string.onboarding_done_body),
                        )
                    }
                }

                PageIndicator(
                    current = currentPage,
                    count = PAGE_COUNT,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = Spacing.m),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenMargin)
                        .padding(bottom = Spacing.m),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (currentPage > 0) {
                        TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(currentPage - 1) } }) {
                            Text(stringResource(R.string.action_back))
                        }
                    } else {
                        Spacer(Modifier.size(Spacing.none))
                    }
                    Button(
                        onClick = {
                            if (isLast) onFinish() else scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                        },
                    ) {
                        Text(stringResource(if (isLast) R.string.onboarding_start else R.string.onboarding_next))
                    }
                }
            }
        }
    }
}

/** Eine Seite: runde Illustration, Titel, Text und optional zusätzlicher Inhalt. */
@Composable
private fun OnboardingPage(
    painter: Painter,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    extra: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
    ) {
        Surface(
            shape = ExpressiveShapes.full,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(Sizing.onboardingHero),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.size(Sizing.onboardingHero.times(HERO_ICON_FRACTION)),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (extra != null) {
            extra()
        }
    }
}

private const val HERO_ICON_FRACTION = 0.6f

/** Auswahl des Wochenziels als Single-Choice-Chips mit Zusammenfassung darüber. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalPicker(selected: Int, onSelected: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            text = pluralStringResource(R.plurals.onboarding_goal_summary, selected, selected),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            OnboardingGoalOptions.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = { Text(option.toString()) },
                )
            }
        }
    }
}

/** Seitenpunkte; die aktuelle Seite ist länglich und in Primärfarbe. */
@Composable
private fun PageIndicator(current: Int, count: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_page_indicator, current + 1, count)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == current
            Box(
                modifier = Modifier
                    .size(width = if (active) Spacing.l else Spacing.xs, height = Spacing.xs)
                    .background(
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = ExpressiveShapes.full,
                    ),
            )
        }
    }
}

@Composable
internal fun OnboardingRoute(viewModel: OnboardingViewModel, modifier: Modifier = Modifier) {
    val weeklyGoal by viewModel.weeklyGoal.collectAsStateWithLifecycle()
    OnboardingScreen(
        weeklyGoal = weeklyGoal,
        onWeeklyGoalSelected = viewModel::selectWeeklyGoal,
        onSkip = viewModel::skip,
        onFinish = viewModel::complete,
        modifier = modifier,
    )
}
