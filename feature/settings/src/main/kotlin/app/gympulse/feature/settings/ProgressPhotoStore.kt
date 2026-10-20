package app.gympulse.feature.settings

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Eine für die Kamera vorbereitete Zieldatei: [fileName] für die Datenbank, [uri] für die Kamera-App. */
class PendingCapture(val fileName: String, val uri: Uri)

/** Dateispeicher der Fortschrittsfotos im privaten App-Verzeichnis (kein Zugriff auf andere Apps nötig). */
interface ProgressPhotoStore {
    fun file(name: String): File

    fun newCapture(): PendingCapture

    /** Kopiert ein ausgewähltes Bild in den App-Speicher; null, wenn das Lesen scheitert. */
    suspend fun import(source: Uri): String?

    suspend fun delete(name: String)
}

@Singleton
class FilesDirProgressPhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ProgressPhotoStore {

    private fun directory(): File = File(context.filesDir, DIRECTORY).also { it.mkdirs() }

    // Nur der reine Dateiname zählt, damit kein Pfad aus dem Verzeichnis herausführt.
    override fun file(name: String): File = File(directory(), File(name).name)

    override fun newCapture(): PendingCapture {
        val name = newName()
        val target = file(name)
        target.createNewFile()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", target)
        return PendingCapture(name, uri)
    }

    override suspend fun import(source: Uri): String? = withContext(Dispatchers.IO) {
        val name = newName()
        val target = file(name)
        val copied = runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (copied && target.length() > 0) {
            name
        } else {
            target.delete()
            null
        }
    }

    override suspend fun delete(name: String) {
        withContext(Dispatchers.IO) { file(name).delete() }
    }

    private fun newName(): String = "photo_${UUID.randomUUID()}.jpg"

    private companion object {
        const val DIRECTORY = "progress_photos"
        const val AUTHORITY_SUFFIX = ".photos"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ProgressPhotoStoreModule {
    @Binds
    @Singleton
    abstract fun bindProgressPhotoStore(impl: FilesDirProgressPhotoStore): ProgressPhotoStore
}
