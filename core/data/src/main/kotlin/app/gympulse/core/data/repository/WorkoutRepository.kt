package app.gympulse.core.data.repository

import app.gympulse.core.data.mapper.encodeSettings
import app.gympulse.core.data.mapper.toModel
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.database.dao.BodyDao
import app.gympulse.core.database.dao.EquipmentDao
import app.gympulse.core.database.dao.ExerciseDao
import app.gympulse.core.database.dao.PlanDao
import app.gympulse.core.database.dao.SessionDao
import app.gympulse.core.database.entity.SessionExerciseEntity
import app.gympulse.core.database.entity.SetEntryEntity
import app.gympulse.core.database.entity.WorkoutSessionEntity
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.Ids
import app.gympulse.core.model.MeasurementType
import app.gympulse.core.model.SessionStatus
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.SetType
import app.gympulse.core.model.WorkoutSession
import app.gympulse.core.model.setVolumeKg
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/**
 * Lebenszyklus einer Trainingssession: starten (aus Plan oder frei), Sätze erfassen,
 * beenden oder verwerfen. Die Planstruktur wird beim Start kopiert, damit spätere
 * Planänderungen die Historie nicht verfälschen.
 */
class WorkoutRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val planDao: PlanDao,
    private val exerciseDao: ExerciseDao,
    private val equipmentDao: EquipmentDao,
    private val bodyDao: BodyDao,
    private val time: TimeProvider,
) {
    fun observeActiveSession(): Flow<WorkoutSession?> = sessionDao.observeActive().map { it?.toModel() }

    fun observeSession(id: String): Flow<WorkoutSession?> = sessionDao.observeById(id).map { it?.toModel() }

    /**
     * Startet eine Session. Mit [planId] werden die Planübungen samt Zielsätzen übernommen
     * und mit den Werten der letzten Session vorbefüllt.
     */
    suspend fun startSession(planId: String?): String {
        val now = time.now().toEpochMilli()
        val sessionId = Ids.newId(now)
        val plan = planId?.let { planDao.getById(it) }
        val bodyweight = bodyDao.latestBefore(MeasurementType.WEIGHT, now)?.value

        val sessionExercises = mutableListOf<SessionExerciseEntity>()
        val sets = mutableListOf<SetEntryEntity>()
        plan?.exercises
            ?.filter { it.deletedAt == null }
            ?.sortedBy { it.sortOrder }
            ?.forEachIndexed { index, planExercise ->
                val sessionExerciseId = Ids.newId(now)
                sessionExercises += SessionExerciseEntity(
                    id = sessionExerciseId,
                    sessionId = sessionId,
                    exerciseId = planExercise.exerciseId,
                    sortOrder = index,
                    supersetGroup = planExercise.supersetGroup,
                    notes = null,
                    equipmentSettingsSnapshot = equipmentSnapshot(planExercise.exerciseId),
                    createdAt = now,
                    updatedAt = now,
                )
                val previous = sessionDao.getPreviousSets(planExercise.exerciseId)
                    .filter { it.setType != SetType.WARMUP }
                val isCardio = exerciseDao.getById(planExercise.exerciseId)?.exercise?.category == ExerciseCategory.CARDIO.key
                repeat(planExercise.targetSets.coerceAtLeast(1)) { setIndex ->
                    val template = previous.getOrNull(setIndex) ?: previous.lastOrNull()
                    sets += SetEntryEntity(
                        id = Ids.newId(now),
                        sessionExerciseId = sessionExerciseId,
                        setIndex = setIndex,
                        setType = SetType.WORKING,
                        weightKg = if (isCardio) null else template?.weightKg ?: planExercise.targetWeightKg,
                        reps = if (isCardio) null else template?.reps ?: planExercise.targetRepsMax ?: planExercise.targetRepsMin,
                        // Cardio plan entries store the target in minutes in targetRepsMin.
                        durationSeconds = template?.durationSeconds
                            ?: if (isCardio) planExercise.targetRepsMin?.times(SECONDS_PER_MINUTE) else null,
                        distanceMeters = if (isCardio) null else template?.distanceMeters,
                        rpe = null,
                        isCompleted = false,
                        completedAt = null,
                        createdAt = now,
                        updatedAt = now,
                    )
                }
            }

        sessionDao.insertSessionWithContent(
            session = WorkoutSessionEntity(
                id = sessionId,
                planId = plan?.plan?.id,
                title = plan?.plan?.name,
                startedAt = now,
                endedAt = null,
                status = SessionStatus.ACTIVE,
                bodyweightKg = bodyweight,
                rating = null,
                notes = null,
                totalVolumeKg = null,
                createdAt = now,
                updatedAt = now,
            ),
            exercises = sessionExercises,
            sets = sets,
        )
        return sessionId
    }

    /** Die laufende Session als Momentaufnahme, oder null. */
    suspend fun getActiveSession(): WorkoutSession? = sessionDao.observeActive().first()?.toModel()

    /**
     * Startet eine Session, sofern keine aktive existiert; sonst wird die aktive Session
     * zurückgegeben. So gibt es höchstens eine aktive Session gleichzeitig.
     */
    suspend fun startOrResumeSession(planId: String?): String =
        sessionDao.observeActive().first()?.session?.id ?: startSession(planId)

    /**
     * Hängt eine Übung an die Session an. Die Sätze werden mit den Werten der letzten
     * abgeschlossenen Session vorbefüllt (ohne Aufwärmsätze), mindestens ein Satz.
     *
     * @return ID des neuen SessionExercise oder null, wenn die Session nicht existiert.
     */
    suspend fun addExercise(sessionId: String, exerciseId: String): String? {
        val current = sessionDao.getById(sessionId) ?: return null
        val now = time.now().toEpochMilli()
        val sessionExerciseId = Ids.newId(now)
        val nextSortOrder = (current.exercises.maxOfOrNull { it.sessionExercise.sortOrder } ?: -1) + 1
        val previous = sessionDao.getPreviousSets(exerciseId, sessionId).filter { it.setType != SetType.WARMUP }
        val setCount = previous.size.coerceAtLeast(1)
        val sets = List(setCount) { setIndex ->
            val template = previous.getOrNull(setIndex)
            SetEntryEntity(
                id = Ids.newId(now),
                sessionExerciseId = sessionExerciseId,
                setIndex = setIndex,
                setType = SetType.WORKING,
                weightKg = template?.weightKg,
                reps = template?.reps,
                durationSeconds = template?.durationSeconds,
                distanceMeters = template?.distanceMeters,
                rpe = null,
                isCompleted = false,
                completedAt = null,
                createdAt = now,
                updatedAt = now,
            )
        }
        sessionDao.insertExerciseWithSets(
            SessionExerciseEntity(
                id = sessionExerciseId,
                sessionId = sessionId,
                exerciseId = exerciseId,
                sortOrder = nextSortOrder,
                supersetGroup = null,
                notes = null,
                equipmentSettingsSnapshot = equipmentSnapshot(exerciseId),
                createdAt = now,
                updatedAt = now,
            ),
            sets,
        )
        return sessionExerciseId
    }

    /** Entfernt eine Übung samt Sätzen aus der Session (Soft-Delete). */
    suspend fun removeExercise(sessionExerciseId: String) =
        sessionDao.softDeleteSessionExercise(sessionExerciseId, time.now().toEpochMilli())

    /** Setzt die Notiz einer Übung in der Session; leere Notizen werden als null gespeichert. */
    suspend fun updateExerciseNotes(sessionId: String, sessionExerciseId: String, notes: String?) {
        val current = sessionDao.getById(sessionId) ?: return
        val entity = current.exercises.firstOrNull { it.sessionExercise.id == sessionExerciseId }?.sessionExercise ?: return
        val now = time.now().toEpochMilli()
        sessionDao.upsertSessionExercises(
            listOf(entity.copy(notes = notes?.takeIf { it.isNotBlank() }, updatedAt = now)),
        )
    }

    /**
     * Fügt einen offenen Satz hinzu, vorbefüllt mit den Werten des letzten Satzes der Übung.
     *
     * @return ID des neuen Satzes oder null, wenn die Übung nicht existiert.
     */
    suspend fun addSet(sessionId: String, sessionExerciseId: String): String? {
        val current = sessionDao.getById(sessionId) ?: return null
        val exercise = current.exercises.firstOrNull { it.sessionExercise.id == sessionExerciseId } ?: return null
        val now = time.now().toEpochMilli()
        val visibleSets = exercise.sets.filter { it.deletedAt == null }.sortedBy { it.setIndex }
        val template = visibleSets.lastOrNull()
        val setId = Ids.newId(now)
        sessionDao.upsertSets(
            listOf(
                SetEntryEntity(
                    id = setId,
                    sessionExerciseId = sessionExerciseId,
                    setIndex = (exercise.sets.maxOfOrNull { it.setIndex } ?: -1) + 1,
                    setType = SetType.WORKING,
                    weightKg = template?.weightKg,
                    reps = template?.reps,
                    durationSeconds = template?.durationSeconds,
                    distanceMeters = template?.distanceMeters,
                    rpe = null,
                    isCompleted = false,
                    completedAt = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        return setId
    }

    /** Werte der letzten abgeschlossenen Session für die "Vorher"-Spalte. */
    suspend fun previousSets(exerciseId: String, currentSessionId: String?): List<SetEntry> =
        sessionDao.getPreviousSets(exerciseId, currentSessionId).map { it.toModel() }

    /**
     * Alle Sätze der angegebenen Übungen aus abgeschlossenen Sessions (inklusive gelöschter und
     * offener Sätze, siehe [HistorySet]). Grundlage für die Rekord-Erkennung.
     */
    suspend fun setHistory(exerciseIds: Collection<String>): List<HistorySet> {
        if (exerciseIds.isEmpty()) return emptyList()
        val wanted = exerciseIds.toSet()
        val factors = wanted.associateWith { exerciseDao.getById(it)?.exercise?.bodyweightFactor }
        return sessionDao.observeCompletedBetween(0L, Long.MAX_VALUE).first().flatMap { entity ->
            val content = sessionDao.getById(entity.id) ?: return@flatMap emptyList()
            content.exercises
                .filter { it.sessionExercise.exerciseId in wanted }
                .flatMap { item ->
                    item.sets.map { set ->
                        HistorySet(
                            setId = set.id,
                            sessionId = entity.id,
                            exerciseId = item.sessionExercise.exerciseId,
                            setType = set.setType,
                            weightKg = set.weightKg,
                            reps = set.reps,
                            isCompleted = set.isCompleted,
                            deletedAt = set.deletedAt ?: item.sessionExercise.deletedAt,
                            bodyweightKg = entity.bodyweightKg,
                            bodyweightFactor = factors[item.sessionExercise.exerciseId],
                        )
                    }
                }
        }
    }

    /** Speichert einen Satz (neu oder geändert). */
    suspend fun saveSet(sessionExerciseId: String, set: SetEntry) {
        val now = time.now().toEpochMilli()
        sessionDao.upsertSets(
            listOf(
                SetEntryEntity(
                    id = set.id,
                    sessionExerciseId = sessionExerciseId,
                    setIndex = set.setIndex,
                    setType = set.setType,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    durationSeconds = set.durationSeconds,
                    distanceMeters = set.distanceMeters,
                    rpe = set.rpe,
                    isCompleted = set.isCompleted,
                    completedAt = set.completedAt?.toEpochMilli() ?: if (set.isCompleted) now else null,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
    }

    suspend fun deleteSet(setId: String) = sessionDao.softDeleteSet(setId, time.now().toEpochMilli())

    /**
     * Beendet die Session; offene Sätze bleiben gespeichert, zählen aber nicht ins Volumen.
     * Mit [startedAt] wird zusätzlich die Startzeit überschrieben (z. B. wenn die Dauer nachträglich
     * geschätzt wurde).
     */
    suspend fun finishSession(
        sessionId: String,
        rating: Int? = null,
        notes: String? = null,
        startedAt: Instant? = null,
    ) {
        val now = time.now().toEpochMilli()
        val current = sessionDao.getById(sessionId) ?: return
        val session = current.toModel()
        val factors = session.exercises.associate { exercise ->
            exercise.exerciseId to exerciseDao.getById(exercise.exerciseId)?.exercise?.bodyweightFactor
        }
        val volume = session.exercises.sumOf { exercise ->
            exercise.sets
                .filter { it.isCompleted && it.setType != SetType.WARMUP }
                .sumOf { setVolumeKg(it.weightKg, it.reps, session.bodyweightKg, factors[exercise.exerciseId]) }
        }
        sessionDao.upsertSession(
            current.session.copy(
                status = SessionStatus.COMPLETED,
                startedAt = startedAt?.toEpochMilli() ?: current.session.startedAt,
                endedAt = now,
                rating = rating,
                notes = notes,
                totalVolumeKg = volume,
                updatedAt = now,
            ),
        )
    }

    suspend fun discardSession(sessionId: String) {
        val now = time.now().toEpochMilli()
        val current = sessionDao.getById(sessionId)?.session ?: return
        sessionDao.upsertSession(current.copy(status = SessionStatus.DISCARDED, endedAt = now, updatedAt = now))
    }

    private suspend fun equipmentSnapshot(exerciseId: String): String? {
        val equipmentId = exerciseDao.getById(exerciseId)?.exercise?.equipmentId ?: return null
        val equipment = equipmentDao.observeById(equipmentId).first() ?: return null
        return encodeSettings(
            equipment.settings.filter { it.deletedAt == null }.sortedBy { it.sortOrder }.associate { it.key to it.value },
        )
    }
}

private const val SECONDS_PER_MINUTE = 60
