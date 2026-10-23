package app.gympulse.core.data.repository

import app.gympulse.core.data.mapper.toModel
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.database.dao.ExerciseDao
import app.gympulse.core.database.dao.PlanDao
import app.gympulse.core.database.dao.SessionDao
import app.gympulse.core.insights.InsightsEngine
import app.gympulse.core.insights.TrainingInsights
import app.gympulse.core.insights.buildExerciseHistories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Ergebnis der Trainingsauswertung samt Übungsnamen für die Anzeige.
 *
 * @property exerciseNames Name je Übungs-ID in der angefragten Sprache; fehlt eine ID, war die Übung
 *   nicht mehr vorhanden (gelöscht).
 */
data class InsightsReport(
    val insights: TrainingInsights,
    val exerciseNames: Map<String, String>,
)

/**
 * Lädt abgeschlossene Sessions, Übungen und Pläne aus Room und wertet sie mit [InsightsEngine] aus.
 * Alles läuft lokal; der Flow liefert nach jeder Änderung der Trainingsdaten ein neues Ergebnis.
 */
class InsightsRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val exerciseDao: ExerciseDao,
    private val planDao: PlanDao,
    private val time: TimeProvider,
) {
    private val engine = InsightsEngine()

    /** Auswertung der letzten [LOOKBACK_WEEKS] Wochen; Übungsnamen in [locale] (z. B. "de"). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeReport(locale: String): Flow<InsightsReport> = flow {
        emit(time.now().minus(LOOKBACK_WEEKS * DAYS_PER_WEEK, ChronoUnit.DAYS).toEpochMilli())
    }.flatMapLatest { from ->
        combine(
            sessionDao.observeCompletedWithContentSince(from),
            exerciseDao.observeAll(),
            planDao.observeActive(),
        ) { sessions, exercises, plans ->
            val zone = time.zone()
            val models = exercises.map { it.toModel(locale) }
            val histories = buildExerciseHistories(
                sessions = sessions.map { it.toModel() },
                exercises = models,
                plans = plans.map { it.toModel() },
                zoneId = zone,
            )
            InsightsReport(
                insights = engine.analyze(histories, time.now().atZone(zone).toLocalDate()),
                exerciseNames = models.associate { it.id to it.name },
            )
        }
    }.flowOn(Dispatchers.Default)

    companion object {
        /** Zeitraum, der in die Auswertung eingeht; deckt Regressionsfenster und Plateau-Erkennung ab. */
        const val LOOKBACK_WEEKS = 26L
        private const val DAYS_PER_WEEK = 7L
    }
}
