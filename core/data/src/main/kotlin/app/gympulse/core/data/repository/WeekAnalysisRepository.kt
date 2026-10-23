package app.gympulse.core.data.repository

import app.gympulse.core.data.mapper.toModel
import app.gympulse.core.database.dao.ExerciseDao
import app.gympulse.core.database.dao.SessionDao
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.time.Instant
import javax.inject.Inject

/** Abgeschlossene Sessions samt Sätzen und die zugehörigen Übungen (Name, Kategorie, Muskeln). */
data class WeekAnalysisData(
    val sessions: List<WorkoutSession>,
    /** Übungen nach ID; fehlt eine ID, wurde die Übung gelöscht. */
    val exercises: Map<String, Exercise>,
)

/** Rohdaten für die Wochenanalyse; ausgewertet wird im Feature-Modul. */
class WeekAnalysisRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val exerciseDao: ExerciseDao,
) {
    /** Sessions mit Beginn ab [from] und alle Übungen mit Namen in [locale] (z. B. "de"). */
    fun observe(from: Instant, locale: String): Flow<WeekAnalysisData> =
        combine(
            sessionDao.observeCompletedWithContentSince(from.toEpochMilli()),
            exerciseDao.observeAll(),
        ) { sessions, exercises ->
            WeekAnalysisData(
                sessions = sessions.map { it.toModel() },
                exercises = exercises.map { it.toModel(locale) }.associateBy { it.id },
            )
        }.flowOn(Dispatchers.Default)
}
