package app.gympulse.feature.settings

import android.net.Uri
import app.gympulse.core.data.repository.ProgressPhotoRepository
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.model.ProgressPhoto
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressPhotosViewModelTest {

    private class FakeRepository : ProgressPhotoRepository {
        val items = MutableStateFlow<List<ProgressPhoto>>(emptyList())
        private var clock = 1_000L

        override fun observeAll(): Flow<List<ProgressPhoto>> = items

        override suspend fun add(fileName: String, note: String?): ProgressPhoto {
            clock += 1_000
            val photo = ProgressPhoto("id$clock", Instant.ofEpochMilli(clock), fileName, note?.takeIf { it.isNotBlank() })
            items.value += photo
            return photo
        }

        override suspend fun delete(id: String) {
            items.value = items.value.filterNot { it.id == id }
        }
    }

    private class FakeStore : ProgressPhotoStore {
        val deleted = mutableListOf<String>()

        override fun file(name: String): File = File(name)

        override fun newCapture(): PendingCapture = error("not used in unit tests")

        override suspend fun import(source: Uri): String? = null

        override suspend fun delete(name: String) {
            deleted += name
        }
    }

    private class FakeGate(enabled: Boolean) : FeatureGate {
        val enabled = MutableStateFlow(enabled)
        override fun isEnabled(feature: Feature): Boolean = enabled.value
        override fun observe(feature: Feature): Flow<Boolean> = enabled
    }

    private val repository = FakeRepository()
    private val store = FakeStore()
    private val gate = FakeGate(true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ProgressPhotosViewModel(repository, store, gate)

    @Test
    fun photosAreListedNewestFirst() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.save("a.jpg", "")
        viewModel.save("b.jpg", "front")

        val photos = viewModel.uiState.value.photos
        assertEquals(listOf("b.jpg", "a.jpg"), photos.map { it.fileName })
        assertEquals("front", photos.first().note)
    }

    @Test
    fun deleteRemovesEntryAndFile() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.save("a.jpg", "")
        val photo = viewModel.uiState.value.photos.single()

        viewModel.delete(photo)

        assertTrue(viewModel.uiState.value.photos.isEmpty())
        assertEquals(listOf("a.jpg"), store.deleted)
    }

    @Test
    fun discardOnlyRemovesFile() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.discard("tmp.jpg")

        assertEquals(listOf("tmp.jpg"), store.deleted)
        assertTrue(viewModel.uiState.value.photos.isEmpty())
    }

    @Test
    fun featureGateIsReflected() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        assertTrue(viewModel.uiState.value.enabled)

        gate.enabled.value = false

        assertFalse(viewModel.uiState.value.enabled)
    }
}
