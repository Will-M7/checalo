package pe.willian.qrlab.payment

/**
 * Estados de una intención de pago del laboratorio.
 *
 * Sin Boolean tipo `paid`; el estado es siempre explícito.
 */
enum class PaymentStatus {
    CREATED,
    PENDING,
    PAID,
    EXPIRED,
    CANCELLED,
    FAILED,
}
