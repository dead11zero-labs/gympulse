package app.gympulse.core.backup

/** Fremdschlüssel einer Zeile: [column] verweist auf eine ID in Tabelle [parentTable]. */
data class TableReference(val column: String, val parentTable: String)

/**
 * Ein Abschnitt im Backup.
 *
 * @param key Schlüssel in [BackupFile.tables].
 * @param table Zieltabelle in der Datenbank.
 * @param exportSql Abfrage für den Export.
 * @param insertAllowed false: nur bestehende Zeilen werden aktualisiert (Seed-Übungen).
 * @param singleton Tabelle hat höchstens eine aktive Zeile (Profil); eine importierte Zeile
 *   übernimmt dann die ID der vorhandenen, statt eine zweite anzulegen.
 * @param importColumns Spalten, die der Import schreiben darf; null = alle bekannten.
 */
data class TableSpec(
    val key: String,
    val table: String,
    val exportSql: String,
    val insertAllowed: Boolean = true,
    val singleton: Boolean = false,
    val importColumns: Set<String>? = null,
    val references: List<TableReference> = emptyList(),
)

/**
 * Was ins Backup gehört, in Import-Reihenfolge (Eltern vor Kindern, damit Fremdschlüssel greifen).
 *
 * Seed-Übungen werden nicht kopiert, sondern nur per ID referenziert: Ihre IDs sind auf jedem Gerät
 * gleich (namensbasierte UUIDs). Nur die Nutzeränderungen daran (Favorit, Notiz, Pause, Gerät)
 * landen in [EXERCISE_SEED_STATE]. Fortschrittsfotos fehlen bewusst, weil sie auf lokale Dateien
 * zeigen, die ein Backup nicht mitnehmen kann.
 */
object BackupTables {
    const val USER_PROFILE = "user_profile"
    const val BODY_MEASUREMENT = "body_measurement"
    const val EQUIPMENT = "equipment"
    const val EQUIPMENT_SETTING = "equipment_setting"
    const val EXERCISE = "exercise"
    const val EXERCISE_SEED_STATE = "exercise_seed_state"
    const val EXERCISE_TRANSLATION = "exercise_translation"
    const val EXERCISE_MUSCLE = "exercise_muscle"
    const val WORKOUT_PLAN = "workout_plan"
    const val PLAN_EXERCISE = "plan_exercise"
    const val SCHEDULE_RULE = "schedule_rule"
    const val SCHEDULE_OVERRIDE = "schedule_override"
    const val WORKOUT_SESSION = "workout_session"
    const val SESSION_EXERCISE = "session_exercise"
    const val SET_ENTRY = "set_entry"
    const val PERSONAL_RECORD = "personal_record"

    private const val CUSTOM_EXERCISE_IDS = "SELECT id FROM exercise WHERE is_custom = 1"

    val all: List<TableSpec> = listOf(
        TableSpec(USER_PROFILE, USER_PROFILE, "SELECT * FROM user_profile", singleton = true),
        TableSpec(BODY_MEASUREMENT, BODY_MEASUREMENT, "SELECT * FROM body_measurement"),
        TableSpec(EQUIPMENT, EQUIPMENT, "SELECT * FROM equipment"),
        TableSpec(
            EQUIPMENT_SETTING, EQUIPMENT_SETTING, "SELECT * FROM equipment_setting",
            references = listOf(TableReference("equipment_id", EQUIPMENT)),
        ),
        TableSpec(
            EXERCISE, EXERCISE, "SELECT * FROM exercise WHERE is_custom = 1",
            references = listOf(TableReference("equipment_id", EQUIPMENT)),
        ),
        TableSpec(
            key = EXERCISE_SEED_STATE,
            table = EXERCISE,
            exportSql = """
                SELECT id, is_favorite, notes, default_rest_s, equipment_id, updated_at, deleted_at
                FROM exercise
                WHERE is_custom = 0 AND (is_favorite = 1 OR notes IS NOT NULL OR default_rest_s IS NOT NULL
                    OR equipment_id IS NOT NULL OR deleted_at IS NOT NULL)
            """.trimIndent(),
            insertAllowed = false,
            importColumns = setOf("is_favorite", "notes", "default_rest_s", "equipment_id", "updated_at", "deleted_at"),
            references = listOf(TableReference("equipment_id", EQUIPMENT)),
        ),
        TableSpec(
            EXERCISE_TRANSLATION, EXERCISE_TRANSLATION,
            "SELECT * FROM exercise_translation WHERE exercise_id IN ($CUSTOM_EXERCISE_IDS)",
            references = listOf(TableReference("exercise_id", EXERCISE)),
        ),
        TableSpec(
            EXERCISE_MUSCLE, EXERCISE_MUSCLE,
            "SELECT * FROM exercise_muscle WHERE exercise_id IN ($CUSTOM_EXERCISE_IDS)",
            references = listOf(TableReference("exercise_id", EXERCISE)),
        ),
        TableSpec(WORKOUT_PLAN, WORKOUT_PLAN, "SELECT * FROM workout_plan"),
        TableSpec(
            PLAN_EXERCISE, PLAN_EXERCISE, "SELECT * FROM plan_exercise",
            references = listOf(TableReference("plan_id", WORKOUT_PLAN), TableReference("exercise_id", EXERCISE)),
        ),
        TableSpec(
            SCHEDULE_RULE, SCHEDULE_RULE, "SELECT * FROM schedule_rule",
            references = listOf(TableReference("plan_id", WORKOUT_PLAN)),
        ),
        TableSpec(
            SCHEDULE_OVERRIDE, SCHEDULE_OVERRIDE, "SELECT * FROM schedule_override",
            references = listOf(TableReference("plan_id", WORKOUT_PLAN)),
        ),
        TableSpec(
            WORKOUT_SESSION, WORKOUT_SESSION, "SELECT * FROM workout_session",
            references = listOf(TableReference("plan_id", WORKOUT_PLAN)),
        ),
        TableSpec(
            SESSION_EXERCISE, SESSION_EXERCISE, "SELECT * FROM session_exercise",
            references = listOf(
                TableReference("session_id", WORKOUT_SESSION),
                TableReference("exercise_id", EXERCISE),
            ),
        ),
        TableSpec(
            SET_ENTRY, SET_ENTRY, "SELECT * FROM set_entry",
            references = listOf(TableReference("session_exercise_id", SESSION_EXERCISE)),
        ),
        TableSpec(
            PERSONAL_RECORD, PERSONAL_RECORD, "SELECT * FROM personal_record",
            references = listOf(TableReference("exercise_id", EXERCISE)),
        ),
    )

    val keys: Set<String> = all.mapTo(LinkedHashSet()) { it.key }

    /** Zieltabellen, deren lokaler Stand für die Konfliktauflösung gebraucht wird. */
    val targetTables: Set<String> = all.mapTo(LinkedHashSet()) { it.table }
}
