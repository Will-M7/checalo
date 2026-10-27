package com.example.ecolacteoshuata.data.remote

/**
 * Configuración de la API de Laravel. A diferencia de una URL fija, el host y el puerto los
 * escribe el usuario en "Conexión con el servidor" (Laravel corre en la PC de la asociación):
 * aquí solo vive la ruta común y cómo se arma la URL base a partir de ellos.
 */
data class ApiConfig(
    /** Prefijo de las rutas móviles de Laravel (`routes/api.php`). Debe terminar en "/". */
    val rutaApi: String = "api/mobile/",
    val conectarTimeoutMs: Long = 5_000,
    /** El sync completo de la primera instalación puede tardar: margen amplio. */
    val solicitudTimeoutMs: Long = 30_000,
) {
    /**
     * URL base "http://HOST:PUERTO/api/mobile/". Quita espacios, un esquema pegado por error
     * (http:// o https://) y barras finales, para que un host copiado del navegador no arme una
     * URL inválida en silencio.
     */
    fun baseUrl(host: String, puerto: String): String {
        val hostLimpio = limpiarHost(host)
        require(hostLimpio.isNotBlank()) { "Ingrese el host / IP del servidor." }
        val puertoNumero = puerto.trim().toIntOrNull()?.takeIf { it in 1..65535 }
            ?: throw IllegalArgumentException("El puerto debe ser un número entre 1 y 65535 (ej. 8000).")
        val scheme = if (host.trim().startsWith("https://")) "https" else "http"
        return "$scheme://$hostLimpio:$puertoNumero/$rutaApi"
    }

    fun limpiarHost(host: String): String = host.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
        .substringBefore("/")
        .substringBefore(":")
}
