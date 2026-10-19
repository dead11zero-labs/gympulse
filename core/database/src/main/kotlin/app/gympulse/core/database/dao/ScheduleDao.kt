package app.gympulse.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.gympulse.core.database.entity.ScheduleOverrideEntity
import app.gympulse.core.database.entity.ScheduleRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_rule WHERE deleted_at IS NULL")
    fun observeRules(): Flow<List<ScheduleRuleEntity>>

    /** Ausnahmen im Zeitraum; Datumswerte sind ISO-Strings und damit lexikografisch sortierbar. */
    @Query("SELECT * FROM schedule_override WHERE deleted_at IS NULL AND date >= :fromDate AND date <= :toDate")
    fun observeOverrides(fromDate: String, toDate: String): Flow<List<ScheduleOverrideEntity>>

    @Upsert
    suspend fun upsertRule(rule: ScheduleRuleEntity)

    @Upsert
    suspend fun upsertOverride(override: ScheduleOverrideEntity)

    @Query("UPDATE schedule_rule SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDeleteRule(id: String, now: Long)

    @Query("UPDATE schedule_override SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDeleteOverride(id: String, now: Long)
}
