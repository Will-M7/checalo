package pe.willian.qrlab.payment

/**
 * Abstracción del proveedor de pagos. Completamente genérica:
 * no menciona QR, simulación ni ningún proveedor real.
 */
interface PaymentProvider {

    /** Crea (o recupera por idempotencia) una intención de pago. */
    fun createPayment(
        reference: String,
        amountMinor: Long,
        currency: String = "604",
    ): PaymentIntent

    /** Recupera una intención por id, o null si no existe. */
    fun getPayment(paymentId: String): PaymentIntent?

    /** Cancela una intención PENDING. Falla en cualquier otro estado. */
    fun cancelPayment(paymentId: String): PaymentIntent
}
