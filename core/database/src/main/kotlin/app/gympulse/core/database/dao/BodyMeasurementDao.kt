package app.gympulse.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.gympulse.core.database.entity.BodyMeasurementEntity
import app.gympulse.core.database.entity.ProgressPhotoEntity
import app.gympulse.core.database.entity.UserProfileEntity
import app.gympulse.core.model.MeasurementType
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyDao {
    @Query("SELECT * FROM user_profile WHERE deleted_at IS NULL LIMIT 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Upsert
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM body_measurement WHERE type = :type AND deleted_at IS NULL ORDER BY measured_at")
    fun observeMeasurements(type: MeasurementType): Flow<List<BodyMeasurementEntity>>

    /** Letzter Messwert vor einem Zeitpunkt, z. B. das Körpergewicht zu Beginn einer Session. */
    @Query(
        """
        SELECT * FROM body_measurement
        WHERE type = :type AND deleted_at IS NULL AND measured_at <= :atMillis
        ORDER BY measured_at DESC LIMIT 1
        """,
    )
    suspend fun latestBefore(type: MeasurementType, atMillis: Long): BodyMeasurementEntity?

    @Upsert
    suspend fun upsertMeasurement(measurement: BodyMeasurementEntity)

    @Query("UPDATE body_measurement SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDeleteMeasurement(id: String, now: Long)

    @Query("SELECT * FROM progress_photo WHERE deleted_at IS NULL ORDER BY taken_at DESC")
    fun observePhotos(): Flow<List<ProgressPhotoEntity>>

    @Upsert
    suspend fun upsertPhoto(photo: ProgressPhotoEntity)
}
