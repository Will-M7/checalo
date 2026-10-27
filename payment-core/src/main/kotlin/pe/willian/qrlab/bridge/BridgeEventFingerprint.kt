package pe.willian.qrlab.bridge

import java.security.MessageDigest
import java.time.Instant

/**
 * Fingerprint estable para deduplicación (NO es prueba criptográfica del pago).
 *
 * SHA-256 sobre: source package + notification key (si existe) + postedAt +
 * contenido textual normalizado. Una misma notificación reenviada varias
 * veces por Android produce el mismo fingerprint y se procesa una sola vez,
 * incluso después de reinicios (el fingerprint persiste UNIQUE en SQLite).
 */
object BridgeEventFingerprint {

    /** Normaliza texto: minúsculas, recorta y colapsa espacios. */
    fun normalize(text: String): String =
        text.lowercase().trim().replace(Regex("\\s+"), " ")

    fun compute(
        sourcePackage: String,
        notificationKey: String?,
        postedAt: Instant,
        textParts: List<String?>,
    ): String {
        val normalized = textParts.filterNotNull()
            .map(::normalize)
            .filter { it.isNotEmpty() }
            .sorted()
            .joinToString("\n")
        val joined = listOf(
            sourcePackage.trim(),
            notificationKey?.trim().orEmpty(),
            postedAt.toEpochMilli().toString(),
            normalized,
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(joined.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
