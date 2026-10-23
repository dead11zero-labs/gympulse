package app.gympulse.feature.settings.ai

import app.gympulse.core.data.ai.ModelDownloadFailure
import app.gympulse.core.data.ai.ModelDownloadManager
import app.gympulse.core.data.ai.ModelDownloadState
import app.gympulse.core.data.ai.StartDownloadResult
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.featuregate.UserFeatureToggles
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiCoachViewModelTest {

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
        override fun observe(feature: Feature): Flow<Boolean> = toggles.observe(feature).map { it ?: false }
    }

    private class FakeDownloads : ModelDownloadManager {
        override val state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotDownloaded)
        override val downloadSizeBytes: Long = 2_008_432_640L
        var result: StartDownloadResult = StartDownloadResult.Enqueued
        var starts = 0
        var cancels = 0
        var deletes = 0

        override fun requiredFreeBytes(): Long = downloadSizeBytes + 536_870_912L

        override fun startDownload(): StartDownloadResult {
            starts++
            return result
        }

        override fun cancelDownload() {
            cancels++
        }

        override fun deleteModel() {
            deletes++
        }
    }

    private val toggles = FakeToggles()
    private val downloads = FakeDownloads()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun kotlinx.coroutines.test.TestScope.viewModel(): AiCoachViewModel {
        val vm = AiCoachViewModel(FakeGate(toggles), toggles, downloads)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private fun enabled() = toggles.current(Feature.AI_COACH) == true

    @Test
    fun `is off by default and shows no model section`() = runTest {
        val vm = viewModel()
        assertFalse(vm.uiState.value.enabled)
        assertFalse(vm.uiState.value.showModelSection)
    }

    @Test
    fun `switching on asks first and changes nothing until confirmed`() = runTest {
        val vm = viewModel()
        vm.setEnabled(true)
        assertEquals(AiCoachDialog.OPT_IN, vm.uiState.value.dialog)
        assertFalse(enabled())
        assertEquals(0, downloads.starts)
    }

    @Test
    fun `dismissing the opt-in dialog keeps the coach off`() = runTest {
        val vm = viewModel()
        vm.setEnabled(true)
        vm.dismissDialog()
        assertNull(vm.uiState.value.dialog)
        assertFalse(enabled())
    }

    @Test
    fun `confirming enables the coach and starts the download`() = runTest {
        val vm = viewModel()
        vm.setEnabled(true)
        vm.confirmOptIn()
        assertTrue(enabled())
        assertEquals(1, downloads.starts)
        assertNull(vm.uiState.value.dialog)
        assertNull(vm.uiState.value.notice)
        assertTrue(vm.uiState.value.enabled)
    }

    @Test
    fun `dialog numbers come from the download manager`() = runTest {
        val vm = viewModel()
        assertEquals(2_008_432_640L, vm.uiState.value.downloadSizeBytes)
        assertEquals(2_545_303_552L, vm.uiState.value.requiredFreeBytes)
    }

    @Test
    fun `an unconfigured model is handled as a notice and the coach stays on`() = runTest {
        downloads.result = StartDownloadResult.NotConfigured
        val vm = viewModel()
        vm.confirmOptIn()
        assertTrue(enabled())
        assertEquals(AiCoachNotice.NotConfigured, vm.uiState.value.notice)
    }

    @Test
    fun `insufficient storage is reported with both numbers`() = runTest {
        downloads.result = StartDownloadResult.InsufficientStorage(3L, 1L)
        val vm = viewModel()
        vm.confirmOptIn()
        assertEquals(AiCoachNotice.InsufficientStorage(3L, 1L), vm.uiState.value.notice)
    }

    @Test
    fun `retry after a failure starts again and clears an old notice`() = runTest {
        downloads.result = StartDownloadResult.NotConfigured
        val vm = viewModel()
        vm.confirmOptIn()
        downloads.state.value = ModelDownloadState.Failed(ModelDownloadFailure.NETWORK)
        assertTrue(vm.uiState.value.canStartDownload)
        downloads.result = StartDownloadResult.Enqueued
        vm.startDownload()
        assertEquals(2, downloads.starts)
        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun `switching off cancels a running download`() = runTest {
        val vm = viewModel()
        vm.confirmOptIn()
        downloads.state.value = ModelDownloadState.Downloading(0.4f, 40, 100)
        vm.setEnabled(false)
        assertFalse(enabled())
        assertEquals(1, downloads.cancels)
    }

    @Test
    fun `switching on with a ready model skips the dialog`() = runTest {
        downloads.state.value = ModelDownloadState.Ready(2_008_432_640L)
        val vm = viewModel()
        vm.setEnabled(true)
        assertNull(vm.uiState.value.dialog)
        assertTrue(enabled())
        assertEquals(0, downloads.starts)
    }

    @Test
    fun `deleting the model needs confirmation`() = runTest {
        downloads.state.value = ModelDownloadState.Ready(1L)
        val vm = viewModel()
        vm.requestDeleteModel()
        assertEquals(AiCoachDialog.DELETE_MODEL, vm.uiState.value.dialog)
        assertEquals(0, downloads.deletes)
        vm.confirmDeleteModel()
        assertEquals(1, downloads.deletes)
        assertNull(vm.uiState.value.dialog)
    }

    @Test
    fun `cancel forwards to the manager`() = runTest {
        val vm = viewModel()
        vm.cancelDownload()
        assertEquals(1, downloads.cancels)
    }

    @Test
    fun `existing model keeps the model section visible while the coach is off`() = runTest {
        downloads.state.value = ModelDownloadState.Ready(1L)
        val vm = viewModel()
        assertTrue(vm.uiState.value.showModelSection)
        assertFalse(vm.uiState.value.canStartDownload)
    }
}
