package app.gympulse.feature.dashboard

import app.gympulse.core.data.repository.CompletedSessionSummary
import app.gympulse.core.data.repository.InsightsRepository
import app.gympulse.core.data.repository.StatsRepository
import app.gympulse.core.data.repository.WeeklyVolume
import app.gympulse.core.data.repository.WorkoutRepository
import app.gympulse.core.insightcards.InsightsUi
import app.gympulse.core.insightcards.toDashboardInsights
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

/** Laufende Session, die auf dem Dashboard zum Fortsetzen angeboten wird. */
data class ActiveWorkout(
    val sessionId: String,
    val title: String?,
    val startedAt: Instant,
)

/** Datenzugriff des Dashboards; in Tests durch eine Fake-Implementierung ersetzbar. */
interface DashboardDataSource {
    fun observeWeeklyVolume(weekOffset: Long, weekStartsOn: DayOfWeek): Flow<WeeklyVolume>

    /** Endzeitpunkte abgeschlossener Sessions im Zeitraum [from, to). */
    fun observeSessionEndTimes(from: Instant, to: Instant): Flow<List<Instant>>

    fun observeRecentSessions(limit: Int): Flow<List<CompletedSessionSummary>>

    fun observeActiveWorkout(): Flow<ActiveWorkout?>

    /** Wichtigste Befunde der Trainingsauswertung als fertige Karten (lokal berechnet). */
    fun observeInsights(): Flow<InsightsUi>
}

class DefaultDashboardDataSource @Inject constructor(
    private val stats: StatsRepository,
    private val workouts: WorkoutRepository,
    private val insights: InsightsRepository,
) : DashboardDataSource {
    override fun observeWeeklyVolume(weekOffset: Long, weekStartsOn: DayOfWeek): Flow<WeeklyVolume> =
        stats.observeWeeklyVolume(weekOffset = weekOffset, weekStartsOn = weekStartsOn)

    override fun observeSessionEndTimes(from: Instant, to: Instant): Flow<List<Instant>> =
        stats.observeSessionVolumes(from, to).map { sessions -> sessions.map { it.endedAt } }

    override fun observeRecentSessions(limit: Int): Flow<List<CompletedSessionSummary>> =
        stats.observeRecentSessions(limit)

    override fun observeActiveWorkout(): Flow<ActiveWorkout?> =
        workouts.observeActiveSession().map { session ->
            session?.let { ActiveWorkout(it.id, it.title, it.startedAt) }
        }

    override fun observeInsights(): Flow<InsightsUi> =
        insights.observeReport(Locale.getDefault().language).map { report ->
            report.insights.toDashboardInsights(report.exerciseNames)
        }
}

/** Wochenziel (Trainings pro Woche). Gebunden im app-Modul aus den Einstellungen. */
interface WeeklyGoalProvider {
    val weeklyGoal: StateFlow<Int>

    companion object {
        const val DEFAULT_GOAL = 3
    }
}

/** Wochenstart laut Einstellungen. Gebunden im app-Modul aus den Einstellungen. */
interface WeekStartProvider {
    val weekStartsOn: StateFlow<DayOfWeek>
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DashboardModule {
    @Binds
    abstract fun bindDataSource(impl: DefaultDashboardDataSource): DashboardDataSource
}
