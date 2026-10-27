package pe.willian.qrlab.server

import kotlinx.serialization.Serializable

/** Request de creación. Dinero en minor units (Long), nunca Double. */
@Serializable
data class CreatePaymentRequest(
    val reference: String,
    val amountMinor: Long,
    val currency: String = "604",
    /** Opcional: SYNTHETIC_QR (default) o YAPE_NOTIFICATION. */
    val method: String? = null,
)

/** Vista pública de una intención. Nunca expone el objeto interno ni el payload. */
@Serializable
data class PaymentDto(
    val id: String,
    val reference: String,
    val amountMinor: Long,
    val amountDisplay: String,
    val currency: String,
    val status: String,
    val qrUrl: String? = null,
    val createdAt: String,
    val paidAt: String?,
    val providerOperationReference: String?,
    val method: String = "SYNTHETIC_QR",
    /** URL del QR estático del negocio (solo YAPE_NOTIFICATION con archivo configurado). */
    val staticQrUrl: String? = null,
)

/** Error JSON uniforme, sin stack traces. */
@Serializable
data class ErrorDto(
    val error: String,
    val message: String,
)

/** Salud del servidor sintético (sin rutas sensibles). */
@Serializable
data class HealthDto(
    val status: String,
    val mode: String,
    val storage: String = "sqlite",
)

/** Estado mínimo difundido por SSE (sin qrPayload). */
@Serializable
data class PaymentStatusEventDto(
    val id: String,
    val status: String,
)

/** Vista lab de un evento del bridge. Sin código, sin HMAC, sin raw. */
@Serializable
data class BridgeEventDto(
    val id: String,
    val amountMinor: Long? = null,
    val amountDisplay: String? = null,
    val payerName: String? = null,
    val receivedAt: String,
    val matchStatus: String,
    val matchedPaymentId: String? = null,
)

/**
 * Inyección LAB ONLY: simula una notificación normalizada sin Android.
 * Pasa por el mismo matcher que las notificaciones reales.
 */
@Serializable
data class LabBridgeInjectRequest(
    val amountMinor: Long,
    val payerName: String? = null,
    val securityCode: String? = null,
)

/** Resolución por código de seguridad de 3 dígitos (del comprobante del cliente). */
@Serializable
data class ResolveYapeRequest(
    val securityCode: String,
)
