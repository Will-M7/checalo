package pe.willian.qrlab.payment.synthetic

import pe.willian.qrlab.money.MoneyFormatter
import pe.willian.qrlab.payment.InMemoryPaymentRepository
import pe.willian.qrlab.payment.PaymentIdempotencyConflictException
import pe.willian.qrlab.payment.PaymentIntent
import pe.willian.qrlab.payment.PaymentMethod
import pe.willian.qrlab.payment.PaymentProvider
import pe.willian.qrlab.payment.PaymentRepository
import pe.willian.qrlab.payment.PaymentStateMachine
import pe.willian.qrlab.payment.PaymentStatus
import pe.willian.qrlab.qr.QrImageWriter
import pe.willian.qrlab.synthetic.SyntheticPaymentQr
import pe.willian.qrlab.synthetic.SyntheticPaymentQrBuilder
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Proveedor sintético local. No es Yape, Entel, LIGO ni ningún proveedor real.
 *
 * Flujo al crear: CREATED -> genera QR (builder sintético) -> PENDING.
 * El QR se guarda en [qrOutputDir]/<id>.png con round-trip ZXing verificado.
 * Opera solo con datos sintéticos de laboratorio, sin leer muestras reales.
 *
 * Idempotencia por referencia (una referencia = una operación comercial):
 * - existente con mismo monto y moneda (cualquier estado) -> se devuelve,
 *   nunca se crea un segundo cobro silenciosamente;
 * - existente con distinto monto o moneda -> [PaymentIdempotencyConflictException].
 */
class SyntheticPaymentProvider(
    private val repository: PaymentRepository = InMemoryPaymentRepository(),
    private val qrOutputDir: Path = Paths.get("output", "payments"),
) : PaymentProvider {

    private val paymentSequence = AtomicLong(1)
    private val lock = Any()

    override fun createPayment(reference: String, amountMinor: Long, currency: String): PaymentIntent {
        val ref = reference.trim()
        require(ref.isNotEmpty()) { "reference no debe estar vacía" }
        require(ref.length <= 64) { "reference demasiado larga (máximo 64 caracteres)" }
        require(amountMinor > 0) { "amountMinor debe ser > 0" }
        require(Regex("^[0-9]{3}$").matches(currency)) { "currency debe ser 3 dígitos" }
        // MoneyFormatter valida el formato amigable usado en el QR (sin Double).
        MoneyFormatter.format(amountMinor)
        synchronized(lock) {
            repository.findByReference(ref)?.let { existing ->
                if (existing.amountMinor == amountMinor && existing.currency == currency) {
                    return existing
                }
                throw PaymentIdempotencyConflictException(
                    "reference \"$ref\" ya existe con monto ${existing.amountMinor} " +
                        "y moneda ${existing.currency}: no se crea un segundo pago",
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
                paymentMethod = PaymentMethod.SYNTHETIC_QR,
            )
            val payload = SyntheticPaymentQrBuilder.build(
                SyntheticPaymentQr(
                    amountMinorUnits = amountMinor,
                    currency = currency,
                ),
            )
            val png = qrOutputDir.resolve("$id.png")
            QrImageWriter.write(payload, png)
            val roundTrip = QrImageWriter.read(png)
            check(roundTrip == payload) { "round-trip ZXing falló para $id" }
            PaymentStateMachine.validate(PaymentStatus.CREATED, PaymentStatus.PENDING)
            val pending = created.copy(status = PaymentStatus.PENDING, qrPayload = payload)
            repository.save(pending)
            return pending
        }
    }

    override fun getPayment(paymentId: String): PaymentIntent? =
        repository.findById(paymentId)

    /**
     * Siguiente ID libre con el prefijo dado. Tras un reinicio el contador
     * en memoria vuelve a 1, así que se comprueba existencia en el
     * repositorio para no colisionar ni reutilizar IDs persistidos.
     */
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
