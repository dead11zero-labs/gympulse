package app.gympulse.core.ai

import javax.inject.Inject
import app.gympulse.core.data.ai.ModelLocator as DownloadModelLocator

/**
 * Eine Modelldatei auf dem Gerät. [sizeBytes] und [lastModified] identifizieren den Stand: Ist ein
 * Laden fehlgeschlagen, wird dieselbe Datei erst nach einer Änderung (z. B. neuer Download) erneut
 * versucht.
 */
data class ModelFile(val path: String, val sizeBytes: Long, val lastModified: Long)

/** Findet das lokale Modell; schmale Sicht von `core:ai`, damit Tests ohne Dateisystem auskommen. */
fun interface ModelLocator {
    /** Die nutzbare Modelldatei oder `null`, wenn keine vollständige Datei vorhanden ist. */
    fun locate(): ModelFile?
}

/**
 * Adapter auf den Locator des Download-Managers (`core:modeldownload`, KI-PR 5): nur vollständig
 * geladene, geprüfte Dateien in `filesDir/models/` zählen.
 */
class DownloadedModelLocator @Inject constructor(
    private val source: DownloadModelLocator,
) : ModelLocator {
    override fun locate(): ModelFile? =
        source.modelFile()?.let { ModelFile(it.absolutePath, it.length(), it.lastModified()) }
}
