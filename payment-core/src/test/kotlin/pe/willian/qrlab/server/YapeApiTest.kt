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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pe.willian.qrlab.payment.InMemoryPaymentRepository
import pe.willian.qrlab.payment.PaymentRepository
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentProvider
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentSimulator
import pe.willian.qrlab.yape.YapeMatchResult
import pe.willian.qrlab.yape.YapeNotificationDebugDto
import pe.willian.qrlab.yape.YapeWaitCreatedDto
import pe.willian.qrlab.yape.YapeWaitService
import pe.willian.qrlab.yape.YapeWaitViewDto
import java.nio.file.Files
import java.nio.file.Path

/**
 * API HTTP del MODELO 1 con testApplication. Sin Android, sin datos privados,
 * sin samples/private/, sin red externa, sin APIs de Yape.
 */
class YapeApiTest {

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

    private fun yapeService(packageName: String? = allowedPackage) =
        YapeWaitService(allowedPackage = packageName)

    /** Cuerpo raw mínimo del bridge (LAB ONLY). */
    private fun rawBody(packageName: String, text: String): String =
        """{
            "packageName": "$packageName",
            "postedAt": "2026-09-21T12:00:00Z",
            "title": "Yape",
            "text": "$text"
        }"""

    // --- Espera ---------------------------------------------------------------

