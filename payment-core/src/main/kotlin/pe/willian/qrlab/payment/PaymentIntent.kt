package pe.willian.qrlab.payment

import java.time.Instant

/**
 * Intención de pago del laboratorio. Inmutable: los cambios de estado
 * se expresan con `copy` y se validan con [PaymentStateMachine].
 *
 * El QR deja de ser la entidad principal: pertenece a la intención
 * ([qrPayload]). Dinero en minor units ([amountMinor]), nunca Double.
 */
data class PaymentIntent(
    val id: String,
    val reference: String,
    val amountMinor: Long,
    val currency: String,
    val status: PaymentStatus,
    val qrPayload: String?,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val paidAt: Instant?,
    val providerOperationReference: String?,
    /** Método de pago. Default compatible: registros históricos son sintéticos. */
    val paymentMethod: PaymentMethod = PaymentMethod.SYNTHETIC_QR,
)
