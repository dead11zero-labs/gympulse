package app.gympulse.core.data.repository

import app.gympulse.core.data.mapper.toModel
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.database.dao.ExerciseDao
import app.gympulse.core.model.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val time: TimeProvider,
) {
    /** Alle Übungen in der gewünschten Sprache, alphabetisch sortiert. */
    fun observeExercises(locale: String): Flow<List<Exercise>> =
        exerciseDao.observeAll().map { rows ->
            rows.map { it.toModel(locale) }.sortedBy { it.name.lowercase() }
        }

    fun observeExercise(id: String, locale: String): Flow<Exercise?> =
        exerciseDao.observeById(id).map { it?.toModel(locale) }

    /** Übung inklusive gelöschter, z. B. für die Namen in alten Sessions. */
    suspend fun getExerciseIncludingDeleted(id: String, locale: String): Exercise? =
        exerciseDao.getById(id)?.toModel(locale)

    suspend fun setFavorite(id: String, favorite: Boolean) =
        exerciseDao.setFavorite(id, favorite, time.now().toEpochMilli())

    suspend fun delete(id: String) = exerciseDao.softDelete(id, time.now().toEpochMilli())
}
