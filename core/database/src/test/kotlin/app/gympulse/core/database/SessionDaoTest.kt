package app.gympulse.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gympulse.core.database.entity.ExerciseEntity
import app.gympulse.core.database.entity.SessionExerciseEntity
import app.gympulse.core.database.entity.SetEntryEntity
import app.gympulse.core.database.entity.WorkoutSessionEntity
import app.gympulse.core.model.SessionStatus
import app.gympulse.core.model.SetType
import app.gympulse.core.model.TrackingType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionDaoTest {
    private lateinit var db: GymPulseDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GymPulseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun sessionVolume_countsWorkingSetsAndBodyweightShare() = runTest {
        db.exerciseDao().upsertExercises(listOf(exercise("bench", factor = null), exercise("pullup", factor = 1.0)))
        insertSession(
            id = "s1",
            endedAt = 2_000,
            status = SessionStatus.COMPLETED,
            bodyweightKg = 80.0,
            sets = listOf(
                "bench" to set("a", SetType.WARMUP, 40.0, 10, completed = true),
                "bench" to set("b", SetType.WORKING, 100.0, 5, completed = true),
                "bench" to set("c", SetType.WORKING, 100.0, 5, completed = false),
                "pullup" to set("d", SetType.WORKING, null, 8, completed = true),
            ),
        )

        val volumes = db.sessionDao().observeSessionVolumes(0, 10_000, includeWarmups = false).first()

        // 100 × 5 + 80 × 1,0 × 8 = 1140; Aufwärmsatz und offener Satz zählen nicht
        assertEquals(1, volumes.size)
        assertEquals(1140.0, volumes.single().volumeKg, 0.001)
    }

    @Test
    fun sessionVolume_ignoresActiveAndOutOfRangeSessions() = runTest {
        db.exerciseDao().upsertExercises(listOf(exercise("bench", factor = null)))
        insertSession("active", endedAt = null, status = SessionStatus.ACTIVE, sets = listOf("bench" to set("a", SetType.WORKING, 100.0, 5, true)))
        insertSession("old", endedAt = 500, status = SessionStatus.COMPLETED, sets = listOf("bench" to set("b", SetType.WORKING, 100.0, 5, true)))

        val volumes = db.sessionDao().observeSessionVolumes(1_000, 10_000, includeWarmups = false).first()

        assertEquals(0, volumes.size)
    }

    @Test
    fun previousSets_returnsSetsOfLatestCompletedSession() = runTest {
        db.exerciseDao().upsertExercises(listOf(exercise("bench", factor = null)))
        insertSession("s1", endedAt = 1_000, status = SessionStatus.COMPLETED, sets = listOf("bench" to set("a", SetType.WORKING, 60.0, 10, true)))
        insertSession(
            "s2",
            endedAt = 2_000,
            status = SessionStatus.COMPLETED,
            sets = listOf(
                "bench" to set("b", SetType.WORKING, 62.5, 10, true, index = 0),
                "bench" to set("c", SetType.WORKING, 62.5, 9, true, index = 1),
            ),
        )

        val previous = db.sessionDao().getPreviousSets("bench")

        assertEquals(listOf("b", "c"), previous.map { it.id })
    }

    private fun exercise(id: String, factor: Double?) = ExerciseEntity(
        id = id, slug = id, category = "strength", equipmentType = "barbell",
        trackingType = TrackingType.WEIGHT_REPS, bodyweightFactor = factor, imageUrls = null, notes = null,
        isCustom = false, isFavorite = false, defaultRestSeconds = null, equipmentId = null,
        source = null, license = null, createdAt = 0, updatedAt = 0,
    )

    private fun set(
        id: String,
        type: SetType,
        weight: Double?,
        reps: Int,
        completed: Boolean,
        index: Int = 0,
    ) = SetEntryEntity(
        id = id, sessionExerciseId = "", setIndex = index, setType = type, weightKg = weight, reps = reps,
        durationSeconds = null, distanceMeters = null, rpe = null, isCompleted = completed,
        completedAt = null, createdAt = 0, updatedAt = 0,
    )

    /** Legt eine Session an; je Übung entsteht ein SessionExercise, die Sätze werden zugeordnet. */
    private suspend fun insertSession(
        id: String,
        endedAt: Long?,
        status: SessionStatus,
        bodyweightKg: Double? = null,
        sets: List<Pair<String, SetEntryEntity>>,
    ) {
        val session = WorkoutSessionEntity(
            id = id, planId = null, title = null, startedAt = 0, endedAt = endedAt, status = status,
            bodyweightKg = bodyweightKg, rating = null, notes = null, totalVolumeKg = null,
            createdAt = 0, updatedAt = 0,
        )
        val exerciseIds = sets.map { it.first }.distinct()
        val sessionExercises = exerciseIds.mapIndexed { i, exerciseId ->
            SessionExerciseEntity(
                id = "$id-$exerciseId", sessionId = id, exerciseId = exerciseId, sortOrder = i,
                supersetGroup = null, notes = null, equipmentSettingsSnapshot = null,
                createdAt = 0, updatedAt = 0,
            )
        }
        val setEntities = sets.map { (exerciseId, set) -> set.copy(sessionExerciseId = "$id-$exerciseId") }
        db.sessionDao().insertSessionWithContent(session, sessionExercises, setEntities)
    }
}
