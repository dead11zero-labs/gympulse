package app.gympulse.feature.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.designsystem.component.PlaceholderScreen
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.model.WorkoutPlan

/** Platz unter der Liste, damit der FAB die letzte Karte nicht verdeckt. */
internal val FabClearance = Spacing.xxl + Spacing.xl + Spacing.xs

private const val DESCRIPTION_MAX_LINES = 2

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
                PlaceholderScreen(
                    title = stringResource(R.string.plans_empty_title),
                    description = stringResource(R.string.plans_empty_body),
                    modifier = Modifier.padding(padding),
                )
            } else {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(
                        modifier = Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Spacing.screenMargin,
                            end = Spacing.screenMargin,
                            top = Spacing.m,
                            bottom = FabClearance,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
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

    Card(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
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
                    style = MaterialTheme.typography.titleMedium,
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
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
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

@Composable
private fun planMeta(summary: PlanSummary): String {
    if (summary.exerciseCount == 0) return stringResource(R.string.plans_no_exercises_yet)
    val exercises = pluralStringResource(R.plurals.plans_exercise_count, summary.exerciseCount, summary.exerciseCount)
    val sets = pluralStringResource(R.plurals.plans_set_count, summary.totalSets, summary.totalSets)
    val minutes = stringResource(R.string.plans_duration_minutes, summary.estimatedMinutes)
    return stringResource(R.string.plans_meta, exercises, sets, minutes)
}
