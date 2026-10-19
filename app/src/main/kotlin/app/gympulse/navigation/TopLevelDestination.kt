package app.gympulse.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector
import app.gympulse.R
import app.gympulse.feature.calendar.CalendarRoute
import app.gympulse.feature.dashboard.DashboardRoute
import app.gympulse.feature.library.LibraryRoute
import app.gympulse.feature.plans.PlansRoute
import kotlin.reflect.KClass

/**
 * Die vier Haupt-Ziele. Material 3: gefülltes Icon für das aktive Ziel, Outline-Icon sonst.
 * Icons sind Platzhalter bis zum finalen Design.
 */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @StringRes val labelRes: Int,
) {
    DASHBOARD(DashboardRoute, DashboardRoute::class, Icons.Filled.Home, Icons.Outlined.Home, R.string.nav_dashboard),
    PLANS(PlansRoute, PlansRoute::class, Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List, R.string.nav_plans),
    LIBRARY(LibraryRoute, LibraryRoute::class, Icons.Filled.Build, Icons.Outlined.Build, R.string.nav_library),
    CALENDAR(CalendarRoute, CalendarRoute::class, Icons.Filled.DateRange, Icons.Outlined.DateRange, R.string.nav_calendar),
}
