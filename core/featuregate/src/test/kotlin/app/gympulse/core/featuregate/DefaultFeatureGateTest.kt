package app.gympulse.core.featuregate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultFeatureGateTest {

    private fun gate(
        remote: (Feature) -> Boolean? = { null },
        entitlement: (Feature) -> Boolean? = { null },
    ) = DefaultFeatureGate(
        localDefaults = AllEnabledLocalFeatureDefaults(),
        remoteConfig = object : RemoteFeatureConfig {
            override fun isEnabled(feature: Feature): Boolean? = remote(feature)
        },
        entitlements = object : EntitlementProvider {
            override fun hasEntitlement(feature: Feature): Boolean? = entitlement(feature)
        },
    )

    @Test
    fun allFeaturesEnabledByDefault() {
        val gate = gate()
        Feature.entries.forEach { assertTrue("$it", gate.isEnabled(it)) }
    }

    @Test
    fun remoteFalseDisablesFeature() {
        val gate = gate(remote = { if (it == Feature.CLOUD_SYNC) false else null })
        assertFalse(gate.isEnabled(Feature.CLOUD_SYNC))
        assertTrue(gate.isEnabled(Feature.CALENDAR))
    }

    @Test
    fun remoteFalseOverridesEntitlementTrue() {
        val gate = gate(remote = { false }, entitlement = { true })
        assertFalse(gate.isEnabled(Feature.CALENDAR))
    }

    @Test
    fun entitlementFalseDisablesPremiumFeature() {
        val gate = gate(entitlement = { false })
        assertFalse(gate.isEnabled(Feature.ADVANCED_STATS))
    }

    @Test
    fun entitlementNullKeepsDefault() {
        val gate = gate(entitlement = { null })
        assertTrue(gate.isEnabled(Feature.WEAR_APP))
    }
}
