package app.gympulse.core.featuregate

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Regel: Remote `false` schaltet ab (Kill-Switch); sonst schaltet ein Premium-Feature mit
 * Entitlement `false` ab; ansonsten gilt der lokale Standardwert.
 */
class DefaultFeatureGate @Inject constructor(
    private val localDefaults: LocalFeatureDefaults,
    private val remoteConfig: RemoteFeatureConfig,
    private val entitlements: EntitlementProvider,
) : FeatureGate {

    override fun isEnabled(feature: Feature): Boolean = when {
        remoteConfig.isEnabled(feature) == false -> false
        feature.premium && entitlements.hasEntitlement(feature) == false -> false
        else -> localDefaults.isEnabledByDefault(feature)
    }

    // Die Quellen sind derzeit synchron; reaktive Quellen können hier später kombiniert werden.
    override fun observe(feature: Feature): Flow<Boolean> = flow { emit(isEnabled(feature)) }
}
