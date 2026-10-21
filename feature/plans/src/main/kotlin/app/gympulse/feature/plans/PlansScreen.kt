package app.gympulse.feature.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.designsystem.component.EmptyState
import app.gympulse.core.designsystem.component.GymPulseCard
import app.gympulse.core.designsystem.component.GymPulseCardVariant
import app.gympulse.core.designsystem.theme.ExpressiveShapes
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.model.WorkoutPlan

/** Platz unter der Liste, damit der FAB die letzte Karte nicht verdeckt. */
internal val FabClearance = Spacing.xxl + Spacing.xl + Spacing.xs

private const val DESCRIPTION_MAX_LINES = 2
private const val MAX_PREVIEW_CHIPS = 3

@Composable
internal fun PlansScreen(
    onCreatePlan: () -> Unit,
    onEditPlan: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlansViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var planToDelete by remember { mutableStateOf<WorkoutPlan?>(null) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.plans_new)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = onCreatePlan,
            )
        },
    ) { padding ->
        when (val current = state) {
            PlansUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is PlansUiState.Loaded -> if (current.plans.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.List,
                        title = stringResource(R.string.plans_empty_title),
                        message = stringResource(R.string.plans_empty_body),
                        actionLabel = stringResource(R.string.plans_new),
                        onAction = onCreatePlan,
                    )
                }
            } else {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(
                        modifier = Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Spacing.screenMargin,
                            end = Spacing.screenMargin,
                            top = Spacing.xs,
                            bottom = FabClearance,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.gutter),
                    ) {
                        item(key = "header") {
                            PlansHeader(planCount = current.plans.size)
                        }
                        items(current.plans, key = { it.plan.id }) { item ->
                            PlanCard(
                                item = item,
                                onOpen = { onEditPlan(item.plan.id) },
                                onDuplicate = viewModel::duplicate,
                                onDelete = { planToDelete = item.plan },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    planToDelete?.let { plan ->
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            title = { Text(stringResource(R.string.plans_delete_title)) },
            text = { Text(stringResource(R.string.plans_delete_message, plan.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(plan.id)
                        planToDelete = null
                    },
                ) { Text(stringResource(R.string.plans_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { planToDelete = null }) { Text(stringResource(R.string.plans_cancel)) }
            },
        )
    }
}

@Composable
private fun PlanCard(
    item: PlanListItem,
    onOpen: () -> Unit,
    onDuplicate: (WorkoutPlan, String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = item.plan
    val copyName = stringResource(R.string.plans_copy_name, plan.name)
    var menuOpen by remember { mutableStateOf(false) }

    GymPulseCard(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        variant = GymPulseCardVariant.Level2,
        contentPadding = PaddingValues(Spacing.none),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.m, top = Spacing.xs, bottom = Spacing.xs, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                plan.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = DESCRIPTION_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = planMeta(item.summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                if (item.exerciseNames.isNotEmpty()) {
                    ExerciseNameChips(
                        names = item.exerciseNames,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.plans_more_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.plans_menu_edit)) },
                        onClick = {
                            menuOpen = false
                            onOpen()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.plans_menu_duplicate)) },
                        onClick = {
                            menuOpen = false
                            onDuplicate(plan, copyName)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.plans_menu_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/** Titel und Anzahl der Pläne über der Liste. */
@Composable
private fun PlansHeader(planCount: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = stringResource(R.string.plans_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = pluralStringResource(R.plurals.plans_subtitle, planCount, planCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Vorschau der ersten Übungen als kleine Pillen, der Rest als "+N weitere". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseNameChips(names: List<String>, modifier: Modifier = Modifier) {
    val shown = names.take(MAX_PREVIEW_CHIPS)
    val rest = names.size - shown.size
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        shown.forEach { name -> NamePill(name) }
        if (rest > 0) NamePill(stringResource(R.string.plans_more_exercises, rest))
    }
}

@Composable
private fun NamePill(text: String) {
    Surface(
        shape = ExpressiveShapes.full,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
        )
    }
}

@Composable
private fun planMeta(summary: PlanSummary): String {
    if (summary.exerciseCount == 0) return stringResource(R.string.plans_no_exercises_yet)
    val exercises = pluralStringResource(R.plurals.plans_exercise_count, summary.exerciseCount, summary.exerciseCount)
    val sets = pluralStringResource(R.plurals.plans_set_count, summary.totalSets, summary.totalSets)
    val minutes = stringResource(R.string.plans_duration_minutes, summary.estimatedMinutes)
    return stringResource(R.string.plans_meta, exercises, sets, minutes)
}
