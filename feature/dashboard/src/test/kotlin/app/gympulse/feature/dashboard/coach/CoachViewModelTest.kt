package app.gympulse.feature.dashboard.coach

import app.gympulse.core.data.ai.ModelDownloadManager
import app.gympulse.core.data.ai.ModelDownloadState
import app.gympulse.core.data.ai.StartDownloadResult
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.insights.narration.AppLanguage
import app.gympulse.core.insights.narration.InsightSummary
import app.gympulse.core.insights.narration.NarrationChunk
import app.gympulse.core.insights.narration.NarrationSource
import app.gympulse.core.insights.narration.SourceAwareNarrator
import app.gympulse.core.insights.narration.SummaryExercise
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CoachViewModelTest {

    private class FakeGate(enabled: Boolean) : FeatureGate {
        val coach = MutableStateFlow(enabled)
        override fun isEnabled(feature: Feature): Boolean = coach.value
        override fun observe(feature: Feature): Flow<Boolean> = coach
    }

    private class FakeData(first: InsightSummary) : CoachDataSource {
        val summary = MutableStateFlow(first)
        override fun observeSummary(): Flow<InsightSummary> = summary
    }

    private class FakeDownloads : ModelDownloadManager {
        override val state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotDownloaded)
        override val downloadSizeBytes: Long = 0
        override fun requiredFreeBytes(): Long = 0
        override fun startDownload(): StartDownloadResult = StartDownloadResult.Enqueued
        override fun cancelDownload() = Unit
        override fun deleteModel() = Unit
    }

    private class FakeNarrator(
        private val script: suspend kotlinx.coroutines.flow.FlowCollector<NarrationChunk>.() -> Unit,
    ) : SourceAwareNarrator {
        var calls = 0
        override fun narrateWithSource(summary: InsightSummary): Flow<NarrationChunk> {
            calls++
            return flow { script() }
        }
    }

    private fun summary(text: String = "Bankdrücken: e1RM 100 kg") = InsightSummary(
        language = AppLanguage.DE,
        asOf = LocalDate.of(2026, 10, 20),
        weeks = 4,
        exercises = listOf(SummaryExercise("Bankdrücken", 100.0, "steigend", "+2,5 kg")),
        muscles = emptyList(),
        findings = emptyList(),
        text = text,
    )

    private fun ai(vararg parts: String): suspend kotlinx.coroutines.flow.FlowCollector<NarrationChunk>.() -> Unit = {
        parts.forEach { emit(NarrationChunk(it, NarrationSource.AI)) }
    }

    private val gate = FakeGate(true)
    private val data = FakeData(summary())
    private val downloads = FakeDownloads()
    private val cache = CoachTextCache()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(narrator: SourceAwareNarrator) = CoachViewModel(gate, data, narrator, downloads, cache)

    private fun kotlinx.coroutines.test.TestScope.collect(vm: CoachViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
    }

    @Test
    fun `card is hidden while the feature is off`() = runTest {
        val gate = FakeGate(false)
        val narrator = FakeNarrator(ai("x"))
        val vm = CoachViewModel(gate, data, narrator, downloads, cache)
        backgroundScope.launch { vm.uiState.collect {} }
        assertEquals(CoachUiState.Hidden, vm.uiState.value)
        assertEquals(0, narrator.calls)
    }

    @Test
    fun `streams AI text and caches the result`() = runTest {
        val narrator = FakeNarrator(ai("• a", " b"))
        val vm = vm(narrator)
        collect(vm)
        assertEquals(CoachUiState.Content("• a b", NarrationSource.AI, streaming = false), vm.uiState.value)
        assertEquals("• a b", cache.get(CoachTextCache.Key(AppLanguage.DE, summary().text)))
    }

    @Test
    fun `shows partial text and loading state while the model works`() = runTest {
        val gateOpen = CompletableDeferred<Unit>()
        val narrator = FakeNarrator {
            emit(NarrationChunk("• first", NarrationSource.AI))
            gateOpen.await()
            emit(NarrationChunk(" second", NarrationSource.AI))
        }
        val vm = vm(narrator)
        collect(vm)
        assertEquals(CoachUiState.Content("• first", NarrationSource.AI, streaming = true), vm.uiState.value)
        gateOpen.complete(Unit)
        assertEquals(CoachUiState.Content("• first second", NarrationSource.AI, streaming = false), vm.uiState.value)
    }

    @Test
    fun `loading is shown until the first delta arrives`() = runTest {
        val gateOpen = CompletableDeferred<Unit>()
        val vm = vm(
            FakeNarrator {
                gateOpen.await()
                emit(NarrationChunk("• x", NarrationSource.AI))
            },
        )
        collect(vm)
        assertEquals(CoachUiState.Loading, vm.uiState.value)
        gateOpen.complete(Unit)
        assertTrue(vm.uiState.value is CoachUiState.Content)
    }

    @Test
    fun `template text is shown as template and not cached`() = runTest {
        val vm = vm(FakeNarrator { emit(NarrationChunk("• t", NarrationSource.TEMPLATE)) })
        collect(vm)
        assertEquals(CoachUiState.Content("• t", NarrationSource.TEMPLATE, streaming = false), vm.uiState.value)
        assertEquals(null, cache.get(CoachTextCache.Key(AppLanguage.DE, summary().text)))
    }

    @Test
    fun `an error mid stream falls back to template text`() = runTest {
        val vm = vm(
            FakeNarrator {
                emit(NarrationChunk("• half", NarrationSource.AI))
                throw IllegalStateException("native crash")
            },
        )
        collect(vm)
        val state = vm.uiState.value as CoachUiState.Content
        assertEquals(NarrationSource.TEMPLATE, state.source)
        assertTrue(state.text.startsWith("• "))
        assertTrue(!state.streaming && !state.text.contains("half"))
    }

    @Test
    fun `cached text is reused without calling the model again`() = runTest {
        cache.put(CoachTextCache.Key(AppLanguage.DE, summary().text), "• cached")
        val narrator = FakeNarrator(ai("• new"))
        val vm = vm(narrator)
        collect(vm)
        assertEquals(CoachUiState.Content("• cached", NarrationSource.AI, streaming = false), vm.uiState.value)
        assertEquals(0, narrator.calls)
    }

    @Test
    fun `regenerate drops the cache and asks the model again`() = runTest {
        var round = 0
        val narrator = FakeNarrator { emit(NarrationChunk("• v${++round}", NarrationSource.AI)) }
        val vm = vm(narrator)
        collect(vm)
        assertEquals("• v1", (vm.uiState.value as CoachUiState.Content).text)
        vm.regenerate()
        assertEquals("• v2", (vm.uiState.value as CoachUiState.Content).text)
    }

    @Test
    fun `model becoming ready triggers a new narration`() = runTest {
        val narrator = FakeNarrator(ai("• x"))
        val vm = vm(narrator)
        collect(vm)
        cache.clear()
        downloads.state.value = ModelDownloadState.Ready(1L)
        assertEquals(2, narrator.calls)
    }

    @Test
    fun `unchanged summary does not restart the narration`() = runTest {
        val narrator = FakeNarrator(ai("• x"))
        val vm = vm(narrator)
        collect(vm)
        data.summary.value = summary()
        assertEquals(1, narrator.calls)
    }

    @Test
    fun `nothing to say hides the card`() = runTest {
        data.summary.value = summary().copy(exercises = emptyList())
        val vm = vm(FakeNarrator(ai("• x")))
        collect(vm)
        assertEquals(CoachUiState.Hidden, vm.uiState.value)
    }

    @Test
    fun `turning the feature off hides the card again`() = runTest {
        val vm = vm(FakeNarrator(ai("• x")))
        collect(vm)
        gate.coach.value = false
        assertEquals(CoachUiState.Hidden, vm.uiState.value)
    }
}
