package app.gympulse.core.featuregate

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Regel: Remote `false` schaltet ab (Kill-Switch); sonst schaltet ein Premium-Feature mit
 * Entitlement `false` ab; bei vom Nutzer schaltbaren Features gilt danach dessen Wahl;
 * ansonsten gilt der lokale Standardwert.
 */
class DefaultFeatureGate @Inject constructor(
    private val localDefaults: LocalFeatureDefaults,
    private val remoteConfig: RemoteFeatureConfig,
    private val entitlements: EntitlementProvider,
    private val userToggles: UserFeatureToggles,
) : FeatureGate {

    override fun isEnabled(feature: Feature): Boolean = resolve(feature, userToggles.current(feature))

    // Remote-Config und Entitlements sind derzeit synchron; reaktiv ist nur die Nutzerwahl.
    override fun observe(feature: Feature): Flow<Boolean> =
        userToggles.observe(feature).map { resolve(feature, it) }.distinctUntilChanged()

    private fun resolve(feature: Feature, userChoice: Boolean?): Boolean = when {
        remoteConfig.isEnabled(feature) == false -> false
        feature.premium && entitlements.hasEntitlement(feature) == false -> false
        feature.userToggleable && userChoice != null -> userChoice
        else -> localDefaults.isEnabledByDefault(feature)
    }
}
