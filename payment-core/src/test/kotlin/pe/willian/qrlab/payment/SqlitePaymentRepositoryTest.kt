package pe.willian.qrlab.payment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentProvider
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentSimulator
import java.nio.file.Path
import java.time.Instant

/**
 * SQLite temporal por test (nunca data/qr-lab.db).
 * Solo pagos sintéticos QR LAB.
 */
class SqlitePaymentRepositoryTest {

    private fun intent(id: String, reference: String, status: PaymentStatus) = PaymentIntent(
        id = id,
        reference = reference,
        amountMinor = 700,
        currency = "604",
        status = status,
        qrPayload = "qr-$id",
        createdAt = Instant.parse("2026-09-21T00:00:00Z"),
        expiresAt = null,
        paidAt = null,
        providerOperationReference = null,
    )

    @Test
    fun `save y findById`(@TempDir temp: Path) {
        SqlitePaymentRepository(temp.resolve("t.db")).use { repo ->
            repo.save(intent("PAY-000001", "R-1", PaymentStatus.PENDING))

            val found = repo.findById("PAY-000001")
            assertEquals("R-1", found?.reference)
            assertEquals(700, found?.amountMinor)
            assertEquals(PaymentStatus.PENDING, found?.status)
        }
    }

    @Test
    fun `findByReference`(@TempDir temp: Path) {
        SqlitePaymentRepository(temp.resolve("t.db")).use { repo ->
            repo.save(intent("PAY-000001", "R-2", PaymentStatus.PENDING))

            assertEquals("PAY-000001", repo.findByReference("R-2")?.id)
            assertEquals(null, repo.findByReference("R-OTRA"))
        }
    }

    @Test
    fun `update PENDING a PAID persiste paidAt y referencia`(@TempDir temp: Path) {
        SqlitePaymentRepository(temp.resolve("t.db")).use { repo ->
            repo.save(intent("PAY-000001", "R-3", PaymentStatus.PENDING))
            val paid = intent("PAY-000001", "R-3", PaymentStatus.PENDING).copy(
                status = PaymentStatus.PAID,
                paidAt = Instant.parse("2026-09-21T01:00:00Z"),
                providerOperationReference = "LAB-OP-000001",
            )
            repo.save(paid)

            val found = repo.findById("PAY-000001")
            assertEquals(PaymentStatus.PAID, found?.status)
            assertEquals(Instant.parse("2026-09-21T01:00:00Z"), found?.paidAt)
            assertEquals("LAB-OP-000001", found?.providerOperationReference)
        }
    }

    @Test
    fun `transición inválida rechazada`(@TempDir temp: Path) {
        SqlitePaymentRepository(temp.resolve("t.db")).use { repo ->
            repo.save(intent("PAY-000001", "R-4", PaymentStatus.PAID))

            assertThrows(IllegalStateException::class.java) {
                repo.save(intent("PAY-000001", "R-4", PaymentStatus.PAID).copy(status = PaymentStatus.PENDING))
            }
        }
    }

    @Test
    fun `reapertura conserva datos y dos pagos`(@TempDir temp: Path) {
        val db = temp.resolve("t.db")
        SqlitePaymentRepository(db).use { repo ->
            repo.save(intent("PAY-000001", "R-5", PaymentStatus.PENDING))
            repo.save(intent("PAY-000002", "R-6", PaymentStatus.PAID))
        }

        SqlitePaymentRepository(db).use { reopened ->
            val first = reopened.findById("PAY-000001")
            assertEquals("R-5", first?.reference)
            assertEquals(700, first?.amountMinor)
            assertEquals(PaymentStatus.PENDING, first?.status)
            assertEquals(PaymentStatus.PAID, reopened.findById("PAY-000002")?.status)
        }
    }

    @Test
    fun `IDs sin colisión e idempotencia tras reapertura`(@TempDir temp: Path) {
        val db = temp.resolve("t.db")
        val qrDir = temp.resolve("qr")
        SqlitePaymentRepository(db).use { repo ->
            val provider = SyntheticPaymentProvider(repo, qrDir)
            provider.createPayment("SALE-RE", 700)
        }

        SqlitePaymentRepository(db).use { reopened ->
            val provider = SyntheticPaymentProvider(reopened, qrDir)
            // Misma reference PENDING -> existente, sin duplicar.
            assertEquals("PAY-000001", provider.createPayment("SALE-RE", 700).id)
            // Nueva reference -> siguiente libre, sin colisionar.
            val second = provider.createPayment("SALE-RE-2", 500)
            assertEquals("PAY-000002", second.id)
            assertTrue(reopened.findById("PAY-000001") != null)
        }
    }

    @Test
    fun `LAB-OP determinista no se reutiliza tras reapertura`(@TempDir temp: Path) {
        val db = temp.resolve("t.db")
        val qrDir = temp.resolve("qr")
        SqlitePaymentRepository(db).use { repo ->
            val provider = SyntheticPaymentProvider(repo, qrDir)
            SyntheticPaymentSimulator(repo).confirm(provider.createPayment("SALE-OP", 700).id)
        }

        SqlitePaymentRepository(db).use { reopened ->
            val provider = SyntheticPaymentProvider(reopened, qrDir)
            val simulator = SyntheticPaymentSimulator(reopened)
            val second = simulator.confirm(provider.createPayment("SALE-OP-2", 500).id)
            assertEquals("LAB-OP-000002", second.providerOperationReference)
            assertEquals("LAB-OP-000001", reopened.findById("PAY-000001")?.providerOperationReference)
        }
    }
}
