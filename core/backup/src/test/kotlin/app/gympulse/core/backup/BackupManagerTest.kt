package app.gympulse.core.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gympulse.core.database.GymPulseDatabase
import app.gympulse.core.database.entity.BodyMeasurementEntity
import app.gympulse.core.database.entity.EquipmentEntity
import app.gympulse.core.database.entity.EquipmentSettingEntity
import app.gympulse.core.database.entity.ExerciseEntity
import app.gympulse.core.database.entity.ExerciseMuscleEntity
import app.gympulse.core.database.entity.ExerciseTranslationEntity
import app.gympulse.core.database.entity.PersonalRecordEntity
import app.gympulse.core.database.entity.PlanExerciseEntity
import app.gympulse.core.database.entity.ScheduleOverrideEntity
import app.gympulse.core.database.entity.ScheduleRuleEntity
import app.gympulse.core.database.entity.SessionExerciseEntity
import app.gympulse.core.database.entity.SetEntryEntity
import app.gympulse.core.database.entity.UserProfileEntity
import app.gympulse.core.database.entity.WorkoutPlanEntity
import app.gympulse.core.database.entity.WorkoutSessionEntity
import app.gympulse.core.model.MeasurementSource
import app.gympulse.core.model.MeasurementType
import app.gympulse.core.model.PersonalRecordType
import app.gympulse.core.model.ScheduleOverrideType
import app.gympulse.core.model.SessionStatus
import app.gympulse.core.model.SetType
import app.gympulse.core.model.TrackingType
import app.gympulse.core.settings.AppLanguage
import app.gympulse.core.settings.ThemeMode
import app.gympulse.core.settings.UserSettings
import app.gympulse.core.settings.UserSettingsRepository
import app.gympulse.core.settings.WeekStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupManagerTest {
    private lateinit var context: Context
    private lateinit var source: GymPulseDatabase
    private lateinit var target: GymPulseDatabase
    private val sourceSettings = FakeSettings(
        UserSettings(restTimerSeconds = 150, weekStart = WeekStart.SUNDAY, weeklyGoal = 5, themeMode = ThemeMode.LIGHT),
    )
    private val targetSettings = FakeSettings()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        source = inMemoryDb()
        target = inMemoryDb()
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    @Test
    fun roundTrip_restoresAllUserDataIntoFreshDatabase() = runTest {
        populate(source)
        seedLibrary(target)
        val exporter = manager(source, sourceSettings)
        val importer = manager(target, targetSettings)

        val file = exporter.createBackup(Instant.parse("2026-10-20T08:00:00Z"))
        val decoded = BackupCodec.decode(BackupCodec.encode(file))
        val preview = importer.preview(decoded)
        val result = importer.import(decoded, ConflictStrategy.NEWER_WINS, applySettings = true)

        assertEquals("2026-10-20T08:00:00Z", file.exportedAt)
        assertEquals(BackupFormat.SCHEMA_VERSION, file.schemaVersion)
        assertEquals(1, file.databaseVersion)
        assertFalse(preview.isNewerSchema)
        assertEquals(0, preview.plan(ConflictStrategy.NEWER_WINS).missingReferences)
        assertTrue(result.settingsApplied)
        assertEquals(150, targetSettings.state.value.restTimerSeconds)
        assertEquals(WeekStart.SUNDAY, targetSettings.state.value.weekStart)
        assertEquals(ThemeMode.LIGHT, targetSettings.state.value.themeMode)

        // Ein zweiter Export aus der Zieldatenbank muss inhaltlich identisch sein.
        val again = manager(target, targetSettings).createBackup(Instant.parse("2026-10-20T08:00:00Z"))
        BackupTables.keys.forEach { key ->
            assertEquals(key, file.tables.getValue(key).sortedBy { it.idOrNull() }, again.tables.getValue(key).sortedBy { it.idOrNull() })
        }
        assertEquals(file.settings, again.settings)
    }

    @Test
    fun export_containsSeedOnlyAsReferenceAndUserChangesAsState() = runTest {
        populate(source)

        val file = manager(source, sourceSettings).createBackup()

        assertEquals(listOf("custom-curl"), file.tables.getValue(BackupTables.EXERCISE).map { it.idOrNull() })
        val seedState = file.tables.getValue(BackupTables.EXERCISE_SEED_STATE).single()
        assertEquals("seed-bench", seedState.idOrNull())
        assertEquals(1L, seedState.longOrNull("is_favorite"))
        assertNull("seed rows carry no catalogue data", seedState["slug"])
        assertTrue(file.tables.getValue(BackupTables.EXERCISE_TRANSLATION).all { it.stringOrNull("exercise_id") == "custom-curl" })
        assertTrue(file.tables.getValue(BackupTables.PLAN_EXERCISE).any { it.stringOrNull("exercise_id") == "seed-bench" })
        // Gelöschte Zeilen wandern mit, damit Löschungen beim Import ankommen.
        assertTrue(file.tables.getValue(BackupTables.PLAN_EXERCISE).any { it.longOrNull("deleted_at") != null })
    }

    @Test
    fun import_newerWins_updatesOnlyOlderLocalRows() = runTest {
        seedLibrary(target)
        target.planDao().upsertPlan(plan("p1", "Local old", updatedAt = 100))
        target.planDao().upsertPlan(plan("p2", "Local new", updatedAt = 900))
        val file = backupWith(
            BackupTables.WORKOUT_PLAN,
            planRow("p1", "Backup", updatedAt = 500),
            planRow("p2", "Backup", updatedAt = 500),
            planRow("p3", "Backup", updatedAt = 500),
        )

        val result = manager(target).import(file, ConflictStrategy.NEWER_WINS, applySettings = false)

        assertEquals(1, result.inserted)
        assertEquals(1, result.updated)
        assertEquals(1, result.skipped)
        assertEquals("Backup", planName("p1"))
        assertEquals("Local new", planName("p2"))
        assertEquals("Backup", planName("p3"))
    }

    @Test
    fun import_keepExisting_onlyAddsNewRows() = runTest {
        target.planDao().upsertPlan(plan("p1", "Local", updatedAt = 100))
        val file = backupWith(BackupTables.WORKOUT_PLAN, planRow("p1", "Backup", 500), planRow("p2", "Backup", 500))

        manager(target).import(file, ConflictStrategy.KEEP_EXISTING, applySettings = false)

        assertEquals("Local", planName("p1"))
        assertEquals("Backup", planName("p2"))
    }

    @Test
    fun import_overwrite_replacesNewerLocalRows() = runTest {
        target.planDao().upsertPlan(plan("p1", "Local", updatedAt = 900))
        val file = backupWith(BackupTables.WORKOUT_PLAN, planRow("p1", "Backup", 100))

        manager(target).import(file, ConflictStrategy.OVERWRITE, applySettings = false)

        assertEquals("Backup", planName("p1"))
    }

    @Test
    fun import_deletedRowsStayDeleted() = runTest {
        target.planDao().upsertPlan(plan("p1", "Deleted here", updatedAt = 100, deletedAt = 100))
        target.planDao().upsertPlan(plan("p2", "Alive here", updatedAt = 900))
        val file = backupWith(
            BackupTables.WORKOUT_PLAN,
            planRow("p1", "Alive in backup", updatedAt = 500),
            planRow("p2", "Deleted in backup", updatedAt = 200, deletedAt = 200),
        )

        manager(target).import(file, ConflictStrategy.OVERWRITE, applySettings = false)

        assertNotNull(planDeletedAt("p1"))
        assertEquals("Deleted here", planName("p1"))
        assertEquals(200L, planDeletedAt("p2"))
    }

    @Test
    fun import_doesNotCascadeDeleteChildrenWhenParentIsUpdated() = runTest {
        seedLibrary(target)
        target.planDao().upsertPlan(plan("p1", "Local", updatedAt = 100))
        target.planDao().upsertPlanExercises(listOf(planExercise("pe1", "p1", "seed-bench")))
        val file = backupWith(BackupTables.WORKOUT_PLAN, planRow("p1", "Backup", 500))

        manager(target).import(file, ConflictStrategy.NEWER_WINS, applySettings = false)

        assertEquals("Backup", planName("p1"))
        assertEquals(1, count("plan_exercise"))
    }

    @Test
    fun import_missingReferences_failsWithoutWriting() = runTest {
        val file = BackupFile(
            schemaVersion = 1, exportedAt = "x", appVersion = "test",
            tables = mapOf(
                BackupTables.WORKOUT_PLAN to listOf(planRow("p1", "Backup", 1)),
                BackupTables.PLAN_EXERCISE to listOf(
                    JsonObject(
                        planRow("pe1", "", 1) - "name" + mapOf(
                            "plan_id" to JsonPrimitive("p1"),
                            "exercise_id" to JsonPrimitive("not-on-this-device"),
                            "sort_order" to JsonPrimitive(0),
                            "target_sets" to JsonPrimitive(3),
                        ),
                    ),
                ),
            ),
        )

        val error = expectBackupError { manager(target).import(file, ConflictStrategy.NEWER_WINS, false) }

        assertEquals(BackupException.Reason.MISSING_REFERENCES, error.reason)
        assertEquals(0, count("workout_plan"))
    }

    @Test
    fun import_failingRow_rollsBackWholeImport() = runTest {
        target.planDao().upsertPlan(plan("p1", "Local", updatedAt = 100))
        // Zweite Zeile ohne Pflichtspalte "name": die Datenbank lehnt sie ab.
        val broken = JsonObject(planRow("p3", "x", 500) - "name")
        val file = backupWith(BackupTables.WORKOUT_PLAN, planRow("p2", "Backup", 500), broken, planRow("p1", "Backup", 500))

        val error = expectBackupError { manager(target).import(file, ConflictStrategy.NEWER_WINS, true) }

        assertEquals(BackupException.Reason.WRITE_FAILED, error.reason)
        assertEquals(1, count("workout_plan"))
        assertEquals("Local", planName("p1"))
        assertEquals(UserSettings(), targetSettings.state.value)
    }

    @Test
    fun import_newerSchemaVersion_warnsAndIgnoresUnknownColumnsAndTables() = runTest {
        val text = """
            {"format": "gympulse-backup", "schema_version": 99, "exported_at": "2030-01-01T00:00:00Z",
             "app_version": "9.0", "database_version": 42,
             "tables": {
               "workout_plan": [{"id": "p1", "name": "Future", "description": null, "color": null,
                 "is_archived": false, "created_at": 1, "updated_at": 1, "deleted_at": null,
                 "hologram_color": "teal"}],
               "teleport_log": [{"id": "t1"}]
             }}
        """.trimIndent()
        val importer = manager(target)

        val preview = importer.preview(BackupCodec.decode(text))
        val plan = preview.plan(ConflictStrategy.NEWER_WINS)
        importer.import(preview.file, ConflictStrategy.NEWER_WINS, applySettings = true)

        assertTrue(preview.isNewerSchema)
        assertFalse(preview.hasSettings)
        assertEquals(listOf("teleport_log"), plan.unknownTables)
        assertEquals("Future", planName("p1"))
    }

    @Test
    fun import_olderFileWithoutOptionalColumns_usesDefaults() = runTest {
        val text = """
            {"format": "gympulse-backup", "schema_version": 1, "exported_at": "2026-01-01T00:00:00Z",
             "app_version": "0.0.1",
             "tables": {"workout_plan": [{"id": "p1", "name": "Old", "is_archived": 0,
               "created_at": 1, "updated_at": 1}]}}
        """.trimIndent()

        manager(target).import(BackupCodec.decode(text), ConflictStrategy.NEWER_WINS, applySettings = false)

        assertEquals("Old", planName("p1"))
        assertNull(planDeletedAt("p1"))
    }

    @Test
    fun import_profileMergesIntoExistingProfile() = runTest {
        target.bodyDao().upsertProfile(profile("local", "Local", updatedAt = 100))
        val file = backupWith(
            BackupTables.USER_PROFILE,
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive("other-device"), "name" to JsonPrimitive("Imported"),
                    "birth_year" to JsonPrimitive(1990), "height_cm" to JsonPrimitive(180.5),
                    "sex" to JsonNull,
                    "created_at" to JsonPrimitive(1), "updated_at" to JsonPrimitive(500),
                ),
            ),
        )

        manager(target).import(file, ConflictStrategy.NEWER_WINS, applySettings = false)

        assertEquals(1, count("user_profile"))
        val db = target.openHelper.readableDatabase
        db.query("SELECT id, name, height_cm FROM user_profile").use { c ->
            c.moveToFirst()
            assertEquals("local", c.getString(0))
            assertEquals("Imported", c.getString(1))
            assertEquals(180.5, c.getDouble(2), 0.0001)
        }
    }

    // --- Hilfen ---

    private fun inMemoryDb() = Room.inMemoryDatabaseBuilder(context, GymPulseDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private fun manager(db: GymPulseDatabase, settings: FakeSettings = targetSettings) =
        BackupManager(RoomBackupStore(db), settings, context)

    private suspend fun expectBackupError(block: suspend () -> Unit): BackupException {
        try {
            block()
        } catch (e: BackupException) {
            return e
        }
        fail("Expected BackupException")
        throw AssertionError()
    }

    private fun count(table: String): Int =
        target.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private fun planName(id: String): String? = planColumn(id, "name") { getString(0) }

    private fun planDeletedAt(id: String): Long? = planColumn(id, "deleted_at") { if (isNull(0)) null else getLong(0) }

    private fun <T> planColumn(id: String, column: String, read: android.database.Cursor.() -> T): T? =
        target.openHelper.readableDatabase.query("SELECT $column FROM workout_plan WHERE id = ?", arrayOf(id)).use {
            if (it.moveToFirst()) it.read() else null
        }

    private fun backupWith(key: String, vararg rows: JsonObject) = BackupFile(
        schemaVersion = BackupFormat.SCHEMA_VERSION,
        exportedAt = "2026-10-20T00:00:00Z",
        appVersion = "test",
        tables = mapOf(key to rows.toList()),
    )

    private fun planRow(id: String, name: String, updatedAt: Long, deletedAt: Long? = null) = JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "name" to JsonPrimitive(name),
            "description" to JsonNull,
            "color" to JsonNull,
            "is_archived" to JsonPrimitive(0),
            "created_at" to JsonPrimitive(1),
            "updated_at" to JsonPrimitive(updatedAt),
            "deleted_at" to (deletedAt?.let { JsonPrimitive(it) } ?: JsonNull),
        ),
    )

    /** Seed-Übungen, wie sie der SeedImporter auf jedem Gerät mit gleicher ID anlegt. */
    private suspend fun seedLibrary(db: GymPulseDatabase) {
        db.exerciseDao().upsertExercises(listOf(exercise("seed-bench", custom = false), exercise("seed-squat", custom = false)))
    }

    private suspend fun populate(db: GymPulseDatabase) {
        seedLibrary(db)
        db.bodyDao().upsertProfile(profile("profile", "Jörg", updatedAt = 10))
        db.bodyDao().upsertMeasurement(
            BodyMeasurementEntity(
                id = "m1", measuredAt = 1_000, type = MeasurementType.WEIGHT, value = 82.4,
                source = MeasurementSource.MANUAL, createdAt = 1_000, updatedAt = 1_000,
            ),
        )
        db.equipmentDao().upsert(
            EquipmentEntity(id = "eq1", name = "Leg press", location = "Gym A", notes = null, templateKey = null, createdAt = 1, updatedAt = 1),
        )
        db.equipmentDao().upsertSettings(
            listOf(EquipmentSettingEntity(id = "es1", equipmentId = "eq1", key = "Seat", value = "4", sortOrder = 0, createdAt = 1, updatedAt = 1)),
        )
        // Nutzeränderung an einer Seed-Übung.
        db.exerciseDao().upsertExercises(listOf(exercise("seed-bench", custom = false).copy(isFavorite = true, equipmentId = "eq1", updatedAt = 50)))
        db.exerciseDao().upsertFull(
            listOf(exercise("custom-curl", custom = true)),
            listOf(
                ExerciseTranslationEntity(
                    id = "tr1", exerciseId = "custom-curl", locale = "de", name = "Meine Curls",
                    instructions = "Langsam\nKontrolliert", createdAt = 1, updatedAt = 1,
                ),
            ),
            listOf(ExerciseMuscleEntity(id = "mu1", exerciseId = "custom-curl", muscle = "biceps", isPrimary = true, createdAt = 1, updatedAt = 1)),
        )
        db.planDao().upsertPlan(plan("p1", "Push", updatedAt = 20))
        db.planDao().upsertPlanExercises(
            listOf(
                planExercise("pe1", "p1", "seed-bench"),
                planExercise("pe2", "p1", "custom-curl"),
                planExercise("pe3", "p1", "seed-squat").copy(deletedAt = 30, updatedAt = 30),
            ),
        )
        db.scheduleDao().upsertRule(
            ScheduleRuleEntity(id = "r1", planId = "p1", weekday = 1, validFrom = "2026-01-05", validUntil = null, createdAt = 1, updatedAt = 1),
        )
        db.scheduleDao().upsertOverride(
            ScheduleOverrideEntity(id = "o1", date = "2026-02-02", planId = null, type = ScheduleOverrideType.SKIPPED, createdAt = 1, updatedAt = 1),
        )
        db.sessionDao().insertSessionWithContent(
            WorkoutSessionEntity(
                id = "s1", planId = "p1", title = "Push", startedAt = 2_000, endedAt = 5_000,
                status = SessionStatus.COMPLETED, bodyweightKg = 82.4, rating = 4, notes = "Gut",
                totalVolumeKg = 1_000.0, createdAt = 2_000, updatedAt = 5_000,
            ),
            listOf(
                SessionExerciseEntity(
                    id = "se1", sessionId = "s1", exerciseId = "seed-bench", sortOrder = 0, supersetGroup = null,
                    notes = null, equipmentSettingsSnapshot = """{"Seat":"4"}""", createdAt = 2_000, updatedAt = 2_000,
                ),
            ),
            listOf(
                SetEntryEntity(
                    id = "set1", sessionExerciseId = "se1", setIndex = 0, setType = SetType.WORKING, weightKg = 100.0,
                    reps = 5, durationSeconds = null, distanceMeters = null, rpe = 8.5, isCompleted = true,
                    completedAt = 3_000, createdAt = 2_000, updatedAt = 3_000,
                ),
            ),
        )
        db.personalRecordDao().upsert(
            listOf(
                PersonalRecordEntity(
                    id = "pr1", exerciseId = "seed-bench", type = PersonalRecordType.MAX_WEIGHT, value = 100.0,
                    setEntryId = "set1", achievedAt = 3_000, createdAt = 3_000, updatedAt = 3_000,
                ),
            ),
        )
    }

    private fun exercise(id: String, custom: Boolean) = ExerciseEntity(
        id = id, slug = if (custom) null else id, category = "strength", equipmentType = "barbell",
        trackingType = TrackingType.WEIGHT_REPS, bodyweightFactor = null, imageUrls = null, notes = null,
        isCustom = custom, isFavorite = false, defaultRestSeconds = null, equipmentId = null,
        source = null, license = null, createdAt = 0, updatedAt = 0,
    )

    private fun plan(id: String, name: String, updatedAt: Long, deletedAt: Long? = null) = WorkoutPlanEntity(
        id = id, name = name, description = null, color = null, isArchived = false,
        createdAt = 1, updatedAt = updatedAt, deletedAt = deletedAt,
    )

    private fun planExercise(id: String, planId: String, exerciseId: String) = PlanExerciseEntity(
        id = id, planId = planId, exerciseId = exerciseId, sortOrder = 0, supersetGroup = null, targetSets = 3,
        targetRepsMin = 8, targetRepsMax = 12, targetWeightKg = 60.0, restSeconds = 90, createdAt = 1, updatedAt = 1,
    )

    private fun profile(id: String, name: String, updatedAt: Long) = UserProfileEntity(
        id = id, name = name, birthYear = null, heightCm = 178.0, sex = null, createdAt = 1, updatedAt = updatedAt,
    )
}

