package app.gympulse.core.wearprotocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Protokollversion, JSON-Format und Kodierung der Data-Layer-Nutzlasten (UTF-8-JSON). */
object WearProtocol {
    /** Wird erhöht, wenn sich das Format inkompatibel ändert. Neue optionale Felder brauchen das nicht. */
    const val VERSION = 1

    /** So viele Quittungen trägt ein Snapshot höchstens mit. */
    const val MAX_ACKS = 64

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun isCompatible(protocolVersion: Int): Boolean = protocolVersion == VERSION

    fun encodeSnapshot(snapshot: WorkoutSnapshot): ByteArray = encode(WorkoutSnapshot.serializer(), snapshot)

    /** null bei kaputten Daten oder inkompatibler Protokollversion. */
    fun decodeSnapshot(bytes: ByteArray): WorkoutSnapshot? =
        decode(WorkoutSnapshot.serializer(), bytes)?.takeIf { isCompatible(it.protocolVersion) }

    fun encodePlans(plans: PlanSummaryList): ByteArray = encode(PlanSummaryList.serializer(), plans)

    fun decodePlans(bytes: ByteArray): PlanSummaryList? =
        decode(PlanSummaryList.serializer(), bytes)?.takeIf { isCompatible(it.protocolVersion) }

    fun encodeCommand(command: WearCommand): ByteArray =
        encode(CommandEnvelope.serializer(), CommandEnvelope(command = command))

    /** null bei kaputten Daten, unbekanntem Kommandotyp oder inkompatibler Protokollversion. */
    fun decodeCommand(bytes: ByteArray): WearCommand? =
        decode(CommandEnvelope.serializer(), bytes)?.takeIf { isCompatible(it.protocolVersion) }?.command

    private fun <T> encode(serializer: KSerializer<T>, value: T): ByteArray =
        json.encodeToString(serializer, value).encodeToByteArray()

    private fun <T> decode(serializer: KSerializer<T>, bytes: ByteArray): T? = try {
        json.decodeFromString(serializer, bytes.decodeToString())
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
