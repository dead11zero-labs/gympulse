package app.gympulse.core.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Eine Modelldatei auf dem Gerät. [sizeBytes] und [lastModified] identifizieren den Stand: Ist ein
 * Laden fehlgeschlagen, wird dieselbe Datei erst nach einer Änderung (z. B. neuer Download) erneut
 * versucht.
 */
data class ModelFile(val path: String, val sizeBytes: Long, val lastModified: Long)

/** Findet das lokale Modell. Den Download übernimmt ein späterer PR (WorkManager, SHA-256). */
fun interface ModelLocator {
    /** Die nutzbare Modelldatei oder `null`, wenn keine (vollständige) Datei vorhanden ist. */
    fun locate(): ModelFile?
}

/**
 * Sucht in `filesDir/models/` (nicht `cacheDir`, das darf das System leeren) nach den bekannten
 * Gemma-4-E2B-Dateien. Dateien unter [minSizeBytes] gelten als abgebrochener Download.
 */
class FilesDirModelLocator internal constructor(
    private val modelsDir: File,
    private val candidates: List<String> = DEFAULT_CANDIDATES,
    private val minSizeBytes: Long = MIN_MODEL_SIZE_BYTES,
) : ModelLocator {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, MODELS_DIR))

    override fun locate(): ModelFile? = candidates.asSequence()
        .map { File(modelsDir, it) }
        .firstOrNull { it.isFile && it.length() >= minSizeBytes }
        ?.let { ModelFile(it.absolutePath, it.length(), it.lastModified()) }

    companion object {
        const val MODELS_DIR = "models"

        /**
         * Gemma 4 E2B-it aus `litert-community/gemma-4-E2B-it-litert-lm` (Apache 2.0). Welche Datei
         * auf dem S25 Ultra besser läuft, klärt die Messung; bevorzugt wird die GPU-Variante (2,01 GB).
         */
        val DEFAULT_CANDIDATES = listOf("gemma-4-E2B-it-gpu.litertlm", "gemma-4-E2B-it.litertlm")

        /** Deutlich unter der echten Größe (ca. 2 GB), fängt nur leere und abgebrochene Dateien ab. */
        const val MIN_MODEL_SIZE_BYTES = 500L * 1024 * 1024
    }
}