private class FakeSettings(initial: UserSettings = UserSettings()) : UserSettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: StateFlow<UserSettings> = state
    override val restTimerSeconds = state.map { it.restTimerSeconds }
    override val weekStart = state.map { it.weekStart }
    override val weeklyGoal = state.map { it.weeklyGoal }
    override suspend fun setRestTimerSeconds(seconds: Int) = state.update { copy(restTimerSeconds = seconds) }
    override suspend fun setWeekStart(weekStart: WeekStart) = state.update { copy(weekStart = weekStart) }
    override suspend fun setWeeklyGoal(sessionsPerWeek: Int) = state.update { copy(weeklyGoal = sessionsPerWeek) }
    override suspend fun setThemeMode(mode: ThemeMode) = state.update { copy(themeMode = mode) }
    override suspend fun setDynamicColor(enabled: Boolean) = state.update { copy(dynamicColor = enabled) }
    override suspend fun setAccentColor(accent: app.gympulse.core.designsystem.theme.AccentColor) = state.update { copy(accentColor = accent) }
    override suspend fun setLanguage(language: AppLanguage) = state.update { copy(language = language) }
    override suspend fun setOnboardingCompleted(completed: Boolean) = state.update { copy(onboardingCompleted = completed) }

    private fun MutableStateFlow<UserSettings>.update(change: UserSettings.() -> UserSettings) {
        value = value.change()
    }
}
