package pe.willian.qrlab.server

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pe.willian.qrlab.payment.InMemoryPaymentRepository
import pe.willian.qrlab.payment.PaymentRepository
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentProvider
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentSimulator
import pe.willian.qrlab.yape.YapeNotificationViewDto
import pe.willian.qrlab.yape.YapeWaitCreatedDto
import pe.willian.qrlab.yape.YapeWaitService
import pe.willian.qrlab.yape.YapeWaitViewDto
import java.nio.file.Path

/**
 * API HTTP del MODELO 2 (múltiples ventas + resolución ambigua).
 * testApplication, sin Android, sin datos privados, sin red externa.
 */
class YapeApiModel2Test {

    private val json = Json { ignoreUnknownKeys = true }
    private val allowedPackage = "pe.willian.qrlab.fakeyape"

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

    private fun yapeService() = YapeWaitService(
        allowedPackage = allowedPackage,
        matchSecret = "test-secret",
        matchWindowSeconds = 600,
    )

    /** Crea dos ventas de S/7.00 y deja una notificación ambigua con código. */
    private suspend fun io.ktor.client.HttpClient.setupAmbiguous(): Pair<YapeWaitCreatedDto, YapeWaitCreatedDto> {
        val w1 = json.decodeFromString<YapeWaitCreatedDto>(
            post("/api/yape/wait") {
                contentType(ContentType.Application.Json)
                setBody("""{"amountMinor": 700}""")
            }.bodyAsText(),
        )
        val w2 = json.decodeFromString<YapeWaitCreatedDto>(
            post("/api/yape/wait") {
                contentType(ContentType.Application.Json)
                setBody("""{"amountMinor": 700, "reference": "SALE-002"}""")
            }.bodyAsText(),
        )
        val inject = post("/api/lab/yape/inject") {
            contentType(ContentType.Application.Json)
            setBody("""{"amountMinor": 700, "payerName": "Persona Uno", "securityCode": "381"}""")
        }
        assertEquals(HttpStatusCode.Accepted, inject.status)
        assertTrue(inject.bodyAsText().contains("AMBIGUOUS"))
        return w1 to w2
    }

    // --- Ventas múltiples ------------------------------------------------------

