package app.gympulse.core.featuregate

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Wahl des Nutzers für Features mit [Feature.userToggleable]. `null` heißt: keine Wahl getroffen,
 * es gilt der Standardwert.
 */
interface UserFeatureToggles {
    fun current(feature: Feature): Boolean?

    fun observe(feature: Feature): Flow<Boolean?>

    fun setEnabled(feature: Feature, enabled: Boolean)
}

/**
 * Speichert die Wahl in SharedPreferences, damit [FeatureGate.isEnabled] synchron bleibt.
 * Die Werte werden einmal geladen und danach im Speicher gehalten.
 */
@Singleton
class SharedPreferencesUserFeatureToggles @Inject constructor(
    @ApplicationContext context: Context,
) : UserFeatureToggles {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val state: MutableStateFlow<Map<Feature, Boolean>> by lazy {
        MutableStateFlow(
            Feature.entries
                .filter { it.userToggleable && prefs.contains(key(it)) }
                .associateWith { prefs.getBoolean(key(it), it.enabledByDefault) },
        )
    }

    override fun current(feature: Feature): Boolean? = state.value[feature]

    override fun observe(feature: Feature): Flow<Boolean?> = state.map { it[feature] }.distinctUntilChanged()

    override fun setEnabled(feature: Feature, enabled: Boolean) {
        if (!feature.userToggleable) return
        prefs.edit { putBoolean(key(feature), enabled) }
        state.value = state.value + (feature to enabled)
    }

    private fun key(feature: Feature) = "feature_${feature.name.lowercase()}"

    private companion object {
        const val PREFS_NAME = "gympulse_feature_toggles"
    }
}
