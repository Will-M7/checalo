package pe.willian.qrlab.yape

/**
 * Configuración del Modelo 2, siempre desde el entorno.
 *
 * - YAPE_ALLOWED_PACKAGE: package exacto de Android aceptado para matching.
 *   Si NO está definida el servicio entra en MODO DIAGNÓSTICO: muestra el
 *   packageName observado pero NUNCA marca PAID automáticamente
 *   (resultado PACKAGE_NOT_CONFIGURED).
 * - YAPE_MATCH_SECRET: secreto del HMAC-SHA256 del código de 3 dígitos.
 *   El secreto NUNCA se persiste ni se loguea; los tests lo fijan.
 * - YAPE_MATCH_WINDOW_SECONDS: ventana de match venta<->notificación
 *   (default 600, igual que el bridge).
 *
 * Defaults de laboratorio solamente; en producción se exportan explícitos.
 */
object YapeModelConfig {

    /** Package permitido o null en modo diagnóstico. */
    fun allowedPackageFromEnv(env: Map<String, String> = System.getenv()): String? =
        env["YAPE_ALLOWED_PACKAGE"]?.takeIf { it.isNotBlank() }

    /** Secreto HMAC del código de seguridad (default de laboratorio). */
    fun matchSecretFromEnv(env: Map<String, String> = System.getenv()): String =
        env["YAPE_MATCH_SECRET"]?.takeIf { it.isNotBlank() } ?: "lab-match-secret"

    /** Ventana de match en segundos (default 600). */
    fun matchWindowSecondsFromEnv(env: Map<String, String> = System.getenv()): Long =
        env["YAPE_MATCH_WINDOW_SECONDS"]?.toLongOrNull()?.takeIf { it > 0 } ?: 600L

    fun waitTtlSecondsFromEnv(env: Map<String, String> = System.getenv()): Long =
        env["YAPE_WAIT_TTL_SECONDS"]?.toLongOrNull()?.takeIf { it > 0 } ?: 300L
}