    @Test
    fun `crear espera responde WAITING con display`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }

        val response = client.post("/api/yape/wait") {
            contentType(ContentType.Application.Json)
            setBody("""{"amountMinor":700}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val wait = json.decodeFromString<YapeWaitCreatedDto>(response.bodyAsText())
        assertEquals(700, wait.amountMinor)
        assertEquals("S/ 7.00", wait.amountDisplay)
        assertEquals("WAITING", wait.status)
        assertTrue(wait.id.isNotBlank())
    }

    @Test
    fun `crear espera con monto cero responde 400`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }

        val response = client.post("/api/yape/wait") {
            contentType(ContentType.Application.Json)
            setBody("""{"amountMinor":0}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `consultar espera actual devuelve la creada`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }
        val created = client.createWait(700)

        val response = client.get("/api/yape/wait/current")

        assertEquals(HttpStatusCode.OK, response.status)
        val current = json.decodeFromString<YapeWaitViewDto>(response.bodyAsText())
        assertEquals(created.id, current.id)
        assertEquals("WAITING", current.status)
    }

    @Test
    fun `sin espera actual responde 404`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }

        assertEquals(HttpStatusCode.NotFound, client.get("/api/yape/wait/current").status)
    }

    @Test
    fun `una nueva espera reemplaza a la anterior`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }
        val first = client.createWait(700)

        val second = client.createWait(500)

        assertNotEquals(first.id, second.id)
        assertEquals(second.id, json.decodeFromString<YapeWaitViewDto>(client.get("/api/yape/wait/current").bodyAsText()).id)
    }

    // --- Bridge LAB ONLY ------------------------------------------------------

    @Test
    fun `notificacion con package y monto correctos marca PAID`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }
        client.createWait(700)

        val response = client.post("/api/bridge/raw-notification") {
            contentType(ContentType.Application.Json)
            setBody(rawBody(allowedPackage, "Has recibido S/ 7.00"))
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        val debug = json.decodeFromString<YapeNotificationDebugDto>(response.bodyAsText())
        assertEquals(YapeMatchResult.PAYMENT_MATCHED, debug.result)
        assertEquals(allowedPackage, debug.packageName)

        val current = json.decodeFromString<YapeWaitViewDto>(client.get("/api/yape/wait/current").bodyAsText())
        assertEquals("PAID", current.status)
        assertEquals(allowedPackage, current.sourcePackage)
        assertTrue(current.receivedAt != null)
    }

    @Test
    fun `notificacion con monto incorrecto no marca PAID`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }
        client.createWait(700)

        val response = client.post("/api/bridge/raw-notification") {
            contentType(ContentType.Application.Json)
            setBody(rawBody(allowedPackage, "Has recibido S/ 50.00"))
        }

        assertEquals(YapeMatchResult.AMOUNT_MISMATCH, json.decodeFromString<YapeNotificationDebugDto>(response.bodyAsText()).result)
        assertEquals("WAITING", json.decodeFromString<YapeWaitViewDto>(client.get("/api/yape/wait/current").bodyAsText()).status)
    }

    @Test
    fun `notificacion con package incorrecto no marca PAID`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }
        client.createWait(700)

        val response = client.post("/api/bridge/raw-notification") {
            contentType(ContentType.Application.Json)
            setBody(rawBody("com.otra.app", "Has recibido S/ 7.00"))
        }

        assertEquals(YapeMatchResult.PACKAGE_NOT_ALLOWED, json.decodeFromString<YapeNotificationDebugDto>(response.bodyAsText()).result)
        assertEquals("WAITING", json.decodeFromString<YapeWaitViewDto>(client.get("/api/yape/wait/current").bodyAsText()).status)
    }

    @Test
    fun `sin YAPE_ALLOWED_PACKAGE responde PACKAGE_NOT_CONFIGURED`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService(packageName = null)) }
        client.createWait(700)

        val response = client.post("/api/bridge/raw-notification") {
            contentType(ContentType.Application.Json)
            setBody(rawBody("pe.any.app", "Has recibido S/ 7.00"))
        }

        assertEquals(YapeMatchResult.PACKAGE_NOT_CONFIGURED, json.decodeFromString<YapeNotificationDebugDto>(response.bodyAsText()).result)
        assertEquals("WAITING", json.decodeFromString<YapeWaitViewDto>(client.get("/api/yape/wait/current").bodyAsText()).status)
    }

    @Test
    fun `evento duplicado no vuelve a procesar el pago`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }
        client.createWait(700)

        val first = client.post("/api/bridge/raw-notification") {
            contentType(ContentType.Application.Json)
            setBody(rawBody(allowedPackage, "Has recibido S/ 7.00"))
        }
        val second = client.post("/api/bridge/raw-notification") {
            contentType(ContentType.Application.Json)
            setBody(rawBody(allowedPackage, "Has recibido S/ 7.00"))
        }

        assertEquals(YapeMatchResult.PAYMENT_MATCHED, json.decodeFromString<YapeNotificationDebugDto>(first.bodyAsText()).result)
        assertEquals(YapeMatchResult.ALREADY_PAID, json.decodeFromString<YapeNotificationDebugDto>(second.bodyAsText()).result)

        val current = json.decodeFromString<YapeWaitViewDto>(client.get("/api/yape/wait/current").bodyAsText())
        assertEquals("PAID", current.status)
    }

    // --- Web ------------------------------------------------------------------

    @Test
    fun `yape-test sirve HTML del laboratorio`(@TempDir temp: Path) = testApplication {
        application { paymentModule(testService(temp), yapeService()) }

        val response = client.get("/yape-test")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()?.match(ContentType.Text.Html) == true)
        val html = response.bodyAsText()
        assertTrue(html.contains("YAPE PAYMENT TEST"))
        assertTrue(html.contains("ESPERAR YAPE"))
        assertTrue(html.contains("NUEVA PRUEBA"))
        assertTrue(html.contains("WAITING FOR NOTIFICATION"))
        assertTrue(html.contains("/api/yape/wait/events"))
    }

    // Helper con cliente implícito: se usa solo dentro de testApplication.
    private suspend fun io.ktor.client.HttpClient.createWait(amountMinor: Long): YapeWaitCreatedDto {
        val response = post("/api/yape/wait") {
            contentType(ContentType.Application.Json)
            setBody("""{"amountMinor":$amountMinor}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(response.bodyAsText())
    }
}
