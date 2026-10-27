package pe.willian.yapebridge.model

/**
 * Notificación capturada localmente con fines de diagnóstico.
 *
 * Fase 1: modelo genérico, sin interpretación de pagos.
 * No incluye imágenes ni iconos.
 */
data class CapturedNotification(
    val packageName: String,
    val notificationKey: String?,
    val postedAt: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val summaryText: String?,
    val textLines: List<String> = emptyList(),
    val extras: Map<String, String> = emptyMap(),
    val securityCode: String? = null,
)
