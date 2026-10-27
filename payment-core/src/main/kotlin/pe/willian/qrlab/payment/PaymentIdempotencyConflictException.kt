package pe.willian.qrlab.payment

/**
 * Conflicto de dominio por idempotencia: la referencia ya existe con
 * monto o moneda diferentes. Extiende [IllegalStateException] para
 * mantener el mapeo HTTP 409 existente, con código de error propio.
 */
class PaymentIdempotencyConflictException(message: String) : IllegalStateException(message)
