package app.gympulse.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector
import app.gympulse.R
import app.gympulse.feature.calendar.CalendarRoute
import app.gympulse.feature.dashboard.DashboardRoute
import app.gympulse.feature.library.LibraryRoute
import app.gympulse.feature.plans.PlansRoute
import kotlin.reflect.KClass

/** Die vier Tabs der Bottom Navigation. Icons sind Platzhalter bis zum finalen Design. */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
) {
    DASHBOARD(DashboardRoute, DashboardRoute::class, Icons.Filled.Home, R.string.nav_dashboard),
    PLANS(PlansRoute, PlansRoute::class, Icons.AutoMirrored.Filled.List, R.string.nav_plans),
    LIBRARY(LibraryRoute, LibraryRoute::class, Icons.Filled.Build, R.string.nav_library),
    CALENDAR(CalendarRoute, CalendarRoute::class, Icons.Filled.DateRange, R.string.nav_calendar),
}
