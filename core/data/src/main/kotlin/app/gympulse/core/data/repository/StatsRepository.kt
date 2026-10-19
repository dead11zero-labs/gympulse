package app.gympulse.core.data.repository

import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.database.dao.SessionDao
import app.gympulse.core.model.SessionVolume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** Volumen der laufenden Woche, aufgeteilt nach Tagen. */
data class WeeklyVolume(
    val weekStart: LocalDate,
    /** Sieben Werte, beginnend mit [weekStart]. */
    val dailyVolumeKg: List<Double>,
) {
    val totalKg: Double get() = dailyVolumeKg.sum()
}

class StatsRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val time: TimeProvider,
) {
    fun observeSessionVolumes(from: Instant, to: Instant, includeWarmups: Boolean = false): Flow<List<SessionVolume>> =
        sessionDao.observeSessionVolumes(from.toEpochMilli(), to.toEpochMilli(), includeWarmups).map { rows ->
            rows.map { SessionVolume(it.sessionId, Instant.ofEpochMilli(it.endedAt), it.volumeKg) }
        }

    /**
     * @param weekOffset 0 = aktuelle Woche, -1 = Vorwoche
     * @param weekStartsOn Wochenstart laut Einstellungen
     */
    fun observeWeeklyVolume(
        weekOffset: Long = 0,
        weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
        includeWarmups: Boolean = false,
    ): Flow<WeeklyVolume> {
        val zone = time.zone()
        val weekStart = time.now().atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(weekStartsOn))
            .plusWeeks(weekOffset)
        val from = weekStart.atStartOfDay(zone).toInstant()
        val to = weekStart.plusDays(7).atStartOfDay(zone).toInstant()
        return observeSessionVolumes(from, to, includeWarmups).map { sessions ->
            val daily = DoubleArray(7)
            sessions.forEach { session ->
                val day = session.endedAt.atZone(zone).toLocalDate()
                val index = (day.toEpochDay() - weekStart.toEpochDay()).toInt()
                if (index in 0..6) daily[index] += session.volumeKg
            }
            WeeklyVolume(weekStart, daily.toList())
        }
    }
}
