package app.gympulse.feature.library.data

import app.gympulse.core.model.Equipment
import app.gympulse.core.model.EquipmentTemplate
import app.gympulse.core.model.SetEntry
import kotlinx.coroutines.flow.Flow

/** Datenzugriff der Bibliothek (Übungen und Geräte). Per Fake in Tests austauschbar. */
interface LibraryRepository {
    /** Alle Übungen, alphabetisch nach dem Namen in [locale] (Fallback Englisch). */
    fun observeExercises(locale: String): Flow<List<LibraryExercise>>

    fun observeExercise(id: String, locale: String): Flow<ExerciseDetail?>

    suspend fun getExercise(id: String, locale: String): ExerciseDetail?

    fun observeEquipment(): Flow<List<Equipment>>

    fun observeEquipmentById(id: String): Flow<Equipment?>

    suspend fun templates(locale: String): List<EquipmentTemplate>

    /** Sätze der zuletzt abgeschlossenen Session mit dieser Übung. */
    suspend fun previousSets(exerciseId: String): List<SetEntry>

    suspend fun setFavorite(id: String, favorite: Boolean)

    /** Legt eine eigene Übung an oder ändert sie; gibt die ID zurück. */
    suspend fun saveCustomExercise(draft: ExerciseDraft, locale: String): String

    /** Ändert nur die Nutzerfelder, die auch bei Seed-Übungen erlaubt sind. */
    suspend fun updateExerciseUserFields(id: String, notes: String?, defaultRestSeconds: Int?, equipmentId: String?)

    /** Löscht (Soft Delete) eine eigene Übung; Seed-Übungen bleiben unverändert. */
    suspend fun deleteCustomExercise(id: String)

    suspend fun saveEquipment(draft: EquipmentDraft): String

    /** Löscht das Gerät (Soft Delete) und löst die Zuordnung aller Übungen. */
    suspend fun deleteEquipment(id: String)
}
