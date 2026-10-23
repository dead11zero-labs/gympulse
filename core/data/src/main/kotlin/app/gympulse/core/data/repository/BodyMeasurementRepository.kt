package app.gympulse.core.data.repository

import app.gympulse.core.data.mapper.toModel
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.database.dao.BodyDao
import app.gympulse.core.database.entity.BodyMeasurementEntity
import app.gympulse.core.model.BodyMeasurement
import app.gympulse.core.model.Ids
import app.gympulse.core.model.MeasurementSource
import app.gympulse.core.model.MeasurementType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/** Körpermaße (Gewicht in kg, Körperfett in Prozent, Umfänge in cm). */
interface BodyMeasurementRepository {
    /** Alle Messwerte, neueste zuerst. */
    fun observeAll(): Flow<List<BodyMeasurement>>

    /** Legt pro Eintrag einen Messwert zum aktuellen Zeitpunkt an. */
    suspend fun record(values: Map<MeasurementType, Double>)

    /** Legt ein aus Health Connect gelesenes Körpergewicht (kg) mit dessen Messzeitpunkt an. */
    suspend fun recordHealthConnectWeight(measuredAt: Instant, weightKg: Double)

    /** Ändert den Wert eines Messwerts; unbekannte oder gelöschte IDs werden ignoriert. */
    suspend fun updateValue(id: String, value: Double)

    /** Löscht einen Messwert (Soft-Delete). */
    suspend fun delete(id: String)
}

class DefaultBodyMeasurementRepository @Inject constructor(
    private val bodyDao: BodyDao,
    private val time: TimeProvider,
) : BodyMeasurementRepository {
    override fun observeAll(): Flow<List<BodyMeasurement>> =
        bodyDao.observeAllMeasurements().map { list -> list.map { it.toModel() } }

    override suspend fun record(values: Map<MeasurementType, Double>) {
        val now = time.now().toEpochMilli()
        values.forEach { (type, value) ->
            bodyDao.upsertMeasurement(
                BodyMeasurementEntity(
                    id = Ids.newId(now),
                    measuredAt = now,
                    type = type,
                    value = value,
                    source = MeasurementSource.MANUAL,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    override suspend fun recordHealthConnectWeight(measuredAt: Instant, weightKg: Double) {
        val now = time.now().toEpochMilli()
        bodyDao.upsertMeasurement(
            BodyMeasurementEntity(
                id = Ids.newId(now),
                measuredAt = measuredAt.toEpochMilli(),
                type = MeasurementType.WEIGHT,
                value = weightKg,
                source = MeasurementSource.HEALTH_CONNECT,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun updateValue(id: String, value: Double) {
        val existing = bodyDao.getMeasurement(id) ?: return
        bodyDao.upsertMeasurement(existing.copy(value = value, updatedAt = time.now().toEpochMilli()))
    }

    override suspend fun delete(id: String) {
        bodyDao.softDeleteMeasurement(id, time.now().toEpochMilli())
    }
}
