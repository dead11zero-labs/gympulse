package app.gympulse.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.gympulse.R
import app.gympulse.feature.settings.SettingsRoute
import app.gympulse.feature.workout.bar.ActiveWorkoutBar
import app.gympulse.feature.workout.bar.ActiveWorkoutServiceEffect
import app.gympulse.feature.workout.navigateToWorkout
import app.gympulse.navigation.GymPulseNavHost
import app.gympulse.navigation.TopLevelDestination

/** Ab dieser Fensterbreite (Material 3 "Medium") wird die Navigation zur Rail an der Seite. */
private val MediumWidthBreakpoint = 600.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymPulseApp(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentTopLevel = TopLevelDestination.entries.firstOrNull { currentDestination.isOn(it) }
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    // Adaptive Navigation: Navigation Bar auf Handys, Navigation Rail auf Tablets und Foldables.
    // Im Workout und in den Einstellungen wird sie ausgeblendet.
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val layoutType = when {
        currentTopLevel == null -> NavigationSuiteType.None
        windowWidth >= MediumWidthBreakpoint -> NavigationSuiteType.NavigationRail
        else -> NavigationSuiteType.NavigationBar
    }

    // Keeps the workout notification running while a session is active.
    ActiveWorkoutServiceEffect()

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = destination == currentTopLevel
                item(
                    selected = selected,
                    onClick = { navController.navigateToTopLevel(destination) },
                    icon = {
                        Icon(
                            if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(stringResource(currentTopLevel?.labelRes ?: R.string.app_name))
                    },
                    navigationIcon = {
                        if (currentTopLevel == null && navController.previousBackStackEntry != null) {
                            // Über den Dispatcher, damit BackHandler (z. B. "Änderungen verwerfen?" im Plan-Editor) greifen.
                            IconButton(onClick = { backDispatcher?.onBackPressed() ?: navController.navigateUp() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                )
                            }
                        }
                    },
                    actions = {
                        if (currentTopLevel != null) {
                            IconButton(onClick = { navController.navigate(SettingsRoute) }) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = stringResource(R.string.action_settings),
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                // Mini bar of a running workout, only on the main tabs. Without a bottom
                // navigation bar it has to keep clear of the system navigation itself.
                if (currentTopLevel != null) {
                    ActiveWorkoutBar(
                        onClick = { navController.navigateToWorkout() },
                        modifier = if (layoutType == NavigationSuiteType.NavigationBar) {
                            Modifier
                        } else {
                            Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                        },
                    )
                }
            },
        ) { innerPadding ->
            GymPulseNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
