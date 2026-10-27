package pe.willian.qrlab.payment.yape

import pe.willian.qrlab.money.MoneyFormatter
import pe.willian.qrlab.payment.PaymentIdempotencyConflictException
import pe.willian.qrlab.payment.PaymentIntent
import pe.willian.qrlab.payment.PaymentMethod
import pe.willian.qrlab.payment.PaymentProvider
import pe.willian.qrlab.payment.PaymentRepository
import pe.willian.qrlab.payment.PaymentStateMachine
import pe.willian.qrlab.payment.PaymentStatus
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Proveedor receptor de notificaciones Yape (LAB).
 *
 * Al crear un pago:
 * - NO genera QR dinámico ni payload EMV;
 * - NO contacta a Yape ni a ningún servicio externo;
 * - crea un [PaymentIntent] PENDING con paymentMethod = YAPE_NOTIFICATION,
 *   que luego el matcher podrá conciliar cuando llegue la notificación
 *   reenviada por el teléfono del negocio.
 *
 * Conserva idempotencia por referencia, state machine, concurrencia
 * e IDs con el mismo formato existente (PAY-000001, ...).
 */
class YapeNotificationPaymentProvider(
    private val repository: PaymentRepository,
) : PaymentProvider {

    private val paymentSequence = AtomicLong(1)
    private val lock = Any()

    override fun createPayment(reference: String, amountMinor: Long, currency: String): PaymentIntent {
        val ref = reference.trim()
        require(ref.isNotEmpty()) { "reference no debe estar vacía" }
        require(ref.length <= 64) { "reference demasiado larga (máximo 64 caracteres)" }
        require(amountMinor > 0) { "amountMinor debe ser > 0" }
        require(Regex("^[0-9]{3}$").matches(currency)) { "currency debe ser 3 dígitos" }
        MoneyFormatter.format(amountMinor)
        synchronized(lock) {
            repository.findByReference(ref)?.let { existing ->
                if (existing.amountMinor == amountMinor && existing.currency == currency &&
                    existing.paymentMethod == PaymentMethod.YAPE_NOTIFICATION
                ) {
                    return existing
                }
                throw PaymentIdempotencyConflictException(
                    "reference \"$ref\" ya existe con monto ${existing.amountMinor}, " +
                        "moneda ${existing.currency} y método ${existing.paymentMethod}: " +
                        "no se crea un segundo pago",
                )
            }
            val id = nextFreeId("PAY")
            val created = PaymentIntent(
                id = id,
                reference = ref,
                amountMinor = amountMinor,
                currency = currency,
                status = PaymentStatus.CREATED,
                qrPayload = null,
                createdAt = Instant.now(),
                expiresAt = null,
                paidAt = null,
                providerOperationReference = null,
                paymentMethod = PaymentMethod.YAPE_NOTIFICATION,
            )
            PaymentStateMachine.validate(PaymentStatus.CREATED, PaymentStatus.PENDING)
            val pending = created.copy(status = PaymentStatus.PENDING)
            repository.save(pending)
            return pending
        }
    }

    override fun getPayment(paymentId: String): PaymentIntent? =
        repository.findById(paymentId)

    private fun nextFreeId(prefix: String): String {
        while (true) {
            val id = "$prefix-%06d".format(paymentSequence.getAndIncrement())
            if (repository.findById(id) == null) return id
        }
    }

    override fun cancelPayment(paymentId: String): PaymentIntent {
        val current = repository.findById(paymentId)
            ?: throw NoSuchElementException("pago no encontrado: $paymentId")
        PaymentStateMachine.validate(current.status, PaymentStatus.CANCELLED)
        val cancelled = current.copy(status = PaymentStatus.CANCELLED)
        repository.save(cancelled)
        return cancelled
    }
}
