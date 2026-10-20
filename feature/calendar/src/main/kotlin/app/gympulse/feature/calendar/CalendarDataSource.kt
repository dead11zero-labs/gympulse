package app.gympulse.feature.calendar

import app.gympulse.core.data.repository.CompletedSessionSummary
import app.gympulse.core.data.repository.PlanRepository
import app.gympulse.core.data.repository.ScheduleRepository
import app.gympulse.core.data.repository.StatsRepository
import app.gympulse.core.model.ScheduleOverride
import app.gympulse.core.model.ScheduleRule
import app.gympulse.core.model.WorkoutPlan
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Datenzugriff des Kalenders; in Tests durch eine Fake-Implementierung ersetzbar. */
interface CalendarDataSource {
    fun observePlans(): Flow<List<WorkoutPlan>>

    fun observeRules(): Flow<List<ScheduleRule>>

    fun observeOverrides(from: LocalDate, to: LocalDate): Flow<List<ScheduleOverride>>

    /** Abgeschlossene Sessions mit Ende in [from, to). */
    fun observeCompletedSessions(from: Instant, to: Instant): Flow<List<CompletedSessionSummary>>

    /** Weist dem Wochentag ab heute einen Plan zu; `null` entfernt die Zuweisung. */
    suspend fun setWeekdayPlan(weekday: DayOfWeek, planId: String?)

    suspend fun skipDay(date: LocalDate)

    suspend fun moveDay(from: LocalDate, to: LocalDate, planId: String)

    suspend fun addPlan(date: LocalDate, planId: String)

    suspend fun clearOverride(date: LocalDate)
}

class DefaultCalendarDataSource @Inject constructor(
    private val plans: PlanRepository,
    private val schedule: ScheduleRepository,
    private val stats: StatsRepository,
) : CalendarDataSource {
    override fun observePlans(): Flow<List<WorkoutPlan>> = plans.observePlans()

    override fun observeRules(): Flow<List<ScheduleRule>> = schedule.observeRules()

    override fun observeOverrides(from: LocalDate, to: LocalDate): Flow<List<ScheduleOverride>> =
        schedule.observeOverrides(from, to)

    override fun observeCompletedSessions(from: Instant, to: Instant): Flow<List<CompletedSessionSummary>> =
        stats.observeCompletedSessions(from, to)

    override suspend fun setWeekdayPlan(weekday: DayOfWeek, planId: String?) =
        schedule.setWeekdayPlan(weekday, planId)

    override suspend fun skipDay(date: LocalDate) = schedule.skipDay(date)

    override suspend fun moveDay(from: LocalDate, to: LocalDate, planId: String) =
        schedule.moveDay(from, to, planId)

    override suspend fun addPlan(date: LocalDate, planId: String) = schedule.addPlan(date, planId)

    override suspend fun clearOverride(date: LocalDate) = schedule.clearOverride(date)
}

/**
 * Wochenstart laut Einstellungen. Eigene Schnittstelle des Kalenders, damit dieses Modul nicht vom
 * Dashboard abhängt; die App bindet beide Schnittstellen an die Einstellungen.
 */
interface CalendarWeekStartProvider {
    val weekStartsOn: StateFlow<DayOfWeek>
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarModule {
    @Binds
    abstract fun bindDataSource(impl: DefaultCalendarDataSource): CalendarDataSource
}
