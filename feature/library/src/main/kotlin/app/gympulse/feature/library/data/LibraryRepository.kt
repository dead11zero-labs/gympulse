package app.gympulse.feature.library.data

import app.gympulse.core.model.Equipment
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.WorkoutPlan
import kotlinx.coroutines.flow.Flow

/** Datenzugriff der Bibliothek (Übungen und Pläne zum Hinzufügen). Per Fake in Tests austauschbar. */
interface LibraryRepository {
    /** Alle Übungen, alphabetisch nach dem Namen in [locale] (Fallback Englisch). */
    fun observeExercises(locale: String): Flow<List<LibraryExercise>>

    fun observeExercise(id: String, locale: String): Flow<ExerciseDetail?>

    suspend fun getExercise(id: String, locale: String): ExerciseDetail?

    fun observeEquipment(): Flow<List<Equipment>>

    /** Aktive (nicht gelöschte, nicht archivierte) Pläne, alphabetisch. */
    fun observePlans(): Flow<List<WorkoutPlan>>

    /**
     * Hängt die Übung mit den Standardwerten des Plan-Editors ans Ende des Plans.
     * Liefert false, wenn der Plan nicht (mehr) existiert.
     */
    suspend fun addExerciseToPlan(planId: String, exercise: Exercise): Boolean

    /** Sätze der zuletzt abgeschlossenen Session mit dieser Übung. */
    suspend fun previousSets(exerciseId: String): List<SetEntry>

    suspend fun setFavorite(id: String, favorite: Boolean)

    /** Legt eine eigene Übung an oder ändert sie; gibt die ID zurück. */
    suspend fun saveCustomExercise(draft: ExerciseDraft, locale: String): String

    /** Ändert nur die Nutzerfelder, die auch bei Seed-Übungen erlaubt sind. */
    suspend fun updateExerciseUserFields(id: String, notes: String?, defaultRestSeconds: Int?, equipmentId: String?)

    /** Löscht (Soft Delete) eine eigene Übung; Seed-Übungen bleiben unverändert. */
    suspend fun deleteCustomExercise(id: String)
}
