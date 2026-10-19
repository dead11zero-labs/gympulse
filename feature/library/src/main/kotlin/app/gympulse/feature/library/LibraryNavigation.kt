package app.gympulse.feature.library

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Einstieg: Tab "Bibliothek" mit Übungen und Geräten. */
@Serializable
data object LibraryRoute

@Serializable
data class ExerciseDetailRoute(val exerciseId: String)

/** Neue eigene Übung ([exerciseId] = null) oder vorhandene bearbeiten. */
@Serializable
data class ExerciseEditRoute(val exerciseId: String? = null)

/** Auswahl einer Gerätevorlage (oder "leeres Gerät") vor dem Anlegen. */
@Serializable
data object EquipmentTemplatesRoute

/** Neues Gerät (optional aus Vorlage [templateKey]) oder vorhandenes bearbeiten. */
@Serializable
data class EquipmentEditRoute(val equipmentId: String? = null, val templateKey: String? = null)

fun NavGraphBuilder.libraryScreen(navController: NavController) {
    composable<LibraryRoute> {
        LibraryScreen(
            onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) },
            onCreateExercise = { navController.navigate(ExerciseEditRoute()) },
            onOpenEquipment = { navController.navigate(EquipmentEditRoute(equipmentId = it)) },
            onCreateEquipment = { navController.navigate(EquipmentTemplatesRoute) },
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
    composable<EquipmentTemplatesRoute> {
        EquipmentTemplatesScreen(
            onPickTemplate = { templateKey ->
                // Die Auswahl ersetzt sich durch das Formular, "Zurück" führt wieder in die Bibliothek.
                navController.popBackStack()
                navController.navigate(EquipmentEditRoute(templateKey = templateKey))
            },
        )
    }
    composable<EquipmentEditRoute> {
        EquipmentEditScreen(onDone = { navController.popBackStack() })
    }
}
