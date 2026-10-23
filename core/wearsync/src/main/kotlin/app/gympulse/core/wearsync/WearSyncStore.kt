package app.gympulse.core.wearsync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.gympulse.core.wearprotocol.CommandAck
import app.gympulse.core.wearprotocol.WearProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer

/**
 * Persistenter Zustand der Wear-Anbindung am Telefon: verarbeitete Kommandos (für die Idempotenz
 * und als Quittungen im nächsten Snapshot) und die zuletzt vergebene Snapshot-Version. Beides
 * muss einen Prozess-Neustart überleben, den der WearableListenerService jederzeit auslösen kann.
 */
interface WearSyncStore {
    /** Verarbeitete Kommandos, älteste zuerst, höchstens [MAX_REMEMBERED] Einträge. */
    val processed: Flow<List<CommandAck>>

    /** Merkt sich [ack]; ein schon bekanntes Kommando wird nicht doppelt eingetragen. */
    suspend fun remember(ack: CommandAck)

    /**
     * Vergibt die nächste Version: größer als jede bisher vergebene und mindestens [nowMillis],
     * damit sie auch nach dem Löschen der App-Daten über dem letzten Stand der Uhr liegt.
     */
    suspend fun nextVersion(nowMillis: Long): Long

    companion object {
        const val MAX_REMEMBERED = 512
    }
}

/** [WearSyncStore] in einer eigenen Preferences-DataStore-Datei. */
class DataStoreWearSyncStore(
    private val dataStore: DataStore<Preferences>,
) : WearSyncStore {
    private val serializer = ListSerializer(CommandAck.serializer())

    override val processed: Flow<List<CommandAck>> =
        dataStore.data.map { decode(it[KEY_PROCESSED]) }.distinctUntilChanged()

    override suspend fun remember(ack: CommandAck) {
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY_PROCESSED])
            if (current.none { it.commandId == ack.commandId }) {
                prefs[KEY_PROCESSED] = encode((current + ack).takeLast(WearSyncStore.MAX_REMEMBERED))
            }
        }
    }

    override suspend fun nextVersion(nowMillis: Long): Long {
        var next = 0L
        dataStore.edit { prefs ->
            next = maxOf((prefs[KEY_VERSION] ?: 0L) + 1, nowMillis)
            prefs[KEY_VERSION] = next
        }
        return next
    }

    private fun encode(acks: List<CommandAck>): String = WearProtocol.json.encodeToString(serializer, acks)

    private fun decode(raw: String?): List<CommandAck> {
        if (raw.isNullOrEmpty()) return emptyList()
        return try {
            WearProtocol.json.decodeFromString(serializer, raw)
        } catch (e: SerializationException) {
            emptyList()
        } catch (e: IllegalArgumentException) {
            emptyList()
        }
    }

    private companion object {
        val KEY_PROCESSED = stringPreferencesKey("processed_commands")
        val KEY_VERSION = longPreferencesKey("snapshot_version")
    }
}
