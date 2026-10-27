package pe.willian.qrlab.payment

/**
 * Máquina de estados explícita de intenciones de pago.
 *
 * Permitido:
 * - CREATED -> PENDING
 * - PENDING -> PAID / FAILED / EXPIRED / CANCELLED
 *
 * Todo lo demás (p. ej. PAID -> PENDING/CANCELLED/FAILED,
 * EXPIRED -> PAID, CANCELLED -> PAID) es inválido y falla claramente
 * con [IllegalStateException].
 */
object PaymentStateMachine {

    private val allowed: Map<PaymentStatus, Set<PaymentStatus>> = mapOf(
        PaymentStatus.CREATED to setOf(PaymentStatus.PENDING),
        PaymentStatus.PENDING to setOf(
            PaymentStatus.PAID,
            PaymentStatus.FAILED,
            PaymentStatus.EXPIRED,
            PaymentStatus.CANCELLED,
        ),
    )

    /** true si la transición [from] -> [to] está permitida. */
    fun canTransition(from: PaymentStatus, to: PaymentStatus): Boolean =
        allowed[from]?.contains(to) == true

    /** Valida la transición o lanza [IllegalStateException] con mensaje explícito. */
    fun validate(from: PaymentStatus, to: PaymentStatus) {
        if (!canTransition(from, to)) {
            throw IllegalStateException("transición de pago inválida: $from -> $to")
        }
    }
}
