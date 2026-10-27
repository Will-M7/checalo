package pe.willian.qrlab.payment.synthetic

import pe.willian.qrlab.payment.PaymentIntent
import pe.willian.qrlab.payment.PaymentRepository
import pe.willian.qrlab.payment.PaymentStateMachine
import pe.willian.qrlab.payment.PaymentStatus
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Simulador SOLO para laboratorio. No existiría en un proveedor real
 * (por eso [pe.willian.qrlab.payment.PaymentProvider] no lo incluye).
 *
 * - confirm: PENDING -> PAID (fija paidAt y referencia sintética LAB-OP-*).
 * - fail: PENDING -> FAILED.
 * - expire: PENDING -> EXPIRED.
 */
class SyntheticPaymentSimulator(
    private val repository: PaymentRepository,
) {

    private val operationSequence = AtomicLong(1)

    /** Confirma un pago PENDING como PAID. */
    fun confirm(paymentId: String): PaymentIntent {
        val current = repository.findById(paymentId)
            ?: throw NoSuchElementException("pago no encontrado: $paymentId")
        check(current.paymentMethod == pe.willian.qrlab.payment.PaymentMethod.SYNTHETIC_QR) { "Synthetic simulator rejects commercial payments" }
        PaymentStateMachine.validate(current.status, PaymentStatus.PAID)
        val paid = current.copy(
            status = PaymentStatus.PAID,
            paidAt = Instant.now(),
            providerOperationReference = operationReferenceFor(current.id),
        )
        repository.save(paid)
        return paid
    }

    /**
     * Referencia sintética determinista desde el propio ID del pago
     * (PAY-000001 -> LAB-OP-000001). Como los IDs nunca se reutilizan
     * (ver [SyntheticPaymentProvider]), tampoco las referencias,
     * incluso después de reinicios.
     */
    private fun operationReferenceFor(paymentId: String): String {
        val number = paymentId.removePrefix("PAY-")
        if (number.matches(Regex("[0-9]+"))) return "LAB-OP-$number"
        return "LAB-OP-%06d".format(operationSequence.getAndIncrement())
    }

    /** Marca un pago PENDING como FAILED. */
    fun fail(paymentId: String): PaymentIntent {
        val current = repository.findById(paymentId)
            ?: throw NoSuchElementException("pago no encontrado: $paymentId")
        check(current.paymentMethod == pe.willian.qrlab.payment.PaymentMethod.SYNTHETIC_QR) { "Synthetic simulator rejects commercial payments" }
        PaymentStateMachine.validate(current.status, PaymentStatus.FAILED)
        val failed = current.copy(status = PaymentStatus.FAILED)
        repository.save(failed)
        return failed
    }

    /** Marca un pago PENDING como EXPIRED. */
    fun expire(paymentId: String): PaymentIntent {
        val current = repository.findById(paymentId)
            ?: throw NoSuchElementException("pago no encontrado: $paymentId")
        check(current.paymentMethod == pe.willian.qrlab.payment.PaymentMethod.SYNTHETIC_QR) { "Synthetic simulator rejects commercial payments" }
        PaymentStateMachine.validate(current.status, PaymentStatus.EXPIRED)
        val expired = current.copy(status = PaymentStatus.EXPIRED)
        repository.save(expired)
        return expired
    }
}
