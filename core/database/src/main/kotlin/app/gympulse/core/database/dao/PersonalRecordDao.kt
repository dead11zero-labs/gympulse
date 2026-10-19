package app.gympulse.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.gympulse.core.database.entity.PersonalRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalRecordDao {
    @Query("SELECT * FROM personal_record WHERE exercise_id = :exerciseId AND deleted_at IS NULL")
    fun observeForExercise(exerciseId: String): Flow<List<PersonalRecordEntity>>

    @Query("SELECT * FROM personal_record WHERE exercise_id = :exerciseId AND deleted_at IS NULL")
    suspend fun getForExercise(exerciseId: String): List<PersonalRecordEntity>

    @Upsert
    suspend fun upsert(records: List<PersonalRecordEntity>)
}
