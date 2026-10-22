package app.gympulse.core.workout.timer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import java.time.Instant

/** Persists the running rest period, so it survives process death. */
interface RestTimerStore {
    suspend fun read(): RestTimerState?

    /** Stores [state], or clears the stored period with null. */
    suspend fun write(state: RestTimerState?)
}

/** [RestTimerStore] in its own Preferences DataStore file (only `endsAt` and the total length). */
class DataStoreRestTimerStore(
    private val dataStore: DataStore<Preferences>,
) : RestTimerStore {

    override suspend fun read(): RestTimerState? {
        val prefs = dataStore.data.first()
        val endsAt = prefs[KEY_ENDS_AT] ?: return null
        val total = prefs[KEY_TOTAL_SECONDS] ?: return null
        return RestTimerState(endsAt = Instant.ofEpochMilli(endsAt), totalSeconds = total)
    }

    override suspend fun write(state: RestTimerState?) {
        dataStore.edit { prefs ->
            if (state == null) {
                prefs.remove(KEY_ENDS_AT)
                prefs.remove(KEY_TOTAL_SECONDS)
            } else {
                prefs[KEY_ENDS_AT] = state.endsAt.toEpochMilli()
                prefs[KEY_TOTAL_SECONDS] = state.totalSeconds
            }
        }
    }

    private companion object {
        val KEY_ENDS_AT = longPreferencesKey("rest_ends_at_epoch_millis")
        val KEY_TOTAL_SECONDS = intPreferencesKey("rest_total_seconds")
    }
}
