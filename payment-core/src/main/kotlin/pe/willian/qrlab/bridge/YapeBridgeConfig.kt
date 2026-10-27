package pe.willian.qrlab.bridge

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Configuración del bridge Yape, siempre desde entorno (nunca hardcodeada
 * en el sentido de credenciales reales; los defaults son solo de laboratorio).
 *
 * - YAPE_BRIDGE_KEY: secreto exigido en el header X-Bridge-Key.
 * - YAPE_MATCH_SECRET: secreto del HMAC del código de 3 dígitos.
 * - YAPE_MATCH_WINDOW_SECONDS: ventana de conciliación (default 600).
 * - YAPE_STATIC_QR_PAYLOAD_FILE: archivo privado con el payload QR estático.
 * - YAPE_NOTIFICATION_RULES_FILE: archivo privado con reglas del parser.
 */
data class YapeBridgeConfig(
    val bridgeKey: String,
    val matchSecret: String,
    val matchWindowSeconds: Long = 600,
    val staticQrPayloadFile: Path? = null,
    val rulesFile: Path? = null,
) {
    companion object {
        fun fromEnv(): YapeBridgeConfig = YapeBridgeConfig(
            bridgeKey = System.getenv("YAPE_BRIDGE_KEY")?.takeIf { it.isNotBlank() }
                ?: "lab-bridge-key",
            matchSecret = System.getenv("YAPE_MATCH_SECRET")?.takeIf { it.isNotBlank() }
                ?: "lab-match-secret",
            matchWindowSeconds = System.getenv("YAPE_MATCH_WINDOW_SECONDS")?.toLongOrNull()
                ?.takeIf { it > 0 } ?: 600,
            staticQrPayloadFile = System.getenv("YAPE_STATIC_QR_PAYLOAD_FILE")
                ?.takeIf { it.isNotBlank() }?.let(Paths::get),
            rulesFile = System.getenv("YAPE_NOTIFICATION_RULES_FILE")
                ?.takeIf { it.isNotBlank() }?.let(Paths::get),
        )
    }
}
