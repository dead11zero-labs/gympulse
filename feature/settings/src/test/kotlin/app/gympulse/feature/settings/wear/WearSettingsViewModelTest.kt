package app.gympulse.feature.settings.wear

import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.wearsync.WatchAppLauncher
import app.gympulse.core.wearsync.WatchAppState
import app.gympulse.core.wearsync.WatchAppStatus
import app.gympulse.core.wearsync.WatchLaunchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WearSettingsViewModelTest {

    private class FakeGate(enabled: Boolean) : FeatureGate {
        val wear = MutableStateFlow(enabled)
        override fun isEnabled(feature: Feature): Boolean = feature != Feature.WEAR_APP || wear.value
        override fun observe(feature: Feature): Flow<Boolean> = if (feature == Feature.WEAR_APP) wear else MutableStateFlow(true)
    }

    private class FakeStatus(state: WatchAppState) : WatchAppStatus {
        override val state = MutableStateFlow(state)
    }

    private class FakeLauncher(var result: WatchLaunchResult = WatchLaunchResult.STARTED) : WatchAppLauncher {
        var installCalls = 0
        var openCalls = 0
        override suspend fun openInstallPageOnWatch(): WatchLaunchResult {
            installCalls++
            return result
        }
        override suspend fun openAppOnWatch(): WatchLaunchResult {
            openCalls++
            return result
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        gate: FakeGate = FakeGate(true),
        status: FakeStatus = FakeStatus(WatchAppState.NONE),
        launcher: FakeLauncher = FakeLauncher(),
    ) = WearSettingsViewModel(gate, status, launcher)

    private fun kotlinx.coroutines.test.TestScope.collect(vm: WearSettingsViewModel) =
        backgroundScope.launch { vm.uiState.collect {} }

    @Test
    fun `entry is hidden while the feature is off`() = runTest {
        val vm = viewModel(gate = FakeGate(false))
        collect(vm)
        assertFalse(vm.uiState.value.featureEnabled)
        assertFalse(vm.uiState.value.canInstall)
    }

    @Test
    fun `connection reflects watch state`() {
        fun connection(installed: Boolean, connected: Boolean) =
            WearSettingsUiState(true, WatchAppState(installed, connected)).connection
        assertEquals(WatchConnection.CONNECTED, connection(installed = true, connected = true))
        assertEquals(WatchConnection.INSTALLED_NOT_CONNECTED, connection(installed = true, connected = false))
        assertEquals(WatchConnection.NOT_DETECTED, connection(installed = false, connected = false))
    }

    @Test
    fun `install is only possible without the watch app`() = runTest {
        val launcher = FakeLauncher()
        val status = FakeStatus(WatchAppState(installed = true, connected = true))
        val vm = viewModel(status = status, launcher = launcher)
        collect(vm)
        vm.installOnWatch()
        assertEquals(0, launcher.installCalls)

        status.state.value = WatchAppState.NONE
        vm.installOnWatch()
        assertEquals(1, launcher.installCalls)
        assertEquals(WearMessage.INSTALL_STARTED, vm.uiState.value.message)
    }

    @Test
    fun `open needs an installed and connected watch app`() = runTest {
        val launcher = FakeLauncher()
        val status = FakeStatus(WatchAppState(installed = true, connected = false))
        val vm = viewModel(status = status, launcher = launcher)
        collect(vm)
        vm.openOnWatch()
        assertEquals(0, launcher.openCalls)

        status.state.value = WatchAppState(installed = true, connected = true)
        vm.openOnWatch()
        assertEquals(1, launcher.openCalls)
        assertEquals(WearMessage.OPEN_STARTED, vm.uiState.value.message)
    }

    @Test
    fun `launch results map to messages and can be dismissed`() = runTest {
        val launcher = FakeLauncher(WatchLaunchResult.NO_WATCH)
        val vm = viewModel(launcher = launcher)
        collect(vm)
        vm.installOnWatch()
        assertEquals(WearMessage.NO_WATCH, vm.uiState.value.message)
        assertFalse(vm.uiState.value.busy)

        launcher.result = WatchLaunchResult.FAILED
        vm.dismissMessage()
        assertNull(vm.uiState.value.message)
        vm.installOnWatch()
        assertEquals(WearMessage.FAILED, vm.uiState.value.message)
    }

    @Test
    fun `actions are disabled when the feature is off`() = runTest {
        val launcher = FakeLauncher()
        val vm = viewModel(gate = FakeGate(false), launcher = launcher)
        collect(vm)
        vm.installOnWatch()
        assertEquals(0, launcher.installCalls)
        assertTrue(!vm.uiState.value.canInstall && !vm.uiState.value.canOpen)
    }
}
