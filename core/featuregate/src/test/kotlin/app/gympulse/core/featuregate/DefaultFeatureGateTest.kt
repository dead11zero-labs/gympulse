package app.gympulse.core.featuregate

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultFeatureGateTest {

    private class FakeToggles : UserFeatureToggles {
        val values = MutableStateFlow<Map<Feature, Boolean>>(emptyMap())

        override fun current(feature: Feature): Boolean? = values.value[feature]

        override fun observe(feature: Feature): Flow<Boolean?> = values.map { it[feature] }

        override fun setEnabled(feature: Feature, enabled: Boolean) {
            values.value = values.value + (feature to enabled)
        }
    }

    private val toggles = FakeToggles()

    private fun gate(
        remote: (Feature) -> Boolean? = { null },
        entitlement: (Feature) -> Boolean? = { null },
    ) = DefaultFeatureGate(
        localDefaults = StaticLocalFeatureDefaults(),
        remoteConfig = object : RemoteFeatureConfig {
            override fun isEnabled(feature: Feature): Boolean? = remote(feature)
        },
        entitlements = object : EntitlementProvider {
            override fun hasEntitlement(feature: Feature): Boolean? = entitlement(feature)
        },
        userToggles = toggles,
    )

    @Test
    fun featuresFollowTheirLocalDefault() {
        val gate = gate()
        Feature.entries.forEach { assertEquals("$it", it.enabledByDefault, gate.isEnabled(it)) }
    }

    @Test
    fun healthConnectIsOffByDefault() {
        assertFalse(gate().isEnabled(Feature.HEALTH_CONNECT))
    }

    @Test
    fun aiCoachIsOffByDefaultAndUserToggleable() = runTest {
        val gate = gate()
        assertFalse(gate.isEnabled(Feature.AI_COACH))
        toggles.setEnabled(Feature.AI_COACH, true)
        assertTrue(gate.observe(Feature.AI_COACH).first())
        toggles.setEnabled(Feature.AI_COACH, false)
        assertFalse(gate.isEnabled(Feature.AI_COACH))
    }

    @Test
    fun cloudBackupIsOffByDefaultAndUserToggleable() = runTest {
        val gate = gate()
        assertFalse(gate.isEnabled(Feature.CLOUD_BACKUP))
        toggles.setEnabled(Feature.CLOUD_BACKUP, true)
        assertTrue(gate.observe(Feature.CLOUD_BACKUP).first())
    }

    @Test
    fun userChoiceEnablesToggleableFeature() = runTest {
        val gate = gate()
        toggles.setEnabled(Feature.HEALTH_CONNECT, true)
        assertTrue(gate.isEnabled(Feature.HEALTH_CONNECT))
        assertTrue(gate.observe(Feature.HEALTH_CONNECT).first())
    }

    @Test
    fun userChoiceIsIgnoredForNonToggleableFeature() {
        val gate = gate()
        toggles.values.value = mapOf(Feature.CALENDAR to false)
        assertTrue(gate.isEnabled(Feature.CALENDAR))
    }

    @Test
    fun remoteFalseOverridesUserChoice() {
        val gate = gate(remote = { if (it == Feature.HEALTH_CONNECT) false else null })
        toggles.setEnabled(Feature.HEALTH_CONNECT, true)
        assertFalse(gate.isEnabled(Feature.HEALTH_CONNECT))
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
