package app.gympulse.core.data.repository

import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.database.dao.BodyDao
import app.gympulse.core.database.entity.ProgressPhotoEntity
import app.gympulse.core.model.Ids
import app.gympulse.core.model.PhotoPose
import app.gympulse.core.model.ProgressPhoto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/**
 * Metadaten der Fortschrittsfotos. Die Bilddateien selbst liegen lokal im App-Speicher; die
 * Spalte `local_uri` enthält nur den Dateinamen, damit der Pfad bei Umzügen des App-Verzeichnisses stabil bleibt.
 */
interface ProgressPhotoRepository {
    /** Alle Fotos, neueste zuerst. */
    fun observeAll(): Flow<List<ProgressPhoto>>

    /** Legt einen Eintrag für eine bereits gespeicherte Bilddatei an. */
    suspend fun add(fileName: String, note: String?): ProgressPhoto

    /** Löscht den Eintrag (Soft-Delete); die Bilddatei entfernt der Aufrufer. */
    suspend fun delete(id: String)
}

class DefaultProgressPhotoRepository @Inject constructor(
    private val bodyDao: BodyDao,
    private val time: TimeProvider,
) : ProgressPhotoRepository {
    override fun observeAll(): Flow<List<ProgressPhoto>> =
        bodyDao.observePhotos().map { list -> list.map { it.toModel() } }

    override suspend fun add(fileName: String, note: String?): ProgressPhoto {
        val now = time.now().toEpochMilli()
        val entity = ProgressPhotoEntity(
            id = Ids.newId(now),
            takenAt = now,
            localUri = fileName,
            pose = PhotoPose.FRONT,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = now,
            updatedAt = now,
        )
        bodyDao.upsertPhoto(entity)
        return entity.toModel()
    }

    override suspend fun delete(id: String) {
        bodyDao.softDeletePhoto(id, time.now().toEpochMilli())
    }

    private fun ProgressPhotoEntity.toModel() = ProgressPhoto(
        id = id,
        takenAt = Instant.ofEpochMilli(takenAt),
        fileName = localUri,
        note = note,
    )
}
