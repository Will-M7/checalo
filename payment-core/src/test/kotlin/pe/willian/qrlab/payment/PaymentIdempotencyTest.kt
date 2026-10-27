package pe.willian.qrlab.payment

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentProvider
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentSimulator
import java.nio.file.Path

/**
 * Idempotencia con monto/moneda y concurrencia. SQLite temporal por test
 * (nunca data/qr-lab.db). Todo sintético QR LAB.
 */
class PaymentIdempotencyTest {

    private fun provider(
        repository: PaymentRepository,
        temp: Path,
    ): Pair<SyntheticPaymentProvider, SyntheticPaymentSimulator> =
        SyntheticPaymentProvider(repository, temp.resolve("qr")) to
            SyntheticPaymentSimulator(repository)

    @Test
    fun `misma reference mismo monto devuelve mismo ID y QR`(@TempDir temp: Path) {
        SqlitePaymentRepository(temp.resolve("t.db")).use { repo ->
            val (provider, _) = provider(repo, temp)

            val first = provider.createPayment("SALE-001", 700)
            val second = provider.createPayment("SALE-001", 700)

            assertEquals(first.id, second.id)
            assertEquals(first.qrPayload, second.qrPayload)
        }
    }

    @Test
    fun `misma reference distinto monto es conflicto`(@TempDir temp: Path) {
        SqlitePaymentRepository(temp.resolve("t.db")).use { repo ->
            val (provider, _) = provider(repo, temp)
            provider.createPayment("SALE-001", 700)

            val conflict = assertThrows(PaymentIdempotencyConflictException::class.java) {
                provider.createPayment("SALE-001", 5000)
            }
            assertTrue(conflict.message!!.contains("SALE-001"))
        }
    }

    @Test
    fun `misma reference distinta currency es conflicto`(@TempDir temp: Path) {
        SqlitePaymentRepository(temp.resolve("t.db")).use { repo ->
            val (provider, _) = provider(repo, temp)
            provider.createPayment("SALE-001", 700, "604")

            assertThrows(PaymentIdempotencyConflictException::class.java) {
                provider.createPayment("SALE-001", 700, "840")
            }
        }
    }

    @Test
    fun `terminal con misma reference no crea segundo cobro`(@TempDir temp: Path) {
        SqlitePaymentRepository(temp.resolve("t.db")).use { repo ->
            val (provider, _) = provider(repo, temp)
            val cancelled = provider.cancelPayment(provider.createPayment("SALE-001", 700).id)

            // Compatible: devuelve el existente, sin duplicar.
            assertEquals(cancelled.id, provider.createPayment("SALE-001", 700).id)
            // Incompatible: conflicto de dominio.
            assertThrows(PaymentIdempotencyConflictException::class.java) {
                provider.createPayment("SALE-001", 5000)
            }
        }
    }

    @Test
    fun `20 concurrentes misma reference dan un solo pago`(@TempDir temp: Path) = runBlocking {
        SqlitePaymentRepository(temp.resolve("t.db")).use { repo ->
            val (provider, _) = provider(repo, temp)

            val results = (1..20).map {
                async(Dispatchers.IO) { provider.createPayment("SALE-CONCURRENT-001", 700) }
            }.awaitAll()

            assertEquals(1, results.map { it.id }.toSet().size)
            assertEquals(1, repo.count())
        }
    }

    @Test
    fun `20 concurrentes distintas references sin colisión`(@TempDir temp: Path) = runBlocking {
        SqlitePaymentRepository(temp.resolve("t.db")).use { repo ->
            val (provider, _) = provider(repo, temp)

            val results = (1..20).map { n ->
                async(Dispatchers.IO) {
                    provider.createPayment("SALE-CONC-%03d".format(n), 100L + n)
                }
            }.awaitAll()

            assertEquals(20, results.map { it.id }.toSet().size)
            assertEquals(20, repo.count())
        }
    }

    @Test
    fun `reapertura conserva idempotencia y QR`(@TempDir temp: Path) {
        val db = temp.resolve("t.db")
        val firstPayload = SqlitePaymentRepository(db).use { repo ->
            val (provider, _) = provider(repo, temp)
            provider.createPayment("SALE-RE-IDEM", 700).qrPayload
        }

        SqlitePaymentRepository(db).use { reopened ->
            val (provider, _) = provider(reopened, temp)
            val again = provider.createPayment("SALE-RE-IDEM", 700)

            assertEquals("PAY-000001", again.id)
            assertEquals(firstPayload, again.qrPayload)
            assertEquals(1, reopened.count())
        }
    }
}
