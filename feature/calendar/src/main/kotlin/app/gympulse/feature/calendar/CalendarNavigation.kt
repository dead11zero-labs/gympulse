package app.gympulse.feature.calendar

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object CalendarRoute

fun NavGraphBuilder.calendarScreen() {
    composable<CalendarRoute> {
        CalendarScreen()
    }
}
