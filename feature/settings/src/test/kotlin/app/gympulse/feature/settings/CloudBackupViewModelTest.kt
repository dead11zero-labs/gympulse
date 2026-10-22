package app.gympulse.feature.settings

import app.gympulse.core.data.cloud.CloudAuth
import app.gympulse.core.data.cloud.CloudBackupRemote
import app.gympulse.core.data.cloud.CloudBackupRow
import app.gympulse.core.data.cloud.CloudBackupSync
import app.gympulse.core.data.cloud.CloudConfiguration
import app.gympulse.core.data.cloud.CloudException
import app.gympulse.core.data.cloud.CloudUser
import app.gympulse.core.data.cloud.GoogleIdToken
import app.gympulse.core.data.cloud.GoogleIdTokenRequester
import app.gympulse.core.data.cloud.LocalBackupSource
import app.gympulse.core.data.cloud.RestoreSummary
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.featuregate.UserFeatureToggles
import app.gympulse.feature.settings.cloud.CloudBackupViewModel
import app.gympulse.feature.settings.cloud.CloudConfirmation
import app.gympulse.feature.settings.cloud.CloudMessage
import app.gympulse.feature.settings.cloud.CloudOperation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class CloudBackupViewModelTest {

    private class FakeToggles : UserFeatureToggles {
        val values = MutableStateFlow<Map<Feature, Boolean>>(emptyMap())
        override fun current(feature: Feature): Boolean? = values.value[feature]
        override fun observe(feature: Feature): Flow<Boolean?> = values.map { it[feature] }
        override fun setEnabled(feature: Feature, enabled: Boolean) {
            values.value = values.value + (feature to enabled)
        }
    }

    /** Opt-in-Feature: aus, bis der Nutzer es einschaltet. */
    private class FakeGate(private val toggles: FakeToggles) : FeatureGate {
        override fun isEnabled(feature: Feature): Boolean = toggles.current(feature) ?: false
        override fun observe(feature: Feature): Flow<Boolean> = toggles.values.map { it[feature] ?: false }
    }

    private class FakeAuth(
        override val configuration: CloudConfiguration = CloudConfiguration(true, true),
    ) : CloudAuth {
        val user = MutableStateFlow<CloudUser?>(null)
        var signInFailure: CloudException? = null
        var lastToken: GoogleIdToken? = null

        override fun observeUser(): Flow<CloudUser?> = user
        override suspend fun currentUser(): CloudUser? = user.value
        override suspend fun signInWithGoogle(token: GoogleIdToken) {
            signInFailure?.let { throw it }
            lastToken = token
            user.value = CloudUser("user-1", "jo@example.com")
        }
        override suspend fun signOut() {
            user.value = null
        }
    }

    private class FakeRemote : CloudBackupRemote {
        val rows = mutableMapOf<String, CloudBackupRow>()
        var failure: CloudException? = null

        override suspend fun upsert(row: CloudBackupRow) {
            failure?.let { throw it }
            rows[row.userId] = row
        }
        override suspend fun fetch(userId: String): CloudBackupRow? {
            failure?.let { throw it }
            return rows[userId]
        }
        override suspend fun delete(userId: String) {
            rows.remove(userId)
        }
    }

    private class FakeLocal : LocalBackupSource {
        val restored = mutableListOf<String>()
        override suspend fun exportJson(): String =
            """{"format":"gympulse-backup","schema_version":1,"exported_at":"2026-10-20T08:00:00Z","app_version":"0.1.0"}"""
        override suspend fun restoreJson(json: String): RestoreSummary {
            restored += json
            return RestoreSummary(inserted = 3, updated = 1, skipped = 2)
        }
    }

    private val now = Instant.parse("2026-10-21T10:00:00Z")
    private val time = object : TimeProvider {
        override fun now(): Instant = now
        override fun zone(): ZoneId = ZoneOffset.UTC
    }
    private val toggles = FakeToggles()
    private val gate = FakeGate(toggles)
    private val remote = FakeRemote()
    private val local = FakeLocal()
    private val integration = FakeIntegrationSettings()
    private val token = GoogleIdTokenRequester { GoogleIdToken("id-token", "nonce") }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(auth: FakeAuth = FakeAuth()) =
        CloudBackupViewModel(CloudBackupSync(auth, remote, local, gate, toggles, time), integration)

    @Test
    fun featureIsOffByDefault() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertTrue(state.available)
        assertFalse(state.enabled)
        assertFalse(state.canSignIn)
    }

    @Test
    fun cannotEnableWithoutBackend() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(FakeAuth(CloudConfiguration.NONE))
        backgroundScope.launch { vm.uiState.collect {} }

        vm.setEnabled(true)

        assertFalse(vm.uiState.value.enabled)
        assertFalse(vm.uiState.value.available)
    }

    @Test
    fun signInIsDisabledWithoutGoogleClientId() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuth(CloudConfiguration(backendConfigured = true, googleSignInConfigured = false))
        val vm = viewModel(auth)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.setEnabled(true)

        assertTrue(vm.uiState.value.active)
        assertFalse(vm.uiState.value.canSignIn)
        vm.signIn(token)
        assertNull(auth.lastToken)
    }

    @Test
    fun signInPassesTokenAndNonce() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuth()
        val vm = viewModel(auth)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.setEnabled(true)

        vm.signIn(token)

        assertEquals(GoogleIdToken("id-token", "nonce"), auth.lastToken)
        assertEquals("jo@example.com", vm.uiState.value.user?.email)
        assertNull(vm.uiState.value.working)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun cancelledSignInShowsNoMessage() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.setEnabled(true)

        vm.signIn { throw CloudException(CloudException.Reason.CANCELLED) }

        assertNull(vm.uiState.value.message)
        assertNull(vm.uiState.value.user)
        assertNull(vm.uiState.value.working)
    }

    @Test
    fun backupUploadsRowAndShowsTimestamp() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.setEnabled(true)
        vm.signIn(token)
        assertNull(vm.uiState.value.latest)

        vm.backupNow()

        val row = remote.rows.getValue("user-1")
        assertEquals("2026-10-21T10:00:00Z", row.updatedAt)
        val state = vm.uiState.value
        assertEquals(now, state.latest?.updatedAt)
        assertTrue(state.message is CloudMessage.BackupDone)
        assertTrue(state.canRestore)
    }

    @Test
    fun existingBackupIsLoadedAfterSignIn() = runTest(UnconfinedTestDispatcher()) {
        val auth = FakeAuth()
        val vm = viewModel(auth)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.setEnabled(true)
        vm.signIn(token)
        vm.backupNow()
        vm.signOut()
        assertNull(vm.uiState.value.latest)

        vm.signIn(token)

        assertEquals(now, vm.uiState.value.latest?.updatedAt)
    }

    @Test
    fun restoreNeedsConfirmationAndUsesImportPath() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.setEnabled(true)
        vm.signIn(token)
        vm.backupNow()
        vm.dismissMessage()

        vm.requestRestore()
        assertEquals(CloudConfirmation.RESTORE, vm.uiState.value.confirmation)
        assertTrue(local.restored.isEmpty())

        vm.confirm()

        assertEquals(1, local.restored.size)
        assertTrue(local.restored.single().contains("gympulse-backup"))
        val message = vm.uiState.value.message as CloudMessage.RestoreDone
        assertEquals(RestoreSummary(3, 1, 2), message.summary)
        assertNull(vm.uiState.value.confirmation)
    }

    @Test
    fun dismissingConfirmationDoesNothing() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.setEnabled(true)
        vm.signIn(token)
        vm.backupNow()

        vm.requestDelete()
        vm.dismissConfirmation()

        assertNull(vm.uiState.value.confirmation)
        assertNotNull(remote.rows["user-1"])
    }

    @Test
    fun deleteRemovesRemoteRow() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.setEnabled(true)
        vm.signIn(token)
        vm.backupNow()

        vm.requestDelete()
        vm.confirm()

        assertTrue(remote.rows.isEmpty())
        assertNull(vm.uiState.value.latest)
        assertEquals(CloudMessage.Deleted, vm.uiState.value.message)
    }

    @Test
    fun networkErrorBecomesMessage() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.setEnabled(true)
        vm.signIn(token)
        remote.failure = CloudException(CloudException.Reason.NETWORK)

        vm.backupNow()

        val message = vm.uiState.value.message as CloudMessage.Failed
        assertEquals(CloudOperation.BACKUP, message.operation)
        assertEquals(CloudException.Reason.NETWORK, message.reason)
        assertNull(vm.uiState.value.working)
    }

    @Test
    fun disablingHidesAccount() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.setEnabled(true)
        vm.signIn(token)
        assertTrue(vm.uiState.value.signedIn)

        vm.setEnabled(false)

        assertFalse(vm.uiState.value.active)
        assertNull(vm.uiState.value.user)
    }

    @Test
    fun autoBackupIsOffByDefaultAndStoredWhenSwitched() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        assertFalse(vm.uiState.value.autoBackup)

        vm.setAutoBackup(true)

        assertTrue(integration.autoBackup.value)
        assertTrue(vm.uiState.value.autoBackup)

        vm.setAutoBackup(false)
        assertFalse(vm.uiState.value.autoBackup)
    }
}
