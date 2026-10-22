package app.gympulse.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import app.gympulse.R
import app.gympulse.core.designsystem.icon.GymPulseIcons
import app.gympulse.feature.calendar.CalendarRoute
import app.gympulse.feature.dashboard.DashboardRoute
import app.gympulse.feature.library.LibraryRoute
import app.gympulse.feature.plans.PlansRoute
import kotlin.reflect.KClass

/**
 * Die vier Haupt-Ziele. Material 3: gefülltes Icon für das aktive Ziel, Outline-Icon sonst.
 * Icons stammen aus dem Designpaket (Material Symbols, siehe [GymPulseIcons]).
 */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @StringRes val labelRes: Int,
) {
    DASHBOARD(DashboardRoute, DashboardRoute::class, GymPulseIcons.DashboardFilled, GymPulseIcons.DashboardOutlined, R.string.nav_dashboard),
    PLANS(PlansRoute, PlansRoute::class, GymPulseIcons.FitnessCenterFilled, GymPulseIcons.FitnessCenterOutlined, R.string.nav_plans),
    LIBRARY(LibraryRoute, LibraryRoute::class, GymPulseIcons.MenuBookFilled, GymPulseIcons.MenuBookOutlined, R.string.nav_library),
    CALENDAR(CalendarRoute, CalendarRoute::class, GymPulseIcons.CalendarMonthFilled, GymPulseIcons.CalendarMonthOutlined, R.string.nav_calendar),
}
