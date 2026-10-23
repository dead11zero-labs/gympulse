package app.gympulse.core.data.ai

import java.io.File

/**
 * Liefert die Datei des lokalen KI-Modells. Die Implementierung liegt in `core:modeldownload`;
 * hier steht nur die Schnittstelle, damit `core:ai` und die UI ohne Download-Code auskommen.
 */
interface ModelLocator {
    /**
     * Vollständig geladene und per SHA-256 geprüfte Modelldatei oder null, solange kein Modell
     * vorhanden ist. Teilweise geladene Dateien liefert diese Methode nie.
     */
    fun modelFile(): File?
}
