package app.gympulse.feature.library

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Einstieg: Tab "Bibliothek" mit der Übungsliste. */
@Serializable
data object LibraryRoute

@Serializable
data class ExerciseDetailRoute(val exerciseId: String)

/** Neue eigene Übung ([exerciseId] = null) oder vorhandene bearbeiten. */
@Serializable
data class ExerciseEditRoute(val exerciseId: String? = null)

fun NavGraphBuilder.libraryScreen(navController: NavController) {
    composable<LibraryRoute> {
        LibraryScreen(
            onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) },
            onCreateExercise = { navController.navigate(ExerciseEditRoute()) },
        )
    }
    composable<ExerciseDetailRoute> {
        ExerciseDetailScreen(
            onEdit = { navController.navigate(ExerciseEditRoute(it)) },
            onBack = { navController.popBackStack() },
        )
    }
    composable<ExerciseEditRoute> {
        ExerciseEditScreen(onDone = { navController.popBackStack() })
    }
}
