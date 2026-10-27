package pe.willian.qrlab.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

@Serializable
data class PaymentClient(val id: String, val key: String, val receivers: List<String>, val receiverAdministration: Boolean = false)
@Serializable
data class PaymentReceiver(
    val id: String, val name: String, val phone: String, val qrSha256: String,
    val verified: Boolean = false, val enabled: Boolean = false,
)
@Serializable
data class PaymentDevice(val id: String, val key: String, val receiverId: String,
                         val packageName: String = "com.bcp.innovacxion.yapeapp")

/** Private operator-owned file. A receiver remains disabled until physical verification. */
@Serializable
data class CommercialConfig(
    val matchSecret: String,
    val clients: List<PaymentClient>,
    val receivers: List<PaymentReceiver>,
    val devices: List<PaymentDevice>,
    val ttlSeconds: Long = 300,
    val allowLocalHttp: Boolean = false,
) {
    fun validate(): CommercialConfig {
        require(matchSecret.length >= 32) { "Private match secret required (32+ characters)" }
        require(ttlSeconds in 30..1800)
        require(clients.map { it.id }.distinct().size == clients.size)
        require(devices.map { it.id }.distinct().size == devices.size)
        require(receivers.map { it.id }.distinct().size == receivers.size)
        require(receivers.all { it.id.matches(Regex("[a-zA-Z0-9_-]{1,100}")) && it.name.isNotBlank() && it.name.length<=100 && it.phone.matches(Regex("(?:51)?9[0-9]{8}")) })
        require(receivers.map { it.phone.takeLast(9) }.distinct().size == receivers.size) { "One receiver identity per account phone; duplicate account mappings are unsafe" }
        require((clients.map { it.key } + devices.map { it.key }).distinct().size == clients.size + devices.size)
        require(clients.all { it.key.length >= 32 && it.id.matches(Regex("[a-zA-Z0-9_-]{1,40}")) })
        require(devices.all { it.key.length >= 32 && receivers.any { r -> r.id == it.receiverId } })
        require(receivers.all { it.qrSha256.matches(Regex("[a-f0-9]{64}")) })
        require(clients.all { c -> c.receivers.all { id -> receivers.any { it.id == id } } })
        return this
    }
    fun client(token: String) = clients.singleOrNull { secureEquals(it.key, token) }
    fun device(token: String) = devices.singleOrNull { secureEquals(it.key, token) }
    companion object {
        fun fromEnv(): CommercialConfig {
            val file = System.getenv("PAYMENT_CONFIG_FILE") ?: error("PAYMENT_CONFIG_FILE is required; no insecure commercial defaults")
            val path=Path.of(file)
            val permissions=Files.getPosixFilePermissions(path)
            require(permissions.none { it.name.startsWith("GROUP_") || it.name.startsWith("OTHERS_") }) { "Configuration must be private to its operator (chmod 600)" }
            val config=Json.decodeFromString<CommercialConfig>(Files.readString(path)).validate()
            require((config.clients.map { it.key } + config.devices.map { it.key } + config.matchSecret).all { it.toSet().size >= 12 }) { "Generate independent cryptographically random secrets" }
            return config
        }
        private fun secureEquals(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
    }
}
