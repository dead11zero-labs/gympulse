package app.gympulse.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItemColors
import androidx.compose.runtime.Composable

/**
 * Farben der adaptiven Navigation (Navigation Bar < 600 dp, Rail ab 600 dp) in Kinetic Material:
 * - aktives Ziel: Pillen-Indikator in `primary`, Icon in `onPrimary`, Label in `onSurface`,
 * - inaktive Ziele: Icon und Label in `onSurfaceVariant`,
 * - Leiste auf `surfaceContainerLowest`, damit sie in die Gestenleiste übergeht.
 * Labels bleiben immer sichtbar (Material-Standard `alwaysShowLabel = true`).
 *
 * Nutzung im `NavigationSuiteScaffold`:
 * ```
 * NavigationSuiteScaffold(
 *     navigationSuiteColors = GymPulseNavigationDefaults.suiteColors(),
 *     navigationSuiteItems = {
 *         item(..., colors = GymPulseNavigationDefaults.itemColors())
 *     },
 * )
 * ```
 */
object GymPulseNavigationDefaults {

    /** Farben eines Navigationsziels in Bar und Rail. */
    @Composable
    fun itemColors(): NavigationSuiteItemColors {
        val scheme = MaterialTheme.colorScheme
        return NavigationSuiteDefaults.itemColors(
            navigationBarItemColors = NavigationBarItemDefaults.colors(
                selectedIconColor = scheme.onPrimary,
                selectedTextColor = scheme.onSurface,
                indicatorColor = scheme.primary,
                unselectedIconColor = scheme.onSurfaceVariant,
                unselectedTextColor = scheme.onSurfaceVariant,
            ),
            navigationRailItemColors = NavigationRailItemDefaults.colors(
                selectedIconColor = scheme.onPrimary,
                selectedTextColor = scheme.onSurface,
                indicatorColor = scheme.primary,
                unselectedIconColor = scheme.onSurfaceVariant,
                unselectedTextColor = scheme.onSurfaceVariant,
            ),
        )
    }

    /** Hintergrund von Bar und Rail. */
    @Composable
    fun suiteColors(): NavigationSuiteColors {
        val scheme = MaterialTheme.colorScheme
        return NavigationSuiteDefaults.colors(
            navigationBarContainerColor = scheme.surfaceContainerLowest,
            navigationBarContentColor = scheme.onSurfaceVariant,
            navigationRailContainerColor = scheme.surfaceContainerLowest,
            navigationRailContentColor = scheme.onSurfaceVariant,
        )
    }
}
