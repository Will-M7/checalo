package pe.willian.qrlab.server

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import pe.willian.qrlab.payment.PaymentStatus

/** Cambio de estado difundido a suscriptores. */
data class PaymentStatusEvent(
    val id: String,
    val status: PaymentStatus,
)

/**
 * Bus de eventos en memoria con Coroutines/Flow.
 * Sin Kafka, RabbitMQ ni infraestructura externa.
 */
class PaymentEventBus {

    private val mutable = MutableSharedFlow<PaymentStatusEvent>(extraBufferCapacity = 64)
    private val events = mutable.asSharedFlow()

    /** Publica un cambio de estado. */
    suspend fun publish(event: PaymentStatusEvent) {
        mutable.emit(event)
    }

    /** Flujo de cambios de un pago concreto. */
    fun forPayment(paymentId: String): Flow<PaymentStatusEvent> =
        events.filter { it.id == paymentId }
}
