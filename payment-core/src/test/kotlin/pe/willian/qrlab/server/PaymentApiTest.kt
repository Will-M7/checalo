package pe.willian.qrlab.server

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pe.willian.qrlab.payment.InMemoryPaymentRepository
import pe.willian.qrlab.payment.PaymentRepository
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentProvider
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentSimulator
import pe.willian.qrlab.qr.QrImageWriter
import pe.willian.qrlab.qr.QrPayloadAnalyzer
import java.nio.file.Files
import java.nio.file.Path

/**
 * API HTTP sintética con testApplication. Sin proveedores reales,
 * sin samples/private/, sin red externa.
 */
class PaymentApiTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun testService(qrDir: Path): PaymentService {
        val repository: PaymentRepository = InMemoryPaymentRepository()
        return PaymentService(
            provider = SyntheticPaymentProvider(repository, qrDir),
            repository = repository,
            simulator = SyntheticPaymentSimulator(repository),
            eventBus = PaymentEventBus(),
            qrDir = qrDir,
        )
    }

    // Nota: `client` se resuelve dentro de testApplication; los helpers lo reciben como receptor.
    // Para mantenerlo simple cada test usa el cliente directamente.

    @Test
    fun `health responde ok sintético`() = testApplication {
        application { paymentModule(testService(Files.createTempDirectory("qr"))) }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        val health = json.decodeFromString<HealthDto>(response.bodyAsText())
        assertEquals("ok", health.status)
        assertEquals("synthetic-lab", health.mode)
    }

    @Test
    fun `demo sirve HTML del laboratorio`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp)) }

        val response = client.get("/demo")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()?.match(ContentType.Text.Html) == true)
        val html = response.bodyAsText()
        assertTrue(html.contains("QR LAB"))
        assertTrue(html.contains("SYNTHETIC PAYMENTS ONLY"))
    }

    @Test
    fun `recursos estáticos CSS y JS se sirven`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp)) }

        val css = client.get("/static/payment-lab.css")
        assertEquals(HttpStatusCode.OK, css.status)
        assertTrue(css.contentType()?.match(ContentType.Text.CSS) == true)

        val js = client.get("/static/payment-lab.js")
        assertEquals(HttpStatusCode.OK, js.status)
        val script = js.bodyAsText()
        assertTrue(script.contains("parseMoneyToMinorUnits"))
        assertTrue(script.contains("EventSource"))
    }

    @Test
    fun `raíz redirige a demo`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp)) }

        val response = createClient { followRedirects = false }.get("/")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/demo", response.headers["Location"])
    }

    @Test
    fun `crear pago responde PENDING`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp)) }

        val response = client.post("/api/payments") {
            contentType(ContentType.Application.Json)
            setBody("""{"reference":"SALE-001","amountMinor":700,"currency":"604"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val dto = json.decodeFromString<PaymentDto>(response.bodyAsText())
        assertEquals("PENDING", dto.status)
        assertEquals(700, dto.amountMinor)
        assertEquals("S/ 7.00", dto.amountDisplay)
        assertEquals("/api/payments/${dto.id}/qr", dto.qrUrl)
    }

    @Test
    fun `monto cero responde 400`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp)) }

        val response = client.post("/api/payments") {
            contentType(ContentType.Application.Json)
            setBody("""{"reference":"SALE-002","amountMinor":0,"currency":"604"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `referencia vacía responde 400`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp)) }

        val response = client.post("/api/payments") {
            contentType(ContentType.Application.Json)
            setBody("""{"reference":"","amountMinor":700,"currency":"604"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `consultar pago existente responde 200`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp)) }
        val created = json.decodeFromString<PaymentDto>(client.createDirect("SALE-003", 500))

        val response = client.get("/api/payments/${created.id}")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(created.id, json.decodeFromString<PaymentDto>(response.bodyAsText()).id)
    }

    @Test
    fun `pago inexistente responde 404`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp)) }

        assertEquals(HttpStatusCode.NotFound, client.get("/api/payments/PAY-999999").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/payments/PAY-999999/qr").status)
    }

    @Test
    fun `QR responde image-png legible por ZXing`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp)) }
        val created = json.decodeFromString<PaymentDto>(client.createDirect("SALE-004", 700))

        val response = client.get("/api/payments/${created.id}/qr")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()?.match(ContentType.Image.PNG) == true)
        val png = temp.resolve("leido.png")
        Files.write(png, response.body<ByteArray>())
        val payload = QrImageWriter.read(png)
        val analysis = QrPayloadAnalyzer.analyze(payload)
        assertTrue(analysis.crcValid == true)
        assertEquals("700", analysis.fields.first { it.tag == "54" }.value)
    }

    @Test
    fun `confirmar LAB pasa a PAID con paidAt y LAB-OP`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp)) }
        val created = json.decodeFromString<PaymentDto>(client.createDirect("SALE-005", 700))

        val response = client.post("/api/lab/payments/${created.id}/confirm")

        assertEquals(HttpStatusCode.OK, response.status)
        val paid = json.decodeFromString<PaymentDto>(response.bodyAsText())
        assertEquals("PAID", paid.status)
        assertTrue(paid.paidAt != null)
        assertTrue(paid.providerOperationReference?.startsWith("LAB-OP-") == true)
    }

    @Test
    fun `cancelar PENDING responde CANCELLED`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp)) }
        val created = json.decodeFromString<PaymentDto>(client.createDirect("SALE-006", 500))

        val response = client.post("/api/payments/${created.id}/cancel")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("CANCELLED", json.decodeFromString<PaymentDto>(response.bodyAsText()).status)
    }

    @Test
    fun `cancelar PAID responde 409`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp)) }
        val created = json.decodeFromString<PaymentDto>(client.createDirect("SALE-007", 500))
        client.post("/api/lab/payments/${created.id}/confirm")

        assertEquals(HttpStatusCode.Conflict, client.post("/api/payments/${created.id}/cancel").status)
    }

    @Test
    fun `confirmar CANCELLED responde 409`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp)) }
        val created = json.decodeFromString<PaymentDto>(client.createDirect("SALE-008", 500))
        client.post("/api/payments/${created.id}/cancel")

        assertEquals(HttpStatusCode.Conflict, client.post("/api/lab/payments/${created.id}/confirm").status)
    }

    @Test
    fun `confirmar EXPIRED responde 409`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp)) }
        val created = json.decodeFromString<PaymentDto>(client.createDirect("SALE-009", 500))
        client.post("/api/lab/payments/${created.id}/expire")

        assertEquals(HttpStatusCode.Conflict, client.post("/api/lab/payments/${created.id}/confirm").status)
    }

    @Test
    fun `misma reference devuelve el mismo pago`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp)) }
        val first = json.decodeFromString<PaymentDto>(client.createDirect("SALE-DUP", 700))
        val second = json.decodeFromString<PaymentDto>(client.createDirect("SALE-DUP", 700))

        assertEquals(first.id, second.id)
    }

    @Test
    fun `misma reference distinto monto responde 409 sin stack`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp)) }
        client.createDirect("SALE-CONF", 700)

        val response = client.post("/api/payments") {
            contentType(ContentType.Application.Json)
            setBody("""{"reference":"SALE-CONF","amountMinor":5000,"currency":"604"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val error = json.decodeFromString<ErrorDto>(response.bodyAsText())
        assertEquals("payment_idempotency_conflict", error.error)
        assertTrue(error.message.contains("SALE-CONF"))
    }

    @Test
    fun `references distintas generan IDs distintos`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp)) }
        val a = json.decodeFromString<PaymentDto>(client.createDirect("SALE-A", 500))
        val b = json.decodeFromString<PaymentDto>(client.createDirect("SALE-B", 700))

        assertTrue(a.id != b.id)
    }

    @Test
    fun `SSE en pago inexistente responde 404`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp)) }

        val response = client.get("/api/payments/PAY-999999/events")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `events del servicio emite PAID tras confirmar`(@TempDir temp: Path) = runBlocking {
        val service = testService(temp)
        val created = service.create("SALE-EV", 700, "604")

        val received = async {
            withTimeout(5_000) { service.events(created.id)!!.first() }
        }
        delay(200)
        service.labConfirm(created.id)

        assertEquals("PAID", received.await().status)
    }

    // Helper con cliente implícito: se usa solo dentro de testApplication.
    private suspend fun io.ktor.client.HttpClient.createDirect(reference: String, amount: Long): String {
        val response = post("/api/payments") {
            contentType(ContentType.Application.Json)
            setBody("""{"reference":"$reference","amountMinor":$amount,"currency":"604"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.bodyAsText()
    }

}
