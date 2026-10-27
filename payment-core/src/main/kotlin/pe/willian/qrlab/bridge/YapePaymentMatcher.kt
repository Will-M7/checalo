package pe.willian.qrlab.bridge

import pe.willian.qrlab.payment.PaymentIntent
import pe.willian.qrlab.payment.PaymentMethod
import pe.willian.qrlab.payment.PaymentRepository
import pe.willian.qrlab.payment.PaymentStatus
import java.time.Duration
import java.time.Instant

/**
 * Conciliador de pagos Yape por notificación.
 *
 * Solo considera PaymentIntent con:
 * - paymentMethod = YAPE_NOTIFICATION (SYNTHETIC_QR nunca participa),
 * - status = PENDING,
 * - currency compatible (igual),
 * - amountMinor exactamente igual,
 * - createdAt dentro de la ventana [matchWindowSeconds] previa al evento.
 *
 * El nombre del pagador NO se usa para conciliar: es solo información
 * auxiliar para el cajero.
 */
class YapePaymentMatcher(
    private val repository: PaymentRepository,
    private val matchWindowSeconds: Long = 600,
) {

    fun findCandidates(amountMinor: Long, currency: String, now: Instant): List<PaymentIntent> =
        repository.findAll().filter { intent ->
            intent.paymentMethod == PaymentMethod.YAPE_NOTIFICATION &&
                intent.status == PaymentStatus.PENDING &&
                intent.currency == currency &&
                intent.amountMinor == amountMinor &&
                !intent.createdAt.isAfter(now) &&
                Duration.between(intent.createdAt, now).seconds <= matchWindowSeconds
        }.sortedBy { it.createdAt }
}
