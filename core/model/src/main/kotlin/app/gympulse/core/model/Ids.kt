package app.gympulse.core.model

import java.security.SecureRandom

/**
 * Erzeugt UUIDs der Version 7 (RFC 9562): 48 Bit Unix-Zeit in Millisekunden, danach Zufall.
 * Zeitlich sortierbar, global eindeutig und damit geeignet für den späteren Server-Sync.
 */
object Ids {
    private val random = SecureRandom()

    fun newId(nowMillis: Long = System.currentTimeMillis()): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        for (i in 0 until 6) {
            bytes[i] = (nowMillis ushr (40 - 8 * i)).toByte()
        }
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte() // Version 7
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte() // Variante RFC 9562
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
