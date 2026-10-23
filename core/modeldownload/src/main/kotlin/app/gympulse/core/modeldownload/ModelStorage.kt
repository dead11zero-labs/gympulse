package app.gympulse.core.modeldownload

import app.gympulse.core.data.ai.ModelLocator
import java.io.File

/** Freier Speicher im Zielverzeichnis; austauschbar für Tests. */
fun interface FreeSpaceProvider {
    fun availableBytes(directory: File): Long
}

/**
 * Ablage des Modells in `filesDir/models/` (nicht `cacheDir`, das System darf den Cache leeren).
 * Während des Downloads schreibt der Worker in eine `.part`-Datei; erst nach erfolgreicher
 * SHA-256-Prüfung wird sie atomar umbenannt. Damit ist [modelFile] nie eine halbe Datei.
 */
class ModelStorage(
    val directory: File,
    val spec: ModelSpec,
    private val freeSpace: FreeSpaceProvider = FreeSpaceProvider { dir ->
        dir.mkdirs()
        dir.usableSpace
    },
) : ModelLocator {
    val targetFile: File get() = File(directory, spec.fileName)
    val partialFile: File get() = File(directory, spec.fileName + PARTIAL_SUFFIX)

    override fun modelFile(): File? = targetFile.takeIf { it.isFile && it.length() == spec.sizeBytes }

    fun partialBytes(): Long = partialFile.let { if (it.isFile) it.length() else 0L }

    /** Speicher, der für den Rest des Downloads plus Reserve frei sein muss. */
    fun requiredFreeBytes(): Long {
        if (modelFile() != null) return 0L
        val remaining = (spec.sizeBytes - partialBytes()).coerceAtLeast(0L)
        return remaining + spec.reserveBytes
    }

    fun availableBytes(): Long = freeSpace.availableBytes(directory)

    fun deletePartial() {
        partialFile.delete()
    }

    /** Löscht Modell und Teildatei. */
    fun deleteAll() {
        deletePartial()
        targetFile.delete()
    }

    /** Entfernt Dateien früherer Modellversionen, die nicht mehr zu [spec] gehören. */
    fun deleteStaleFiles() {
        val keep = setOf(targetFile.name, partialFile.name)
        directory.listFiles()?.filter { it.isFile && it.name !in keep }?.forEach { it.delete() }
    }

    companion object {
        const val PARTIAL_SUFFIX = ".part"
    }
}
