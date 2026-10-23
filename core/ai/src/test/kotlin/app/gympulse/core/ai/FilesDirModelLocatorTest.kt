package app.gympulse.core.ai

import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilesDirModelLocatorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(dir: File, name: String, size: Long): File = File(dir, name).also { f ->
        RandomAccessFile(f, "rw").use { it.setLength(size) }
    }

    @Test
    fun `no directory means no model`() {
        assertNull(FilesDirModelLocator(File(tmp.root, "missing")).locate())
    }

    @Test
    fun `truncated download is ignored`() {
        val dir = tmp.newFolder("models")
        file(dir, "gemma-4-E2B-it-gpu.litertlm", 10)
        assertNull(FilesDirModelLocator(dir, minSizeBytes = 1_000).locate())
    }

    @Test
    fun `prefers the first candidate`() {
        val dir = tmp.newFolder("models")
        file(dir, "gemma-4-E2B-it.litertlm", 2_000)
        val gpu = file(dir, "gemma-4-E2B-it-gpu.litertlm", 1_500)

        val model = FilesDirModelLocator(dir, minSizeBytes = 1_000).locate()
        assertEquals(gpu.absolutePath, model?.path)
        assertEquals(1_500L, model?.sizeBytes)
    }

    @Test
    fun `falls back to the generic file`() {
        val dir = tmp.newFolder("models")
        val generic = file(dir, "gemma-4-E2B-it.litertlm", 2_000)
        assertEquals(generic.absolutePath, FilesDirModelLocator(dir, minSizeBytes = 1_000).locate()?.path)
    }
}
