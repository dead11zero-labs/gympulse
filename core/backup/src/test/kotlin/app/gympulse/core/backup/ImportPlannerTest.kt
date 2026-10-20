package app.gympulse.core.backup

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportPlannerTest {

    // --- Konfliktregel ---

    @Test
    fun resolve_newRowIsInserted() {
        assertEquals(RowAction.INSERT, resolve(incoming = meta(5), local = null))
    }

    @Test
    fun resolve_newRowIsSkippedWhenInsertNotAllowed() {
        assertEquals(RowAction.SKIP, resolve(incoming = meta(5), local = null, insertAllowed = false))
    }

    @Test
    fun resolve_newerWins_takesNewerIncoming() {
        assertEquals(RowAction.UPDATE, resolve(incoming = meta(10), local = meta(5)))
    }

    @Test
    fun resolve_newerWins_keepsNewerOrEqualLocal() {
        assertEquals(RowAction.SKIP, resolve(incoming = meta(5), local = meta(10)))
        assertEquals(RowAction.SKIP, resolve(incoming = meta(5), local = meta(5)))
    }

    @Test
    fun resolve_keepExisting_neverTouchesExistingRows() {
        assertEquals(RowAction.SKIP, resolve(meta(10), meta(5), ConflictStrategy.KEEP_EXISTING))
        assertEquals(RowAction.SKIP, resolve(meta(10, deleted = 10), meta(5), ConflictStrategy.KEEP_EXISTING))
        assertEquals(RowAction.INSERT, resolve(meta(10), null, ConflictStrategy.KEEP_EXISTING))
    }

    @Test
    fun resolve_overwrite_replacesEvenOlderIncoming() {
        assertEquals(RowAction.UPDATE, resolve(meta(1), meta(10), ConflictStrategy.OVERWRITE))
    }

    @Test
    fun resolve_locallyDeletedRowStaysDeleted() {
        ConflictStrategy.entries.forEach { strategy ->
            assertEquals(strategy.name, RowAction.SKIP, resolve(meta(99), meta(5, deleted = 5), strategy))
        }
    }

    @Test
    fun resolve_incomingDeletionWinsEvenIfOlder() {
        assertEquals(RowAction.UPDATE, resolve(meta(3, deleted = 3), meta(10)))
        assertEquals(RowAction.UPDATE, resolve(meta(3, deleted = 3), meta(10), ConflictStrategy.OVERWRITE))
    }

    // --- Planer ---

    @Test
    fun plan_countsActionsPerTable() {
        val file = backup(
            BackupTables.WORKOUT_PLAN to listOf(row("p1", 10), row("p2", 1), row("p3", 1)),
        )
        val local = mapOf(BackupTables.WORKOUT_PLAN to mapOf("p1" to meta(5), "p2" to meta(5)))

        val plan = ImportPlanner.plan(file, local, ConflictStrategy.NEWER_WINS)
        val plans = plan.table(BackupTables.WORKOUT_PLAN)

        assertEquals(3, plans.total)
        assertEquals(1, plans.inserts)
        assertEquals(1, plans.updates)
        assertEquals(1, plans.skipped)
        assertEquals(2, plan.changes)
    }

    @Test
    fun plan_detectsReferencesToUnknownExercises() {
        val file = backup(
            BackupTables.WORKOUT_PLAN to listOf(row("p1", 1)),
            BackupTables.PLAN_EXERCISE to listOf(
                row("pe1", 1, "plan_id" to "p1", "exercise_id" to "seed-bench"),
                row("pe2", 1, "plan_id" to "p1", "exercise_id" to "custom-1"),
                row("pe3", 1, "plan_id" to "p1", "exercise_id" to "gone"),
            ),
            BackupTables.EXERCISE to listOf(row("custom-1", 1)),
        )
        val local = mapOf(BackupTables.EXERCISE to mapOf("seed-bench" to meta(0)))

        val plan = ImportPlanner.plan(file, local, ConflictStrategy.NEWER_WINS)

        assertEquals(1, plan.table(BackupTables.PLAN_EXERCISE).missingReferences)
        assertEquals(1, plan.missingReferences)
    }

    @Test
    fun plan_skippedRowsDoNotCountAsMissingReferences() {
        val file = backup(BackupTables.PLAN_EXERCISE to listOf(row("pe1", 1, "plan_id" to "nope")))
        val local = mapOf(BackupTables.PLAN_EXERCISE to mapOf("pe1" to meta(5)))

        val plan = ImportPlanner.plan(file, local, ConflictStrategy.NEWER_WINS)

        assertEquals(0, plan.missingReferences)
    }

    @Test
    fun plan_nullReferenceIsFine() {
        val file = backup(
            BackupTables.WORKOUT_SESSION to listOf(JsonObject(mapOf("id" to JsonPrimitive("s1"), "plan_id" to JsonNull))),
        )

        val plan = ImportPlanner.plan(file, emptyMap(), ConflictStrategy.NEWER_WINS)

        assertEquals(0, plan.missingReferences)
        assertEquals(1, plan.inserts)
    }

    @Test
    fun plan_seedStateOnlyUpdatesExistingExercises() {
        val file = backup(BackupTables.EXERCISE_SEED_STATE to listOf(row("seed-a", 10), row("seed-unknown", 10)))
        val local = mapOf(BackupTables.EXERCISE to mapOf("seed-a" to meta(5)))

        val seed = ImportPlanner.plan(file, local, ConflictStrategy.NEWER_WINS).table(BackupTables.EXERCISE_SEED_STATE)

        assertEquals(listOf(RowAction.UPDATE, RowAction.SKIP), seed.rows.map { it.action })
    }

    @Test
    fun plan_profileTakesOverLocalProfileId() {
        val file = backup(BackupTables.USER_PROFILE to listOf(row("imported", 10)))
        val local = mapOf(BackupTables.USER_PROFILE to mapOf("old-deleted" to meta(1, deleted = 1), "local" to meta(5)))

        val profile = ImportPlanner.plan(file, local, ConflictStrategy.NEWER_WINS).table(BackupTables.USER_PROFILE)

        assertEquals("local", profile.rows.single().id)
        assertEquals(RowAction.UPDATE, profile.rows.single().action)
        assertEquals("local", profile.rows.single().row.idOrNull())
    }

    @Test
    fun plan_duplicateIdsKeepNewestRow() {
        val file = backup(BackupTables.WORKOUT_PLAN to listOf(row("p1", 1, "name" to "old"), row("p1", 9, "name" to "new")))

        val plans = ImportPlanner.plan(file, emptyMap(), ConflictStrategy.NEWER_WINS).table(BackupTables.WORKOUT_PLAN)

        assertEquals(1, plans.total)
        assertEquals("new", plans.rows.single().row.stringOrNull("name"))
    }

    @Test
    fun plan_reportsUnknownNonEmptyTables() {
        val file = backup("future_table" to listOf(row("x", 1)), "empty_future" to emptyList())

        val plan = ImportPlanner.plan(file, emptyMap(), ConflictStrategy.NEWER_WINS)

        assertEquals(listOf("future_table"), plan.unknownTables)
        assertEquals(0, plan.changes)
    }

    private fun resolve(
        incoming: RowMeta,
        local: RowMeta?,
        strategy: ConflictStrategy = ConflictStrategy.NEWER_WINS,
        insertAllowed: Boolean = true,
    ) = ConflictResolver.resolve(incoming, local, strategy, insertAllowed)

    private fun meta(updatedAt: Long, deleted: Long? = null) = RowMeta(updatedAt, deleted)

    private fun row(id: String, updatedAt: Long, vararg values: Pair<String, String>) = JsonObject(
        mapOf("id" to JsonPrimitive(id), "updated_at" to JsonPrimitive(updatedAt)) +
            values.map { (k, v) -> k to JsonPrimitive(v) },
    )

    private fun backup(vararg tables: Pair<String, List<JsonObject>>) = BackupFile(
        schemaVersion = 1,
        exportedAt = "2026-10-20T00:00:00Z",
        appVersion = "test",
        tables = tables.toMap(),
    )

    private fun ImportPlan.table(key: String) = tables.first { it.spec.key == key }
}
