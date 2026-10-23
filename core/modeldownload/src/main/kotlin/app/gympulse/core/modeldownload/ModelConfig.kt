package app.gympulse.core.modeldownload

/**
 * Beschreibt eine Modelldatei, die die App laden darf. Alles, was beim Modellwechsel angepasst
 * werden muss, steht in [ModelConfig].
 */
data class ModelSpec(
    /** Stabiler Bezeichner, z. B. für Logs und Einstellungen. */
    val id: String,
    /** Dateiname unter `filesDir/models/`. */
    val fileName: String,
    /** Download-URL mit gepinntem Commit (nicht `main`), damit sich die Datei nicht unbemerkt ändert. */
    val url: String,
    /** Erwarteter SHA-256 der Datei, 64 Hex-Zeichen in Kleinbuchstaben. */
    val sha256: String,
    /** Exakte Dateigröße in Bytes. */
    val sizeBytes: Long,
    /** Freier Speicher, der zusätzlich zur Datei frei bleiben muss. */
    val reserveBytes: Long = DEFAULT_RESERVE_BYTES,
) {
    /** false, solange URL oder Hash noch Platzhalter sind. Dann startet kein Download. */
    val isConfigured: Boolean
        get() = !url.contains(ModelConfig.PLACEHOLDER) &&
            SHA256_PATTERN.matches(sha256) &&
            sizeBytes > 0

    companion object {
        /** 512 MiB Reserve: ca. 2 GB Download, ca. 2,5 GB frei nötig (Plan "Lokale KI", Abschnitt 4). */
        const val DEFAULT_RESERVE_BYTES: Long = 512L * 1024 * 1024
        private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")
    }
}

/**
 * Konfiguration des Modell-Downloads. Zum Modellwechsel nur diese Datei anpassen.
 *
 * Quelle laut Entscheidung: Hugging Face, Repo `litert-community/gemma-4-E2B-it-litert-lm`
 * (Apache 2.0, nicht gated). Dateiname und Größe sind auf Hugging Face nachgelesen.
 *
 * PLATZHALTER: Commit-Hash und SHA-256 sind noch nicht verifiziert und müssen vor dem Release
 * eingetragen werden (Hugging Face → Files → Datei → "Copy download link" bzw. LFS-Details mit
 * SHA-256; Commit aus dem Verlauf des Repos). Solange sie Platzhalter sind, meldet der Manager
 * `NOT_CONFIGURED` und lädt nichts.
 */
object ModelConfig {
    /** Markiert noch nicht verifizierte Werte. */
    const val PLACEHOLDER = "TODO_PLACEHOLDER"

    private const val HF_REPO = "litert-community/gemma-4-E2B-it-litert-lm"

    /** PLATZHALTER: 40-stelliger Commit-Hash des HF-Repos. */
    private const val HF_COMMIT = "TODO_PLACEHOLDER_COMMIT_SHA"

    private const val GEMMA_4_E2B_GPU_FILE = "gemma-4-E2B-it-gpu.litertlm"

    val GEMMA_4_E2B_GPU = ModelSpec(
        id = "gemma-4-e2b-it-gpu",
        fileName = GEMMA_4_E2B_GPU_FILE,
        url = "https://huggingface.co/$HF_REPO/resolve/$HF_COMMIT/$GEMMA_4_E2B_GPU_FILE",
        // PLATZHALTER: SHA-256 der Datei (LFS-Objekt) von Hugging Face.
        sha256 = "TODO_PLACEHOLDER_SHA256",
        // Verifiziert auf Hugging Face (22.10.2026): 2.008.432.640 Bytes.
        sizeBytes = 2_008_432_640L,
    )

    /** Modell, das die App lädt. */
    val CURRENT: ModelSpec = GEMMA_4_E2B_GPU
}
