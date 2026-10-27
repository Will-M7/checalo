package pe.willian.qrlab.server

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pe.willian.qrlab.payment.PaymentStatus

/** EventBus en memoria: publica y filtra por pago, sin infraestructura externa. */
class PaymentEventBusTest {

    @Test
    fun `publica y recibe cambios del pago`() = runBlocking {
        val bus = PaymentEventBus()

        launch { bus.publish(PaymentStatusEvent("PAY-000001", PaymentStatus.PAID)) }
        val event = withTimeout(5_000) { bus.forPayment("PAY-000001").first() }

        assertEquals("PAY-000001", event.id)
        assertEquals(PaymentStatus.PAID, event.status)
    }
}
