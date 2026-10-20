package app.gympulse.feature.settings

import app.gympulse.core.data.repository.BodyMeasurementRepository
import app.gympulse.core.model.BodyMeasurement
import app.gympulse.core.model.MeasurementSource
import app.gympulse.core.model.MeasurementType
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BodyMetricsViewModelTest {

    private class FakeRepository : BodyMeasurementRepository {
        val items = MutableStateFlow<List<BodyMeasurement>>(emptyList())
        private var clock = 1_000L
        private var nextId = 0

        override fun observeAll(): Flow<List<BodyMeasurement>> = items

        override suspend fun record(values: Map<MeasurementType, Double>) {
            clock += 1_000
            val at = Instant.ofEpochMilli(clock)
            items.value += values.map { (type, value) ->
                BodyMeasurement("n${nextId++}", at, type, value, MeasurementSource.MANUAL)
            }
        }

        override suspend fun updateValue(id: String, value: Double) {
            items.value = items.value.map { if (it.id == id) it.copy(value = value) else it }
        }

        override suspend fun delete(id: String) {
            items.value = items.value.filterNot { it.id == id }
        }
    }

    private val repository = FakeRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun entry(id: String, type: MeasurementType, value: Double, at: Long) =
        BodyMeasurement(id, Instant.ofEpochMilli(at), type, value, MeasurementSource.MANUAL)

    private fun createViewModel() = BodyMetricsViewModel(repository)

    @Test
    fun defaultsToWeightWithNewestFirstHistoryAndOldestFirstChart() = runTest(UnconfinedTestDispatcher()) {
        repository.items.value = listOf(
            entry("a", MeasurementType.WEIGHT, 82.0, at = 1_000),
            entry("b", MeasurementType.WEIGHT, 81.0, at = 3_000),
            entry("c", MeasurementType.WEIGHT, 81.5, at = 2_000),
            entry("d", MeasurementType.WAIST, 90.0, at = 2_000),
        )
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        assertEquals(MeasurementType.WEIGHT, state.selectedType)
        assertEquals(listOf("b", "c", "a"), state.history.map { it.id })
        assertEquals(listOf("a", "c", "b"), state.chart.map { it.id })
        assertEquals(-0.5, state.change!!, 1e-9)
    }

    @Test
    fun selectTypeFiltersHistory() = runTest(UnconfinedTestDispatcher()) {
        repository.items.value = listOf(
            entry("a", MeasurementType.WEIGHT, 82.0, at = 1_000),
            entry("d", MeasurementType.WAIST, 90.0, at = 2_000),
        )
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.selectType(MeasurementType.WAIST)

        val state = viewModel.uiState.value
        assertEquals(MeasurementType.WAIST, state.selectedType)
        assertEquals(listOf("d"), state.history.map { it.id })
        assertNull(state.change)
    }

    @Test
    fun addStoresEveryFilledValue() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.add(mapOf(MeasurementType.WEIGHT to 80.5, MeasurementType.BODY_FAT to 15.0))

        assertEquals(2, repository.items.value.size)
        assertEquals(listOf(80.5), viewModel.uiState.value.history.map { it.value })
        viewModel.selectType(MeasurementType.BODY_FAT)
        assertEquals(listOf(15.0), viewModel.uiState.value.history.map { it.value })
    }

    @Test
    fun addWithoutValuesDoesNothing() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = createViewModel()

        viewModel.add(emptyMap())

        assertEquals(0, repository.items.value.size)
    }

    @Test
    fun updateValueChangesEntry() = runTest(UnconfinedTestDispatcher()) {
        repository.items.value = listOf(entry("a", MeasurementType.WEIGHT, 82.0, at = 1_000))
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.updateValue("a", 79.5)

        assertEquals(listOf(79.5), viewModel.uiState.value.history.map { it.value })
    }

    @Test
    fun deleteRemovesEntry() = runTest(UnconfinedTestDispatcher()) {
        repository.items.value = listOf(
            entry("a", MeasurementType.WEIGHT, 82.0, at = 1_000),
            entry("b", MeasurementType.WEIGHT, 81.0, at = 2_000),
        )
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.delete("b")

        assertEquals(listOf("a"), viewModel.uiState.value.history.map { it.id })
        assertNull(viewModel.uiState.value.change)
    }
}
