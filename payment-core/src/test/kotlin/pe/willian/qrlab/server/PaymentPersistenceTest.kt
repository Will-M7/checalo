package pe.willian.qrlab.server

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pe.willian.qrlab.payment.InMemoryPaymentRepository
import pe.willian.qrlab.payment.PaymentRepository
import pe.willian.qrlab.payment.SqlitePaymentRepository
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentProvider
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentSimulator
import java.nio.file.Path

/**
 * Reinicio lógico con SQLite temporal (nunca data/qr-lab.db):
 * service A crea, se cierra, service B recupera intacto.
 */
class PaymentPersistenceTest {

    private fun service(
        repository: PaymentRepository,
        qrDir: Path,
        storage: String,
    ): PaymentService = PaymentService(
        provider = SyntheticPaymentProvider(repository, qrDir),
        repository = repository,
        simulator = SyntheticPaymentSimulator(repository),
        eventBus = PaymentEventBus(),
        qrDir = qrDir,
        storageName = storage,
    )

    @Test
    fun `reapertura conserva pago y reporta sqlite`(@TempDir temp: Path) = runBlocking {
        val db = temp.resolve("lab.db")
        val qrDir = temp.resolve("qr")
        val created = SqlitePaymentRepository(db).use { repo ->
            service(repo, qrDir, "sqlite").create("SALE-RESTART", 700, "604")
        }

        SqlitePaymentRepository(db).use { reopened ->
            val recovered = service(reopened, qrDir, "sqlite").get(created.id)
            assertEquals("SALE-RESTART", recovered?.reference)
            assertEquals(700, recovered?.amountMinor)
            assertEquals("PENDING", recovered?.status)
            assertEquals("sqlite", service(reopened, qrDir, "sqlite").health().storage)
        }
    }

    @Test
    fun `servicio en memoria reporta memory`(@TempDir temp: Path) {
        val repository: PaymentRepository = InMemoryPaymentRepository()

        val service = PaymentService(
            provider = SyntheticPaymentProvider(repository, temp),
            repository = repository,
            simulator = SyntheticPaymentSimulator(repository),
            eventBus = PaymentEventBus(),
            qrDir = temp,
            storageName = "memory",
        )

        assertEquals("memory", service.health().storage)
    }

    @Test
    fun `SSE tras reinicio emite PENDING persistido`(@TempDir temp: Path) = runBlocking {
        val db = temp.resolve("lab.db")
        val qrDir = temp.resolve("qr")
        val id = SqlitePaymentRepository(db).use { repo ->
            service(repo, qrDir, "sqlite").create("SALE-SSE-RE", 700, "604").id
        }

        SqlitePaymentRepository(db).use { reopened ->
            val serviceB = service(reopened, qrDir, "sqlite")
            // Lo que SSE emite al conectar: estado persistido.
            assertEquals("PENDING", serviceB.get(id)?.status)
            // Y los cambios futuros siguen fluyendo tras el reinicio.
            val received = async {
                withTimeout(5_000) { serviceB.events(id)!!.first() }
            }
            delay(200)
            serviceB.labConfirm(id)
            assertEquals("PAID", received.await().status)
        }
    }

    @Test
    fun `SSE tras reinicio emite PAID persistido`(@TempDir temp: Path) = runBlocking {
        val db = temp.resolve("lab.db")
        val qrDir = temp.resolve("qr")
        val id = SqlitePaymentRepository(db).use { repo ->
            val serviceA = service(repo, qrDir, "sqlite")
            val created = serviceA.create("SALE-SSE-PAID", 700, "604")
            serviceA.labConfirm(created.id).id
        }

        SqlitePaymentRepository(db).use { reopened ->
            // Lo que SSE emite al conectar: PAID persistido.
            assertEquals("PAID", service(reopened, qrDir, "sqlite").get(id)?.status)
        }
    }
}
