package pe.willian.qrlab.bridge

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Comparación HMAC-SHA256 del código de seguridad de 3 dígitos.
 *
 * El código NUNCA se persiste en plano: solo su HMAC con el secreto
 * YAPE_MATCH_SECRET. Cuando el cajero introduce 3 dígitos se calcula el
 * HMAC y se compara en tiempo constante. El secreto nunca va a DB ni logs.
 */
object SecurityCodeMatcher {

    private val CODE_FORMAT = Regex("^\\d{3}$")

    fun isValidFormat(code: String): Boolean = CODE_FORMAT.matches(code)

    /** HMAC-SHA256 hex del código con el secreto dado. */
    fun hmacHex(code: String, secret: String): String {
        require(isValidFormat(code)) { "securityCode debe ser exactamente 3 dígitos" }
        require(secret.isNotEmpty()) { "secreto HMAC vacío" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(code.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /** Comparación en tiempo constante entre HMAC almacenado y candidato. */
    fun matches(storedHmacHex: String, candidateCode: String, secret: String): Boolean {
        if (!isValidFormat(candidateCode)) return false
        val candidate = try {
            hmacHex(candidateCode, secret)
        } catch (_: Exception) {
            return false
        }
        return MessageDigest.isEqual(
            storedHmacHex.lowercase().toByteArray(Charsets.UTF_8),
            candidate.lowercase().toByteArray(Charsets.UTF_8),
        )
    }
}
