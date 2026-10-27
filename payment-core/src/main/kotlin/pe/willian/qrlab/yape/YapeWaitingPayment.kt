package pe.willian.qrlab.yape

import java.time.Instant

/** Estados de una venta pendiente (MODELO 2). */
enum class YapeWaitStatus {
    WAITING,
    PAID,
    AMBIGUOUS,
    CANCELLED,
    EXPIRED,
}

/**
 * Venta pendiente de pago Yape (MODELO 2).
 *
 * Pueden existir VARIAS ventas WAITING simultáneas, incluso con el mismo
 * importe (ese es justamente el problema que resuelve el Modelo 2). Si una
 * notificación coincide con UN único candidato -> AUTO MATCH; con 2+
 * candidatos -> AMBIGUOUS y se resuelve explícitamente.
 *
 * Sigue separada del motor de pagos existente (no rompe Payment Engine).
 * Dinero en minor units (Long), nunca Double.
 */
data class YapeWaitingPayment(
    val id: String,
    /** Referencia de venta opcional (p. ej. SALE-001). */
    val reference: String? = null,
    val amountMinor: Long,
    val createdAt: Instant,
    val expiresAt: Instant,
    val status: YapeWaitStatus,
    /** Momento en que una notificación confirmó el pago (solo PAID). */
    val receivedAt: Instant? = null,
    /** packageName Android de la notificación que confirmó el pago. */
    val sourcePackage: String? = null,
    /** Notificación que pagó esta venta (solo PAID). */
    val matchedNotificationId: String? = null,
)
