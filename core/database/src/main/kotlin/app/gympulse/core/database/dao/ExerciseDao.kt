package app.gympulse.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.gympulse.core.database.entity.ExerciseEntity
import app.gympulse.core.database.entity.ExerciseMuscleEntity
import app.gympulse.core.database.entity.ExerciseTranslationEntity
import app.gympulse.core.database.relation.ExerciseWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Transaction
    @Query("SELECT * FROM exercise WHERE deleted_at IS NULL")
    fun observeAll(): Flow<List<ExerciseWithDetails>>

    @Transaction
    @Query("SELECT * FROM exercise WHERE id = :id AND deleted_at IS NULL")
    fun observeById(id: String): Flow<ExerciseWithDetails?>

    @Transaction
    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun getById(id: String): ExerciseWithDetails?

    @Query("SELECT id FROM exercise WHERE slug = :slug")
    suspend fun findIdBySlug(slug: String): String?

    @Query("SELECT COUNT(*) FROM exercise WHERE deleted_at IS NULL")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertExercises(exercises: List<ExerciseEntity>)

    @Upsert
    suspend fun upsertTranslations(translations: List<ExerciseTranslationEntity>)

    @Upsert
    suspend fun upsertMuscles(muscles: List<ExerciseMuscleEntity>)

    @Transaction
    suspend fun upsertFull(
        exercises: List<ExerciseEntity>,
        translations: List<ExerciseTranslationEntity>,
        muscles: List<ExerciseMuscleEntity>,
    ) {
        upsertExercises(exercises)
        upsertTranslations(translations)
        upsertMuscles(muscles)
    }

    @Query("UPDATE exercise SET is_favorite = :favorite, updated_at = :now WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, now: Long)

    @Query("UPDATE exercise SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
