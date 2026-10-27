package pe.willian.yapebridge.bridge

import java.net.HttpURLConnection
import java.net.URL

data class BridgeHttpResponse(val status: Int, val body: String)

/** Sin redirecciones, sin logs de credenciales y con validación TLS normal. */
class BridgeHttpTransport(private val allowHttp: Boolean = false) {
    fun post(baseUrl: String, path: String, credential: String, body: String): BridgeHttpResponse {
        val base = CanonicalProtocol.secureUrl(baseUrl, allowHttp) ?: error("Configura una URL de Huata válida para este entorno")
        val enrollment=path=="/api/bridge/enroll"
        require(enrollment || path in listOf(BridgeConfig.NOTIFICATION_PATH, BridgeConfig.HEALTH_PATH))
        require(if(enrollment) credential.isEmpty() else credential.matches(Regex("[A-Za-z0-9._~+/-]{32,512}=*")))
        val connection = URL(base + path).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = BridgeConfig.CONNECT_TIMEOUT_MS
            connection.readTimeout = BridgeConfig.READ_TIMEOUT_MS
            if(!enrollment)connection.setRequestProperty("Authorization", "Bearer $credential")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val buffer = CharArray(4097)
                var total = 0
                while (total < buffer.size) {
                    val n = reader.read(buffer, total, buffer.size - total)
                    if (n == -1) break
                    total += n
                }
                check(total <= 4096) { "Respuesta de recepción inválida" }
                String(buffer, 0, total)
            }.orEmpty()
            return BridgeHttpResponse(status, text)
        } finally { connection.disconnect() }
    }
}
