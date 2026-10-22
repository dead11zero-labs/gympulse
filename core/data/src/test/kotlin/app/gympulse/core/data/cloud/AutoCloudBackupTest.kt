package app.gympulse.core.data.cloud

import app.gympulse.core.data.FakeIntegrationSettings
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.featuregate.UserFeatureToggles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class AutoCloudBackupTest {

    private class FakeToggles : UserFeatureToggles {
        val values = MutableStateFlow<Map<Feature, Boolean>>(emptyMap())
        override fun current(feature: Feature): Boolean? = values.value[feature]
        override fun observe(feature: Feature): Flow<Boolean?> = values.map { it[feature] }
        override fun setEnabled(feature: Feature, enabled: Boolean) {
            values.value = values.value + (feature to enabled)
        }
    }

    private class FakeGate(private val toggles: FakeToggles) : FeatureGate {
        override fun isEnabled(feature: Feature): Boolean = toggles.current(feature) ?: false
        override fun observe(feature: Feature): Flow<Boolean> = toggles.values.map { it[feature] ?: false }
    }

    private class FakeAuth(override val configuration: CloudConfiguration) : CloudAuth {
        val user = MutableStateFlow<CloudUser?>(CloudUser("user-1", "jo@example.com"))
        override fun observeUser(): Flow<CloudUser?> = user
        override suspend fun currentUser(): CloudUser? = user.value
        override suspend fun signInWithGoogle(token: GoogleIdToken) = Unit
        override suspend fun signOut() {
            user.value = null
        }
    }

    private class FakeRemote : CloudBackupRemote {
        var upserts = 0
        var failure: Exception? = null
        override suspend fun upsert(row: CloudBackupRow) {
            failure?.let { throw it }
            upserts++
        }
        override suspend fun fetch(userId: String): CloudBackupRow? = null
        override suspend fun delete(userId: String) = Unit
    }

    private class FakeLocal : LocalBackupSource {
        override suspend fun exportJson(): String =
            """{"format":"gympulse-backup","schema_version":1,"exported_at":"2026-10-20T08:00:00Z","app_version":"0.1.0"}"""
        override suspend fun restoreJson(json: String) = RestoreSummary(0, 0, 0)
    }

    private val toggles = FakeToggles().apply { setEnabled(Feature.CLOUD_BACKUP, true) }
    private val settings = FakeIntegrationSettings().apply { autoBackup.value = true }
    private val remote = FakeRemote()
    private var online = true
    private var auth = FakeAuth(CloudConfiguration(backendConfigured = true, googleSignInConfigured = true))

    private fun autoBackup(): AutoCloudBackup {
        val time = object : TimeProvider {
            override fun now(): Instant = Instant.parse("2026-10-21T10:00:00Z")
            override fun zone(): ZoneId = ZoneOffset.UTC
        }
        val sync = CloudBackupSync(auth, remote, FakeLocal(), FakeGate(toggles), toggles, time)
        return AutoCloudBackup(sync, settings) { online }
    }

    @Test
    fun uploadsWhenEverythingIsReady() = runTest {
        assertEquals(AutoBackupResult.UPLOADED, autoBackup().runOnce())
        assertEquals(1, remote.upserts)
    }

    @Test
    fun runsAtMostOncePerAppStart() = runTest {
        val backup = autoBackup()
        assertEquals(AutoBackupResult.UPLOADED, backup.runOnce())
        assertEquals(AutoBackupResult.ALREADY_RAN, backup.runOnce())
        assertEquals(1, remote.upserts)
    }

    @Test
    fun doesNothingWhenSwitchIsOff() = runTest {
        settings.autoBackup.value = false
        assertEquals(AutoBackupResult.DISABLED, autoBackup().runOnce())
        assertEquals(0, remote.upserts)
    }

    @Test
    fun doesNothingWhenCloudFeatureIsOff() = runTest {
        toggles.setEnabled(Feature.CLOUD_BACKUP, false)
        assertEquals(AutoBackupResult.FEATURE_OFF, autoBackup().runOnce())
        assertEquals(0, remote.upserts)
    }

    @Test
    fun doesNothingWithoutBackend() = runTest {
        auth = FakeAuth(CloudConfiguration.NONE)
        assertEquals(AutoBackupResult.FEATURE_OFF, autoBackup().runOnce())
    }

    @Test
    fun doesNothingWhenOffline() = runTest {
        online = false
        assertEquals(AutoBackupResult.OFFLINE, autoBackup().runOnce())
        assertEquals(0, remote.upserts)
    }

    @Test
    fun doesNothingWhenSignedOut() = runTest {
        auth.user.value = null
        assertEquals(AutoBackupResult.NOT_SIGNED_IN, autoBackup().runOnce())
        assertEquals(0, remote.upserts)
    }

    @Test
    fun swallowsFailures() = runTest {
        remote.failure = CloudException(CloudException.Reason.NETWORK)
        assertEquals(AutoBackupResult.FAILED, autoBackup().runOnce())

        val other = autoBackup()
        remote.failure = IllegalStateException("boom")
        assertEquals(AutoBackupResult.FAILED, other.runOnce())
        assertTrue(remote.upserts == 0)
    }
}
