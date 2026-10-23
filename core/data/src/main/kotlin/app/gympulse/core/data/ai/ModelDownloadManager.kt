package app.gympulse.core.data.ai

import kotlinx.coroutines.flow.Flow

/**
 * Lädt das lokale KI-Modell (Opt-in, nur WLAN) und verwaltet es. Die Implementierung mit
 * WorkManager liegt in `core:modeldownload`.
 */
interface ModelDownloadManager {
    /** Aktueller Zustand des Modells; emittiert bei jedem Wechsel. */
    val state: Flow<ModelDownloadState>

    /** Downloadgröße der Modelldatei in Bytes, z. B. für den Opt-in-Dialog. */
    val downloadSizeBytes: Long

    /** Freier Speicher, den ein Download ab jetzt mindestens braucht (Rest der Datei plus Reserve). */
    fun requiredFreeBytes(): Long

    /**
     * Prüft vorab den Speicherplatz und plant den Download. Der Job startet erst, wenn ein
     * ungemessenes Netz (WLAN) verfügbar ist, und setzt einen abgebrochenen Download fort.
     */
    fun startDownload(): StartDownloadResult

    /** Bricht einen laufenden oder wartenden Download ab und verwirft die Teildatei. */
    fun cancelDownload()

    /** Löscht das Modell und eine eventuelle Teildatei. */
    fun deleteModel()
}

/** Zustand des lokalen KI-Modells. */
sealed interface ModelDownloadState {
    data object NotDownloaded : ModelDownloadState

    /**
     * Download geplant oder aktiv.
     * @property progress Anteil 0..1 oder null, solange die Größe unbekannt ist.
     * @property waitingForNetwork true, solange der Job auf WLAN wartet bzw. neu geplant ist.
     */
    data class Downloading(
        val progress: Float?,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val waitingForNetwork: Boolean = false,
    ) : ModelDownloadState

    /** Modell liegt vollständig und geprüft vor. */
    data class Ready(val sizeBytes: Long) : ModelDownloadState

    data class Failed(val reason: ModelDownloadFailure) : ModelDownloadState
}

/** Warum ein Download gescheitert ist. Stabile Codes für die UI. */
enum class ModelDownloadFailure {
    /** Zu wenig freier Speicher. */
    INSUFFICIENT_STORAGE,

    /** Die geladene Datei passt nicht zum hinterlegten SHA-256. */
    HASH_MISMATCH,

    /** Die Datei hat nicht die erwartete Größe. */
    SIZE_MISMATCH,

    /** Der Server hat mit einem Fehlerstatus geantwortet. */
    HTTP_ERROR,

    /** Netzwerkfehler, nachdem alle Wiederholungen ausgeschöpft waren. */
    NETWORK,

    /** URL oder Hash sind in der App noch nicht konfiguriert (Platzhalter). */
    NOT_CONFIGURED,

    /** Fehler beim Schreiben ins Dateisystem oder unbekannte Ursache. */
    UNKNOWN,
}

/** Ergebnis von [ModelDownloadManager.startDownload]. */
sealed interface StartDownloadResult {
    data object Enqueued : StartDownloadResult

    data object AlreadyReady : StartDownloadResult

    data class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) : StartDownloadResult

    data object NotConfigured : StartDownloadResult
}
