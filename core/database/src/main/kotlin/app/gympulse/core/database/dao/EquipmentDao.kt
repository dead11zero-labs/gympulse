package app.gympulse.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.gympulse.core.database.entity.EquipmentEntity
import app.gympulse.core.database.entity.EquipmentSettingEntity
import app.gympulse.core.database.relation.EquipmentWithSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface EquipmentDao {
    @Transaction
    @Query("SELECT * FROM equipment WHERE deleted_at IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<EquipmentWithSettings>>

    @Transaction
    @Query("SELECT * FROM equipment WHERE id = :id AND deleted_at IS NULL")
    fun observeById(id: String): Flow<EquipmentWithSettings?>

    @Upsert
    suspend fun upsert(equipment: EquipmentEntity)

    @Upsert
    suspend fun upsertSettings(settings: List<EquipmentSettingEntity>)

    @Query("UPDATE equipment_setting SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDeleteSetting(id: String, now: Long)

    @Query("UPDATE equipment SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
