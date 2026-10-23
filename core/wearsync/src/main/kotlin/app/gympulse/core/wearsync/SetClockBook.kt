package app.gympulse.core.wearsync

import app.gympulse.core.model.SetEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feld-Uhren der Sätze für die Konfliktregel (`commandWins`).
 *
 * Das Telefon speichert `updated_at` mit seiner eigenen Uhrzeit, auch wenn es ein Kommando der
 * Uhr anwendet. Ohne Korrektur würde ein zweites, offline gepuffertes Kommando der Uhr (älteres
 * `issuedAt`) gegen die Übernahmezeit des ersten verlieren. Deshalb merkt sich das Buch nach jedem
 * angewendeten Kommando, welches `updated_at` dabei entstand und welches `issuedAt` dahinter steht.
 * Solange der Satz unverändert so gespeichert ist, gilt das `issuedAt` als Feld-Uhr; ändert ihn das
 * Telefon danach selbst, gilt wieder dessen `updated_at`.
 *
 * Nur im Speicher: Nach einem Neustart gilt `updated_at`, die Regel wird also höchstens strenger.
 */
@Singleton
class SetClockBook @Inject constructor() {
    private data class Entry(val storedUpdatedAt: Long, val issuedAt: Long)

    private val entries = MutableStateFlow<Map<String, Entry>>(emptyMap())
    private val _revision = MutableStateFlow(0)

    /** Zählt jede Änderung, damit der Publisher die korrigierten Zeiten nachreicht. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** Feld-Uhr des Satzes in Unix-Millisekunden; 0 für einen nie gespeicherten Satz. */
    fun clockOf(set: SetEntry): Long {
        val updatedAt = set.updatedAt?.toEpochMilli() ?: return 0L
        val entry = entries.value[set.id] ?: return updatedAt
        return if (entry.storedUpdatedAt == updatedAt) entry.issuedAt else updatedAt
    }

    /** [stored] ist der Satz nach dem Anwenden eines Kommandos von [issuedAt]. */
    fun record(stored: SetEntry, issuedAt: Long) {
        val updatedAt = stored.updatedAt?.toEpochMilli() ?: return
        entries.update { it + (stored.id to Entry(updatedAt, issuedAt)) }
        _revision.update { it + 1 }
    }

    /** `updated_at` je Satz-ID für den Snapshot, mit den korrigierten Feld-Uhren. */
    fun updatedAtFor(sets: Collection<SetEntry>): Map<String, Instant> =
        sets.filter { it.updatedAt != null }.associate { it.id to Instant.ofEpochMilli(clockOf(it)) }

    /** Vergisst alles, z. B. wenn keine Session mehr läuft. */
    fun clear() {
        if (entries.value.isEmpty()) return
        entries.value = emptyMap()
        _revision.update { it + 1 }
    }
}
