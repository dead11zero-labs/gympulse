package app.gympulse.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.gympulse.R
import coil3.compose.SubcomposeAsyncImage
import app.gympulse.core.designsystem.adaptive.currentWindowWidthClass
import app.gympulse.core.designsystem.component.GymPulseNavigationDefaults
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.feature.settings.AboutRoute
import app.gympulse.feature.settings.SettingsRoute
import app.gympulse.feature.workout.bar.ActiveWorkoutBar
import app.gympulse.feature.workout.bar.ActiveWorkoutServiceEffect
import app.gympulse.feature.workout.navigateToWorkout
import app.gympulse.navigation.GymPulseNavHost
import app.gympulse.navigation.TopLevelDestination

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymPulseApp(
    navController: NavHostController = rememberNavController(),
    avatarViewModel: AvatarViewModel = hiltViewModel(),
) {
    val avatarUrl by avatarViewModel.avatarUrl.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentTopLevel = TopLevelDestination.entries.firstOrNull { currentDestination.isOn(it) }
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    // Adaptive Navigation: Navigation Bar auf Handys, Navigation Rail auf Tablets und Foldables.
    // Im Workout und in den Einstellungen wird sie ausgeblendet.
    val layoutType = when {
        currentTopLevel == null -> NavigationSuiteType.None
        currentWindowWidthClass().isAtLeastMedium -> NavigationSuiteType.NavigationRail
        else -> NavigationSuiteType.NavigationBar
    }

    // Keeps the workout notification running while a session is active.
    ActiveWorkoutServiceEffect()

    val navigationItemColors = GymPulseNavigationDefaults.itemColors()
    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteColors = GymPulseNavigationDefaults.suiteColors(),
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
                    colors = navigationItemColors,
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(currentTopLevel?.labelRes ?: R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
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
                            AvatarMenu(
                                avatarUrl = avatarUrl,
                                onOpenSettings = { navController.navigate(SettingsRoute) },
                                onOpenAbout = { navController.navigate(AboutRoute) },
                            )
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

/** Avatar button in the top bar; its menu leads to the settings and to "Über die App". */
@Composable
private fun AvatarMenu(avatarUrl: String?, onOpenSettings: () -> Unit, onOpenAbout: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // The Box anchors the dropdown to the avatar button instead of the top-left corner.
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.sizeIn(minWidth = Sizing.minTouchTarget, minHeight = Sizing.minTouchTarget),
        ) {
            val description = stringResource(R.string.action_account)
            val fallback = @Composable {
                Icon(
                    Icons.Filled.AccountCircle,
                    contentDescription = description,
                    modifier = Modifier.size(Sizing.iconLarge),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (avatarUrl == null) {
                fallback()
            } else {
                SubcomposeAsyncImage(
                    model = avatarUrl,
                    contentDescription = description,
                    contentScale = ContentScale.Crop,
                    loading = { fallback() },
                    error = { fallback() },
                    modifier = Modifier.size(Sizing.iconLarge).clip(CircleShape),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.large,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenSettings()
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_about)) },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenAbout()
                },
            )
        }
    }
}