    @Test
    fun `crear venta con referencia la devuelve en la respuesta`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }

        val response = client.post("/api/yape/wait") {
            contentType(ContentType.Application.Json)
            setBody("""{"amountMinor": 700, "reference": "SALE-001"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val created = json.decodeFromString<YapeWaitCreatedDto>(response.bodyAsText())
        assertEquals("SALE-001", created.reference)
        assertEquals("WAITING", created.status)
        assertEquals("S/ 7.00", created.amountDisplay)
    }

    @Test
    fun `listado de ventas incluye todas las pendientes`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }
        client.post("/api/yape/wait") {
            contentType(ContentType.Application.Json)
            setBody("""{"amountMinor": 700}""")
        }
        client.post("/api/yape/wait") {
            contentType(ContentType.Application.Json)
            setBody("""{"amountMinor": 700}""")
        }
        client.post("/api/yape/wait") {
            contentType(ContentType.Application.Json)
            setBody("""{"amountMinor": 1200}""")
        }

        val response = client.get("/api/yape/waits")

        assertEquals(HttpStatusCode.OK, response.status)
        val waits = json.decodeFromString<List<YapeWaitViewDto>>(response.bodyAsText())
        assertEquals(3, waits.size)
        assertEquals(listOf(700L, 700L, 1200L), waits.map { it.amountMinor })
        assertTrue(waits.all { it.status == "WAITING" })
    }

    // --- Resolución por código -------------------------------------------------

    @Test
    fun `resolve por codigo responde PAID`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }
        val (w1, w2) = client.setupAmbiguous()

        val response = client.post("/api/yape/waits/${w1.id}/resolve") {
            contentType(ContentType.Application.Json)
            setBody("""{"securityCode": "381"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val resolved = json.decodeFromString<YapeWaitViewDto>(response.bodyAsText())
        assertEquals("PAID", resolved.status)

        val waits = json.decodeFromString<List<YapeWaitViewDto>>(client.get("/api/yape/waits").bodyAsText())
        assertEquals("PAID", waits.first { it.id == w1.id }.status)
        assertEquals("WAITING", waits.first { it.id == w2.id }.status)
    }

    @Test
    fun `resolve con codigo incorrecto responde 409`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }
        val (w1, _) = client.setupAmbiguous()

        val response = client.post("/api/yape/waits/${w1.id}/resolve") {
            contentType(ContentType.Application.Json)
            setBody("""{"securityCode": "999"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val waits = json.decodeFromString<List<YapeWaitViewDto>>(client.get("/api/yape/waits").bodyAsText())
        assertTrue(waits.all { it.status == "WAITING" })
    }

    @Test
    fun `resolve sin datos responde 400`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }
        val (w1, _) = client.setupAmbiguous()

        val response = client.post("/api/yape/waits/${w1.id}/resolve") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `resolve con codigo de formato invalido responde 400`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }
        val (w1, _) = client.setupAmbiguous()

        val response = client.post("/api/yape/waits/${w1.id}/resolve") {
            contentType(ContentType.Application.Json)
            setBody("""{"securityCode": "12"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `resolve de venta inexistente responde 404`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }

        val response = client.post("/api/yape/waits/YW-999999/resolve") {
            contentType(ContentType.Application.Json)
            setBody("""{"securityCode": "381"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // --- Resolución por nombre (online-ready) ----------------------------------

    @Test
    fun `resolve por nombre via API paga la venta`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }
        val w1 = json.decodeFromString<YapeWaitCreatedDto>(
            client.post("/api/yape/wait") {
                contentType(ContentType.Application.Json)
                setBody("""{"amountMinor": 700}""")
            }.bodyAsText(),
        )
        client.post("/api/yape/wait") {
            contentType(ContentType.Application.Json)
            setBody("""{"amountMinor": 700}""")
        }
        val inject = client.post("/api/lab/yape/inject") {
            contentType(ContentType.Application.Json)
            setBody("""{"amountMinor": 700, "payerName": "Jhon Mayta Arotaype"}""")
        }
        assertEquals(HttpStatusCode.Accepted, inject.status)

        val response = client.post("/api/yape/waits/${w1.id}/resolve") {
            contentType(ContentType.Application.Json)
            setBody("""{"payerName": "JHON  mayta AROTAYPE"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("PAID", json.decodeFromString<YapeWaitViewDto>(response.bodyAsText()).status)
    }

    // --- Inyección LAB y diagnóstico -------------------------------------------

    @Test
    fun `inyeccion LAB estructurada devuelve AMBIGUOUS`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }
        client.post("/api/yape/wait") {
            contentType(ContentType.Application.Json)
            setBody("""{"amountMinor": 700}""")
        }
        client.post("/api/yape/wait") {
            contentType(ContentType.Application.Json)
            setBody("""{"amountMinor": 700}""")
        }

        val response = client.post("/api/lab/yape/inject") {
            contentType(ContentType.Application.Json)
            setBody("""{"amountMinor": 700, "payerName": "Persona Dos", "securityCode": null}""")
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertTrue(response.bodyAsText().contains("AMBIGUOUS"))
    }

    @Test
    fun `diagnostico LAB no expone codigo en claro ni texto crudo`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }
        client.setupAmbiguous()

        val response = client.get("/api/lab/yape/notifications")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        val views = json.decodeFromString<List<YapeNotificationViewDto>>(body)
        assertEquals(1, views.size)
        val view = views.single()
        // Nombre ya normalizado, indicador de HMAC pero nunca el código.
        assertEquals("persona uno", view.payerName)
        assertTrue(view.hasSecurityCode)
        assertEquals("S/ 7.00", view.amountDisplay)
        assertFalse(body.contains("\"securityCode\""))
        assertFalse(body.contains("\"securityCodeHmac\""))
        // Ningún campo expuesto contiene el código en claro.
        listOf(view.id, view.payerName!!, view.sourcePackage, view.sourceType, view.matchStatus, view.amountDisplay)
            .forEach { field -> assertFalse(field.contains("381"), "código en claro: $field") }
        // Nada de raw text.
        assertFalse(body.contains("envió"))
        assertFalse(body.contains("Confirmación"))
    }

    // --- UI ---------------------------------------------------------------------

    @Test
    fun `yape-test muestra la tabla y el panel de resolucion`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }

        val response = client.get("/yape-test")

        assertEquals(HttpStatusCode.OK, response.status)
        val html = response.bodyAsText()
        assertTrue(html.contains("YAPE PAYMENT TEST"))
        assertTrue(html.contains("ESPERAR YAPE"))
        assertTrue(html.contains("NUEVA PRUEBA"))
        // Tabla Venta / Monto / Estado.
        assertTrue(html.contains(">Venta<"))
        assertTrue(html.contains(">Monto<"))
        assertTrue(html.contains(">Estado<"))
        // Panel ambiguo.
        assertTrue(html.contains("PAGO RECIBIDO"))
        assertTrue(html.contains("CONFIRMAR"))
        // Flujo SSE.
        assertTrue(html.contains("/api/yape/wait/events"))
        assertTrue(html.contains("WAITING FOR NOTIFICATION"))
    }
}
