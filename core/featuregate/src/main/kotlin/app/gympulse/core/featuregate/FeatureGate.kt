package app.gympulse.core.featuregate

import kotlinx.coroutines.flow.Flow

/** Zentrale Abfrage, ob ein [Feature] aktuell nutzbar ist. */
interface FeatureGate {
    fun isEnabled(feature: Feature): Boolean

    fun observe(feature: Feature): Flow<Boolean>
}
