package pe.willian.qrlab.payment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentProvider
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentSimulator
import pe.willian.qrlab.qr.QrImageWriter
import pe.willian.qrlab.qr.QrPayloadAnalyzer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Payment engine sintético. Todo ficticio (QR LAB, PAY-*, LAB-OP-*).
 * Sin fixtures reales y sin acceso a samples/private/.
 */
class PaymentEngineTest {

    private fun engine(temp: Path): Triple<InMemoryPaymentRepository, SyntheticPaymentProvider, SyntheticPaymentSimulator> {
        val repository = InMemoryPaymentRepository()
        return Triple(repository, SyntheticPaymentProvider(repository, temp), SyntheticPaymentSimulator(repository))
    }

    @Test
    fun `creacion genera PENDING con QR amount y currency`(@TempDir temp: Path) {
        val (_, provider, _) = engine(temp)

        val intent = provider.createPayment("SALE-001", 700)

        assertEquals(PaymentStatus.PENDING, intent.status)
        assertEquals(700, intent.amountMinor)
        assertEquals("604", intent.currency)
        assertNotNull(intent.qrPayload)
        assertTrue(QrPayloadAnalyzer.analyze(intent.qrPayload!!).crcValid == true)
        assertTrue(Files.exists(temp.resolve("${intent.id}.png")))
        assertEquals(intent.qrPayload, QrImageWriter.read(temp.resolve("${intent.id}.png")))
    }

    @Test
    fun `confirmacion pasa a PAID con paidAt y referencia sintetica`(@TempDir temp: Path) {
        val (_, provider, simulator) = engine(temp)
        val created = provider.createPayment("SALE-002", 500)

        val paid = simulator.confirm(created.id)

        assertEquals(PaymentStatus.PAID, paid.status)
        assertNotNull(paid.paidAt)
        assertTrue(paid.providerOperationReference?.startsWith("LAB-OP-") == true)
    }

    @Test
    fun `PENDING admite CANCELLED EXPIRED FAILED`(@TempDir temp: Path) {
        val (_, provider, simulator) = engine(temp)

        assertEquals(PaymentStatus.CANCELLED, provider.cancelPayment(provider.createPayment("R-C", 100).id).status)
        assertEquals(PaymentStatus.EXPIRED, simulator.expire(provider.createPayment("R-E", 100).id).status)
        assertEquals(PaymentStatus.FAILED, simulator.fail(provider.createPayment("R-F", 100).id).status)
    }

    @Test
    fun `PAID rechaza CANCELLED y PENDING`(@TempDir temp: Path) {
        val (repository, provider, simulator) = engine(temp)
        val paid = simulator.confirm(provider.createPayment("R-P", 100).id)

        assertThrows(IllegalStateException::class.java) { provider.cancelPayment(paid.id) }
        assertThrows(IllegalStateException::class.java) {
            PaymentStateMachine.validate(PaymentStatus.PAID, PaymentStatus.PENDING)
        }
        assertThrows(IllegalStateException::class.java) {
            repository.save(paid.copy(status = PaymentStatus.PENDING))
        }
    }

    @Test
    fun `EXPIRED y CANCELLED rechazan PAID`(@TempDir temp: Path) {
        val (_, provider, simulator) = engine(temp)
        val expired = simulator.expire(provider.createPayment("R-X", 100).id)
        val cancelled = provider.cancelPayment(provider.createPayment("R-Z", 100).id)

        assertThrows(IllegalStateException::class.java) {
            PaymentStateMachine.validate(expired.status, PaymentStatus.PAID)
        }
        assertThrows(IllegalStateException::class.java) {
            PaymentStateMachine.validate(cancelled.status, PaymentStatus.PAID)
        }
    }

    @Test
    fun `reference repetida devuelve el pago existente`(@TempDir temp: Path) {
        val (_, provider, _) = engine(temp)

        val first = provider.createPayment("SALE-DUP", 700)
        val second = provider.createPayment("SALE-DUP", 700)

        assertEquals(first.id, second.id)
        assertEquals("PAY-000001", first.id)
    }

    @Test
    fun `references distintas generan pagos distintos`(@TempDir temp: Path) {
        val (_, provider, _) = engine(temp)

        val a = provider.createPayment("SALE-A", 500)
        val b = provider.createPayment("SALE-B", 700)

        assertNotEquals(a.id, b.id)
        assertNull(provider.getPayment("PAY-999999"))
        assertEquals(a, provider.getPayment(a.id))
    }

    @Test
    fun `ningun acceso a samples-private desde el engine`() {
        val roots = listOf("payment", "synthetic", "money").map {
            Paths.get("src", "main", "kotlin", "pe", "willian", "qrlab", it)
        }
        val offenders = roots.flatMap { root ->
            Files.walk(root).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                    .filter { Files.readString(it).contains("samples/private") }
                    .map { it.toString() }.toList()
            }
        }
        assertTrue(offenders.isEmpty(), "acceso a samples/private en: $offenders")
    }
}
