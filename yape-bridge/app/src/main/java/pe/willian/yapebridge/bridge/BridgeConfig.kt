package pe.willian.yapebridge.bridge

import java.net.URI

/** Dirección de Huata elegida por el operador; nunca se presupone un servidor. */
object BridgeConfig {
    const val DEFAULT_BASE_URL = ""
    const val NOTIFICATION_PATH = "/api/bridge/notifications"
    const val HEALTH_PATH = "/api/bridge/heartbeat"
    const val CONNECT_TIMEOUT_MS = 5000
    const val READ_TIMEOUT_MS = 15000

    fun normalizeBaseUrl(raw: String): String? {
        val value = raw.trim().trimEnd('/')
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) return null
        if (uri.userInfo != null || uri.query != null || uri.fragment != null) return null
        if (uri.port != -1 && uri.port !in 1..65535) return null
        if (uri.rawPath.orEmpty().split('/').any { it == "." || it == ".." }) return null
        return value
    }

    // Se conserva el nombre del helper existente; el único destino ahora es canónico.
    fun rawNotificationUrl(baseUrl: String) = baseUrl.trimEnd('/') + NOTIFICATION_PATH
    fun healthUrl(baseUrl: String) = baseUrl.trimEnd('/') + HEALTH_PATH
}
