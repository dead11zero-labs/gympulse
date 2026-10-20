package app.gympulse.core.data.repository

import app.gympulse.core.model.SetType

/**
 * Ein gespeicherter Satz einer abgeschlossenen Session samt dem Kontext, der für Bestleistungen
 * nötig ist (Körpergewicht der Session, Körpergewichtsfaktor der Übung). Gelöschte Sätze
 * ([deletedAt] gesetzt) und offene Sätze werden bewusst mitgeliefert; wer auswertet, filtert selbst.
 */
data class HistorySet(
    val setId: String,
    val sessionId: String,
    val exerciseId: String,
    val setType: SetType,
    val weightKg: Double?,
    val reps: Int?,
    val isCompleted: Boolean,
    /** Epoch-Millisekunden des Soft-Deletes (Satz oder Übung in der Session), sonst null. */
    val deletedAt: Long?,
    val bodyweightKg: Double?,
    val bodyweightFactor: Double?,
)
