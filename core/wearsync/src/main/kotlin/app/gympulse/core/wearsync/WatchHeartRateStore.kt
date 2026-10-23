package app.gympulse.core.wearsync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Puls einer Session, wie die Uhr ihn nach dem Workout gemeldet hat (Schläge pro Minute). */
data class WatchHeartRate(
    val sessionId: String,
    val averageBpm: Int,
    val maxBpm: Int,
)

/**
 * Hält die von der Uhr gemeldeten Ø/Max-Pulswerte je Session. Die Uhr sendet sie erst nach dem
 * Workout; die Zusammenfassung am Telefon kann sie über [observe] abfragen.
 */
interface WatchHeartRateStore {
    /** Speichert [value]; ein zweiter Wert für dieselbe Session ersetzt den ersten. */
    suspend fun record(value: WatchHeartRate)

    /** Wert für [sessionId] oder null, solange die Uhr keinen gemeldet hat. */
    fun observe(sessionId: String): Flow<WatchHeartRate?>

    companion object {
        /** Ältere Einträge fallen heraus. */
        const val MAX_REMEMBERED = 50
    }
}

/** [WatchHeartRateStore] in einer eigenen Preferences-DataStore-Datei, eine Zeile `id|ø|max` je Session. */
class DataStoreWatchHeartRateStore(
    private val dataStore: DataStore<Preferences>,
) : WatchHeartRateStore {
    override suspend fun record(value: WatchHeartRate) {
        dataStore.edit { prefs ->
            val others = decode(prefs[KEY_VALUES]).filterNot { it.sessionId == value.sessionId }
            prefs[KEY_VALUES] = encode((others + value).takeLast(WatchHeartRateStore.MAX_REMEMBERED))
        }
    }

    override fun observe(sessionId: String): Flow<WatchHeartRate?> =
        dataStore.data.map { prefs -> decode(prefs[KEY_VALUES]).firstOrNull { it.sessionId == sessionId } }
            .distinctUntilChanged()

    private fun encode(values: List<WatchHeartRate>): String =
        values.joinToString("\n") { "${it.sessionId}|${it.averageBpm}|${it.maxBpm}" }

    private fun decode(raw: String?): List<WatchHeartRate> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('|')
            val average = parts.getOrNull(1)?.toIntOrNull()
            val max = parts.getOrNull(2)?.toIntOrNull()
            if (parts.size == 3 && parts[0].isNotEmpty() && average != null && max != null) {
                WatchHeartRate(parts[0], average, max)
            } else {
                null
            }
        }.toList()
    }

    private companion object {
        val KEY_VALUES = stringPreferencesKey("watch_heart_rates")
    }
}
