package app.gympulse.core.featuregate

import javax.inject.Inject

/** Lokale Standardwerte, wenn weder Remote-Config noch Entitlements widersprechen. */
interface LocalFeatureDefaults {
    fun isEnabledByDefault(feature: Feature): Boolean
}

/** Remote-Override. `null` heißt: keine Meinung; `false` wirkt als Kill-Switch. */
interface RemoteFeatureConfig {
    fun isEnabled(feature: Feature): Boolean?
}

/** Käufe bzw. Berechtigungen (später Google Play Billing). `null` heißt: keine Meinung. */
interface EntitlementProvider {
    fun hasEntitlement(feature: Feature): Boolean?
}

/** Version 1 ist komplett kostenlos, daher sind alle Features aktiv. */
class AllEnabledLocalFeatureDefaults @Inject constructor() : LocalFeatureDefaults {
    override fun isEnabledByDefault(feature: Feature): Boolean = true
}

/** Platzhalter ohne Remote-Anbindung. */
class NoOpRemoteFeatureConfig @Inject constructor() : RemoteFeatureConfig {
    override fun isEnabled(feature: Feature): Boolean? = null
}

/** Platzhalter ohne Billing-Anbindung. */
class NoOpEntitlementProvider @Inject constructor() : EntitlementProvider {
    override fun hasEntitlement(feature: Feature): Boolean? = null
}
