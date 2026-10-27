package pe.willian.qrlab.yape

import kotlinx.serialization.Serializable

/** Request de POST /api/yape/wait. Dinero en minor units (Long), nunca Double. */
@Serializable
data class YapeWaitCreateRequest(
    val amountMinor: Long,
    /** Referencia de venta opcional (p. ej. SALE-001). */
    val reference: String? = null,
)

/** Respuesta de POST /api/yape/wait (venta recién creada). */
@Serializable
data class YapeWaitCreatedDto(
    val id: String,
    val amountMinor: Long,
    val amountDisplay: String,
    val status: String,
    val reference: String? = null,
)

/** Vista completa de una venta (current, listado y resolve). */
@Serializable
data class YapeWaitViewDto(
    val id: String,
    val amountMinor: Long,
    val amountDisplay: String,
    val status: String,
    val createdAt: String,
    val expiresAt: String,
    val receivedAt: String? = null,
    val sourcePackage: String? = null,
    val reference: String? = null,
    val matchedNotificationId: String? = null,
)

/**
 * Request LAB ONLY de POST /api/bridge/raw-notification.
 * El contenido completo se procesa en memoria: nunca se persiste raw text.
 */
@Serializable
data class YapeRawNotificationRequest(
    val bridgeEventId: String? = null,
    val packageName: String = "",
    val postedAt: String = "",
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val subText: String? = null,
    val summaryText: String? = null,
    /** Código ya extraído localmente por Android; se convierte a HMAC al recibir. */
    val securityCode: String? = null,
)

@Serializable
data class YapeBridgeAckDto(
    val accepted: Boolean,
    val eventId: String,
    val result: String,
    val notificationId: String? = null,
)

/**
 * Debug RESUMIDO de la última notificación (sin raw text en el navegador):
 * packageName, montos candidatos, origen/estado de match y resultado.
 * NUNCA incluye código de seguridad ni raw text.
 */
@Serializable
data class YapeNotificationDebugDto(
    val packageName: String,
    val detectedAmountsMinor: List<Long> = emptyList(),
    val detectedAmountsDisplay: List<String> = emptyList(),
    val expectedAmountMinor: Long? = null,
    val result: String,
    val receivedAt: String,
    val notificationId: String? = null,
    val sourceType: String? = null,
    val matchStatus: String? = null,
)

/**
 * Caso ambiguo visible en la UI: 2+ ventas WAITING con el mismo importe.
 * No revela nombres ni códigos: solo qué se le puede pedir al cliente.
 */
@Serializable
data class YapeAmbiguousDto(
    val amountMinor: Long,
    val amountDisplay: String,
    /** Ventas WAITING compatibles (candidatas a resolución). */
    val paymentIds: List<String>,
    /** Hay al menos una notificación ambigua con código (HMAC) disponible. */
    val requiresSecurityCode: Boolean,
    /** Hay una notificación ambigua del monto sin código disponible. */
    val securityCodeUnavailable: Boolean = false,
    /** Compatibilidad de DTO histórica; el flujo visible ya no lo utiliza. */
    @Deprecated("YAPE SECURITY CODE MATCH no usa nombres")
    val requiresPayerName: Boolean = false,
    /** Cantidad de notificaciones ambiguas de ese importe. */
    val notificationCount: Int,
)

/**
 * Request de POST /api/yape/waits/{paymentId}/resolve (online-ready).
 * Debe venir UNO de los dos campos (ambos -> también se acepta, se usa el
 * código; ninguno -> 400). El backend SIEMPRE contrasta contra una
 * notificación real ya recibida: nunca crea pagos.
 */
@Serializable
data class YapeResolveRequest(
    val securityCode: String? = null,
    @Deprecated("YAPE SECURITY CODE MATCH no usa nombres")
    val payerName: String? = null,
)

/**
 * Inyección LAB ONLY: simula una notificación estructurada (amount,
 * payerName, securityCode) para probar el MISMO matcher real.
 */
@Serializable
data class YapeLabInjectRequest(
    val amountMinor: Long,
    val payerName: String? = null,
    val securityCode: String? = null,
)

/** Selección manual LAB ONLY de un evento ambiguo (fallback §9). */
@Serializable
data class YapeManualResolveRequest(
    val notificationId: String,
)

/**
 * Vista LAB de una notificación (diagnóstico admin).
 * Muestra payerName normalizado; NUNCA raw text ni código (solo si hay HMAC).
 */
@Serializable
data class YapeNotificationViewDto(
    val id: String,
    val amountMinor: Long,
    val amountDisplay: String,
    val payerName: String? = null,
    val hasSecurityCode: Boolean = false,
    val receivedAt: String,
    val sourcePackage: String,
    val sourceType: String,
    val matchStatus: String,
    val matchedPaymentId: String? = null,
)

/** Estado publicado por SSE (/api/yape/wait/events). */
@Serializable
data class YapeStateDto(
    /** Última venta creada (compatibilidad con la vista simple). */
    val wait: YapeWaitViewDto? = null,
    /** Todas las ventas (tabla de la UI), en orden de creación. */
    val waits: List<YapeWaitViewDto> = emptyList(),
    val lastNotification: YapeNotificationDebugDto? = null,
    /** Caso ambiguo actual (si lo hay) para el panel de resolución. */
    val ambiguous: YapeAmbiguousDto? = null,
)

/** Resultados posibles del match (MODELO 2). */
object YapeMatchResult {
    /** Sin YAPE_ALLOWED_PACKAGE: modo diagnóstico, nunca marca PAID. */
    const val PACKAGE_NOT_CONFIGURED = "PACKAGE_NOT_CONFIGURED"
    /** Package configurado distinto del observado. */
    const val PACKAGE_NOT_ALLOWED = "PACKAGE_NOT_ALLOWED"
    /** No hay venta WAITING candidata (no hubo, cancelada o fuera de ventana). */
    const val NO_ACTIVE_WAIT = "NO_ACTIVE_WAIT"
    /** La venta del importe ya estaba PAID: evento duplicado, no se reprocesa. */
    const val ALREADY_PAID = "ALREADY_PAID"
    /** Ningún campo contenía un monto con forma "S/ x". */
    const val AMOUNT_NOT_FOUND = "AMOUNT_NOT_FOUND"
    /** Hay montos pero ninguno coincide con ninguna venta. */
    const val AMOUNT_MISMATCH = "AMOUNT_MISMATCH"
    /** Exactamente UN candidato: WAITING -> PAID (auto match). */
    const val PAYMENT_MATCHED = "PAYMENT_MATCHED"
    /** 2+ candidatos: NO se marca nada PAID; requiere resolución explícita. */
    const val AMBIGUOUS = "AMBIGUOUS"
}
