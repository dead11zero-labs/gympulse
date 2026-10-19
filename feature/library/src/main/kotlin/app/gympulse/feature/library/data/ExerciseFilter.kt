package app.gympulse.feature.library.data

import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MuscleGroup
import java.text.Normalizer

/** Aktive Such- und Filterkriterien der Übungsliste. */
data class ExerciseFilter(
    val query: String = "",
    val muscle: MuscleGroup? = null,
    val equipmentType: EquipmentType? = null,
    val category: ExerciseCategory? = null,
    val favoritesOnly: Boolean = false,
) {
    val hasFacets: Boolean
        get() = muscle != null || equipmentType != null || category != null || favoritesOnly

    val isActive: Boolean
        get() = hasFacets || query.isNotBlank()
}

private val diacritics = Regex("\\p{Mn}+")

/** Kleinschreibung ohne Akzente und Umlautpunkte, damit "Kreuzheben" und "kreuzhében" gleich suchen. */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(diacritics, "")
        .replace("ß", "ss")
        .lowercase()

/**
 * Filtert die Übungen. Jedes Wort der Suche muss im Suchtext vorkommen (UND-Verknüpfung);
 * der Muskelfilter prüft die primären Muskeln.
 */
fun filterExercises(all: List<LibraryExercise>, filter: ExerciseFilter): List<Exercise> {
    val tokens = normalizeForSearch(filter.query).split(' ').filter { it.isNotEmpty() }
    return all.asSequence()
        .filter { item ->
            val exercise = item.exercise
            (filter.muscle == null || filter.muscle in exercise.primaryMuscles) &&
                (filter.equipmentType == null || exercise.equipmentType == filter.equipmentType) &&
                (filter.category == null || exercise.category == filter.category) &&
                (!filter.favoritesOnly || exercise.isFavorite) &&
                tokens.all { token -> item.searchKey.contains(token) }
        }
        .map { it.exercise }
        .toList()
}
