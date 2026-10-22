package app.gympulse.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.gympulse.core.database.entity.SessionExerciseEntity
import app.gympulse.core.database.entity.SetEntryEntity
import app.gympulse.core.database.entity.WorkoutSessionEntity
import app.gympulse.core.database.relation.SessionVolumeRow
import app.gympulse.core.database.relation.SessionWithExercises
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Transaction
    @Query("SELECT * FROM workout_session WHERE status = 'ACTIVE' AND deleted_at IS NULL ORDER BY started_at DESC LIMIT 1")
    fun observeActive(): Flow<SessionWithExercises?>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :id")
    fun observeById(id: String): Flow<SessionWithExercises?>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun getById(id: String): SessionWithExercises?

    @Query(
        """
        SELECT * FROM workout_session
        WHERE status = 'COMPLETED' AND deleted_at IS NULL
        ORDER BY ended_at DESC LIMIT :limit
        """,
    )
    fun observeRecentCompleted(limit: Int): Flow<List<WorkoutSessionEntity>>

    @Query(
        """
        SELECT * FROM workout_session
        WHERE status = 'COMPLETED' AND deleted_at IS NULL
          AND ended_at >= :fromMillis AND ended_at < :toMillis
        ORDER BY ended_at
        """,
    )
    fun observeCompletedBetween(fromMillis: Long, toMillis: Long): Flow<List<WorkoutSessionEntity>>

    /**
     * Abgeschlossene Sessions mit Beginn ab [fromMillis] samt Übungen und Sätzen, älteste zuerst.
     * Grundlage der Trainingsauswertung (`core:insights`); Soft-Deletes der Kindzeilen filtern die Mapper.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM workout_session
        WHERE status = 'COMPLETED' AND deleted_at IS NULL AND started_at >= :fromMillis
        ORDER BY started_at
        """,
    )
    fun observeCompletedWithContentSince(fromMillis: Long): Flow<List<SessionWithExercises>>

    /**
     * Volumen je abgeschlossener Session im Zeitraum [fromMillis, toMillis).
     * Zählt nur erledigte Sätze außer Aufwärmsätzen (sofern [includeWarmups] false ist).
     * Formel wie [app.gympulse.core.model.setVolumeKg]:
     * (Körpergewicht × Faktor + Zusatzgewicht) × Wiederholungen.
     */
    @Query(
        """
        SELECT ws.id AS sessionId,
               ws.ended_at AS endedAt,
               COALESCE(SUM(
                   (COALESCE(e.bodyweight_factor * ws.bodyweight_kg, 0) + COALESCE(s.weight_kg, 0)) * s.reps
               ), 0) AS volumeKg
        FROM workout_session ws
        LEFT JOIN session_exercise se ON se.session_id = ws.id AND se.deleted_at IS NULL
        LEFT JOIN exercise e ON e.id = se.exercise_id
        LEFT JOIN set_entry s ON s.session_exercise_id = se.id
              AND s.deleted_at IS NULL
              AND s.is_completed = 1
              AND s.reps > 0
              AND (:includeWarmups = 1 OR s.set_type != 'WARMUP')
        WHERE ws.status = 'COMPLETED' AND ws.deleted_at IS NULL
          AND ws.ended_at >= :fromMillis AND ws.ended_at < :toMillis
        GROUP BY ws.id
        ORDER BY ws.ended_at
        """,
    )
    fun observeSessionVolumes(fromMillis: Long, toMillis: Long, includeWarmups: Boolean): Flow<List<SessionVolumeRow>>

    /**
     * Sätze der letzten abgeschlossenen Session mit dieser Übung, für die "Vorher"-Spalte
     * und das Vorbefüllen.
     */
    @Query(
        """
        SELECT s.* FROM set_entry s
        JOIN session_exercise se ON se.id = s.session_exercise_id
        WHERE se.id = (
            SELECT se2.id FROM session_exercise se2
            JOIN workout_session ws ON ws.id = se2.session_id
            WHERE se2.exercise_id = :exerciseId
              AND ws.status = 'COMPLETED' AND ws.deleted_at IS NULL AND se2.deleted_at IS NULL
              AND (:excludeSessionId IS NULL OR ws.id != :excludeSessionId)
            ORDER BY ws.ended_at DESC LIMIT 1
        )
        AND s.deleted_at IS NULL
        ORDER BY s.set_index
        """,
    )
    suspend fun getPreviousSets(exerciseId: String, excludeSessionId: String? = null): List<SetEntryEntity>

    @Upsert
    suspend fun upsertSession(session: WorkoutSessionEntity)

    @Upsert
    suspend fun upsertSessionExercises(exercises: List<SessionExerciseEntity>)

    @Upsert
    suspend fun upsertSets(sets: List<SetEntryEntity>)

    @Transaction
    suspend fun insertSessionWithContent(
        session: WorkoutSessionEntity,
        exercises: List<SessionExerciseEntity>,
        sets: List<SetEntryEntity>,
    ) {
        upsertSession(session)
        upsertSessionExercises(exercises)
        upsertSets(sets)
    }

    /** Writes an exercise and its sets atomically so observers never see the exercise without sets. */
    @Transaction
    suspend fun insertExerciseWithSets(exercise: SessionExerciseEntity, sets: List<SetEntryEntity>) {
        upsertSessionExercises(listOf(exercise))
        upsertSets(sets)
    }

    @Query("UPDATE set_entry SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDeleteSet(id: String, now: Long)

    @Query("UPDATE session_exercise SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDeleteSessionExercise(id: String, now: Long)
}
