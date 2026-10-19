package app.gympulse.core.data.repository

import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.database.dao.BodyDao
import app.gympulse.core.database.entity.BodyMeasurementEntity
import app.gympulse.core.database.entity.UserProfileEntity
import app.gympulse.core.model.Ids
import app.gympulse.core.model.MeasurementSource
import app.gympulse.core.model.MeasurementType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Profil des Nutzers. Das Körpergewicht ist keine Profilspalte, sondern der jüngste
 * WEIGHT-Messwert; WorkoutRepository übernimmt ihn beim Start als Session-Körpergewicht.
 */
data class UserProfile(
    val name: String?,
    val heightCm: Double?,
    val bodyWeightKg: Double?,
)

class ProfileRepository @Inject constructor(
    private val bodyDao: BodyDao,
    private val time: TimeProvider,
) {
    fun observeProfile(): Flow<UserProfile> = combine(
        bodyDao.observeProfile(),
        bodyDao.observeMeasurements(MeasurementType.WEIGHT),
    ) { profile, weights ->
        UserProfile(
            name = profile?.name,
            heightCm = profile?.heightCm,
            bodyWeightKg = weights.lastOrNull()?.value,
        )
    }

    /** Speichert Name und Größe; leere Werte werden als "nicht gesetzt" abgelegt. */
    suspend fun updateProfile(name: String?, heightCm: Double?) {
        val now = time.now().toEpochMilli()
        val existing = bodyDao.observeProfile().first()
        bodyDao.upsertProfile(
            UserProfileEntity(
                id = existing?.id ?: Ids.newId(now),
                name = name?.trim()?.takeIf { it.isNotEmpty() },
                birthYear = existing?.birthYear,
                heightCm = heightCm,
                sex = existing?.sex,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    /** Legt einen neuen manuellen Gewichts-Messwert (kg) zum aktuellen Zeitpunkt an. */
    suspend fun recordBodyWeight(weightKg: Double) {
        val now = time.now().toEpochMilli()
        bodyDao.upsertMeasurement(
            BodyMeasurementEntity(
                id = Ids.newId(now),
                measuredAt = now,
                type = MeasurementType.WEIGHT,
                value = weightKg,
                source = MeasurementSource.MANUAL,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}
