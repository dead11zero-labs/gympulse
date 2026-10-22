package app.gympulse.core.wearprotocol

/**
 * Pfade der Wearable Data Layer API.
 *
 * - [ACTIVE_WORKOUT]: Telefon → Uhr, [WorkoutSnapshot] der laufenden Session (persistentes DataItem).
 * - [PLANS_SUMMARY]: Telefon → Uhr, [PlanSummaryList] für die Planauswahl.
 * - `/cmd/<uuid>`: Uhr → Telefon, ein [WearCommand] als urgent DataItem; das Telefon löscht es nach der Verarbeitung.
 */
object WearPaths {
    const val ACTIVE_WORKOUT = "/workout/active"
    const val PLANS_SUMMARY = "/plans/summary"

    /** Präfix aller Kommando-Items, z. B. für `DataClient.getDataItems(uri, FILTER_PREFIX)`. */
    const val COMMAND_PREFIX = "/cmd/"

    private val uuidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /** Pfad für das Kommando mit [commandId]; wirft bei einer ID, die keine UUID ist. */
    fun command(commandId: String): String {
        require(isUuid(commandId)) { "commandId must be a UUID: $commandId" }
        return COMMAND_PREFIX + commandId.lowercase()
    }

    /** UUID aus einem Kommando-Pfad oder null, wenn [path] kein gültiger Kommando-Pfad ist. */
    fun commandIdOf(path: String): String? {
        if (!path.startsWith(COMMAND_PREFIX)) return null
        val id = path.removePrefix(COMMAND_PREFIX)
        return if (isUuid(id)) id.lowercase() else null
    }

    fun isCommandPath(path: String): Boolean = commandIdOf(path) != null

    internal fun isUuid(value: String): Boolean = uuidPattern.matches(value)
}
