package pe.willian.qrlab.bridge

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pe.willian.qrlab.money.MoneyFormatter
import pe.willian.qrlab.payment.InMemoryPaymentRepository
import pe.willian.qrlab.payment.PaymentIntent
import pe.willian.qrlab.payment.PaymentMethod
import pe.willian.qrlab.payment.PaymentRepository
import pe.willian.qrlab.payment.PaymentStatus
import pe.willian.qrlab.payment.SqlitePaymentRepository
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentProvider
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentSimulator
import pe.willian.qrlab.qr.QrImageWriter
import pe.willian.qrlab.server.BridgeEventDto
import pe.willian.qrlab.server.PaymentDto
import pe.willian.qrlab.server.PaymentEventBus
import pe.willian.qrlab.server.PaymentService
import pe.willian.qrlab.server.paymentModule
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Backend receptor y matcher de notificaciones Yape (LAB).
 * SQLite temporal por test, sin QR reales, sin samples/private/.
 */
class YapeBridgeTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val bridgeKey = "test-bridge-key"
    private val matchSecret = "test-match-secret"

    private fun config(staticQrFile: Path? = null) = YapeBridgeConfig(
        bridgeKey = bridgeKey,
        matchSecret = matchSecret,
        matchWindowSeconds = 600,
        staticQrPayloadFile = staticQrFile,
    )

    private fun service(
        qrDir: Path,
        repository: PaymentRepository,
        store: BridgeStore,
        staticQrFile: Path? = null,
    ): PaymentService = PaymentService(
        provider = SyntheticPaymentProvider(repository, qrDir),
        repository = repository,
        simulator = SyntheticPaymentSimulator(repository),
        eventBus = PaymentEventBus(),
        qrDir = qrDir,
        storageName = "memory",
        bridgeStore = store,
        bridgeConfig = config(staticQrFile),
    )

    private fun rawJson(
        eventId: String,
        text: String,
        packageName: String = "com.lab.yape",
        key: String? = null,
    ): String {
        val keyField = if (key == null) "null" else "\"$key\""
        return """{"bridgeEventId":"$eventId","packageName":"$packageName","notificationKey":$keyField,"postedAt":"2026-09-21T10:00:00Z","title":"Yape","text":"$text"}"""
    }

    private suspend fun io.ktor.client.HttpClient.createYape(reference: String, amount: Long): PaymentDto {
        val response = post("/api/payments") {
            contentType(ContentType.Application.Json)
            setBody("""{"reference":"$reference","amountMinor":$amount,"currency":"604","method":"YAPE_NOTIFICATION"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return json.decodeFromString(PaymentDto.serializer(), response.bodyAsText())
    }

    private suspend fun io.ktor.client.HttpClient.postBridge(body: String, key: String? = bridgeKey) =
        post("/api/bridge/notifications") {
            contentType(ContentType.Application.Json)
            if (key != null) header("X-Bridge-Key", key)
            setBody(body)
        }

    private suspend fun io.ktor.client.HttpClient.inject(amount: Long, payer: String? = null, code: String? = null): BridgeEventDto {
        val payerField = if (payer == null) "null" else "\"$payer\""
        val codeField = if (code == null) "null" else "\"$code\""
        val response = post("/api/lab/bridge/inject") {
            contentType(ContentType.Application.Json)
            setBody("""{"amountMinor":$amount,"payerName":$payerField,"securityCode":$codeField}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return json.decodeFromString(BridgeEventDto.serializer(), response.bodyAsText())
    }

    private suspend fun io.ktor.client.HttpClient.getPayment(id: String): PaymentDto {
        val response = get("/api/payments/$id")
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(PaymentDto.serializer(), response.bodyAsText())
    }

    // ---- 1. creación ----

    @Test
    fun `YAPE_NOTIFICATION crea PENDING sin QR dinámico`(@TempDir temp: Path) = testApplication {
        application { paymentModule(service(temp, InMemoryPaymentRepository(), InMemoryBridgeStore())) }
        val created = client.createYape("SALE-Y-001", 700)

        assertEquals("PENDING", created.status)
        assertEquals("YAPE_NOTIFICATION", created.method)
        assertEquals(700, created.amountMinor)
        assertEquals("S/ 7.00", created.amountDisplay)
        assertNull(created.qrUrl)
        assertNull(created.staticQrUrl)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/payments/${created.id}/qr").status)
    }

    // ---- 2/3. seguridad bridge ----

    @Test
    fun `bridge key correcto acepta evento`(@TempDir temp: Path) = testApplication {
        application { paymentModule(service(temp, InMemoryPaymentRepository(), InMemoryBridgeStore())) }
        client.createYape("SALE-Y-002", 700)

        val response = client.postBridge(rawJson("EVT-A-001", "Recibiste S/ 7.00", key = "k-1"))

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("MATCHED", json.decodeFromString(BridgeEventDto.serializer(), response.bodyAsText()).matchStatus)
    }

    @Test
    fun `bridge key incorrecto responde 401`(@TempDir temp: Path) = testApplication {
        application { paymentModule(service(temp, InMemoryPaymentRepository(), InMemoryBridgeStore())) }

        assertEquals(HttpStatusCode.Unauthorized, client.postBridge(rawJson("EVT-A-002", "S/ 7.00"), key = "otra-clave").status)
    }

    @Test
    fun `bridge key ausente responde 401`(@TempDir temp: Path) = testApplication {
        application { paymentModule(service(temp, InMemoryPaymentRepository(), InMemoryBridgeStore())) }

        assertEquals(HttpStatusCode.Unauthorized, client.postBridge(rawJson("EVT-A-003", "S/ 7.00"), key = null).status)
    }

    // ---- 4. deduplicación ----

    @Test
    fun `evento duplicado se procesa una vez`(@TempDir temp: Path) = testApplication {
        val store = InMemoryBridgeStore()
        application { paymentModule(service(temp, InMemoryPaymentRepository(), store)) }
        val created = client.createYape("SALE-Y-003", 700)
        val body = rawJson("EVT-DUP-001", "Recibiste S/ 7.00", key = "k-dup")

        val first = client.postBridge(body)
        val second = client.postBridge(body)

        assertEquals(HttpStatusCode.Created, first.status)
        assertEquals(HttpStatusCode.OK, second.status)
        val a = json.decodeFromString(BridgeEventDto.serializer(), first.bodyAsText())
        val b = json.decodeFromString(BridgeEventDto.serializer(), second.bodyAsText())
        assertEquals(a.id, b.id)
        assertEquals(1, store.count())
        assertEquals("PAID", client.getPayment(created.id).status)
    }

    // ---- 5/6/7. matcher ----

    @Test
    fun `0 candidatos deja UNMATCHED sin crear venta`(@TempDir temp: Path) = testApplication {
        application { paymentModule(service(temp, InMemoryPaymentRepository(), InMemoryBridgeStore())) }

        val response = client.postBridge(rawJson("EVT-U-001", "Recibiste S/ 7.00", key = "k-u"))

        assertEquals("UNMATCHED", json.decodeFromString(BridgeEventDto.serializer(), response.bodyAsText()).matchStatus)
        val events = json.decodeFromString<List<BridgeEventDto>>(
            kotlinx.serialization.builtins.ListSerializer(BridgeEventDto.serializer()),
            client.get("/api/lab/bridge/events").bodyAsText(),
        )
        assertEquals(1, events.size)
        assertEquals("UNMATCHED", events.single().matchStatus)
    }

    @Test
    fun `1 candidato hace AUTO MATCH a PAID con referencia interna`(@TempDir temp: Path) = testApplication {
        application { paymentModule(service(temp, InMemoryPaymentRepository(), InMemoryBridgeStore())) }
        val created = client.createYape("SALE-Y-004", 700)

        client.postBridge(rawJson("EVT-M-001", "Yape! Recibiste S/ 7.00 de Carlos", key = "k-m"))

        val paid = client.getPayment(created.id)
        assertEquals("PAID", paid.status)
        assertNotNull(paid.paidAt)
        assertTrue(paid.providerOperationReference?.startsWith("BRIDGE-EVENT-") == true)
        assertFalse(paid.providerOperationReference!!.contains("Yape", ignoreCase = true))
    }

    @Test
    fun `2 candidatos mismo monto deja AMBIGUOUS sin pagar`(@TempDir temp: Path) = testApplication {
        application { paymentModule(service(temp, InMemoryPaymentRepository(), InMemoryBridgeStore())) }
        val a = client.createYape("SALE-Y-005", 700)
        val b = client.createYape("SALE-Y-006", 700)

        val event = client.inject(700, "Persona Demo", "381")

        assertEquals("AMBIGUOUS", event.matchStatus)
        assertNull(event.matchedPaymentId)
        assertEquals("PENDING", client.getPayment(a.id).status)
        assertEquals("PENDING", client.getPayment(b.id).status)
    }

    // ---- 8/9. resolución por código ----

    @Test
    fun `código correcto resuelve un candidato`(@TempDir temp: Path) = testApplication {
        application { paymentModule(service(temp, InMemoryPaymentRepository(), InMemoryBridgeStore())) }
        val a = client.createYape("SALE-Y-007", 700)
        val b = client.createYape("SALE-Y-008", 700)
        client.inject(700, "Persona Demo", "381")

        val response = client.post("/api/payments/${a.id}/resolve-yape") {
            contentType(ContentType.Application.Json)
            setBody("""{"securityCode":"381"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("PAID", json.decodeFromString(PaymentDto.serializer(), response.bodyAsText()).status)
        assertEquals("PENDING", client.getPayment(b.id).status)
    }

    @Test
    fun `código incorrecto responde 409`(@TempDir temp: Path) = testApplication {
        application { paymentModule(service(temp, InMemoryPaymentRepository(), InMemoryBridgeStore())) }
        val a = client.createYape("SALE-Y-009", 700)
        client.createYape("SALE-Y-010", 700)
        client.inject(700, "Persona Demo", "381")

        val response = client.post("/api/payments/${a.id}/resolve-yape") {
            contentType(ContentType.Application.Json)
            setBody("""{"securityCode":"999"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("PENDING", client.getPayment(a.id).status)
    }

    // ---- 10/11/12/13. reglas de no-match ----

    @Test
    fun `nombre solo no confirma pago`(@TempDir temp: Path) = testApplication {
        application { paymentModule(service(temp, InMemoryPaymentRepository(), InMemoryBridgeStore())) }
        val created = client.createYape("SALE-Y-011", 700)

        val event = client.inject(800, "Persona Demo", null)

        assertEquals("UNMATCHED", event.matchStatus)
        assertEquals("PENDING", client.getPayment(created.id).status)
    }

    @Test
    fun `diferente monto no hace match`(@TempDir temp: Path) = testApplication {
        application { paymentModule(service(temp, InMemoryPaymentRepository(), InMemoryBridgeStore())) }
        val created = client.createYape("SALE-Y-012", 700)

        val event = client.inject(750, null, null)

        assertEquals("UNMATCHED", event.matchStatus)
        assertEquals("PENDING", client.getPayment(created.id).status)
    }

    @Test
    fun `fuera de ventana temporal no hace match`(@TempDir temp: Path) {
        val repository = InMemoryPaymentRepository()
        val old = PaymentIntent(
            id = "PAY-000001",
            reference = "SALE-OLD",
            amountMinor = 700,
            currency = "604",
            status = PaymentStatus.PENDING,
            qrPayload = null,
            createdAt = Instant.now().minusSeconds(7200),
            expiresAt = null,
            paidAt = null,
            providerOperationReference = null,
            paymentMethod = PaymentMethod.YAPE_NOTIFICATION,
        )
        repository.save(old)
        val svc = service(temp, repository, InMemoryBridgeStore())

        runBlocking {
            val view = svc.labInject(LabBridgeInjectRequestShim(700).toRequest())

            assertEquals("UNMATCHED", view.matchStatus)
            assertEquals(PaymentStatus.PENDING, repository.findById("PAY-000001")?.status)
        }
    }

    @Test
    fun `SYNTHETIC_QR nunca participa en matcher`(@TempDir temp: Path) = testApplication {
        application { paymentModule(service(temp, InMemoryPaymentRepository(), InMemoryBridgeStore())) }
        val created = json.decodeFromString<PaymentDto>(
            client.post("/api/payments") {
                contentType(ContentType.Application.Json)
                setBody("""{"reference":"SALE-S-001","amountMinor":700,"currency":"604"}""")
            }.bodyAsText(),
        )

        val event = client.inject(700, null, null)

        assertEquals("UNMATCHED", event.matchStatus)
        assertEquals("PENDING", client.getPayment(created.id).status)
    }

    // ---- 14. HMAC ----

    @Test
    fun `código nunca persiste en plano`(@TempDir temp: Path) = testApplication {
        val store = InMemoryBridgeStore()
        application { paymentModule(service(temp, InMemoryPaymentRepository(), store)) }
        client.createYape("SALE-Y-013", 700)
        client.createYape("SALE-Y-014", 700)
        client.inject(700, "Persona Demo", "381")

        val record = store.findAll().single()
        assertNotNull(record.securityCodeHmac)
        assertNotEquals("381", record.securityCodeHmac)
        assertFalse(record.securityCodeHmac!!.contains("381"))
        assertEquals(SecurityCodeMatcher.hmacHex("381", matchSecret), record.securityCodeHmac)
    }

    // ---- 15/16. SSE ----

    @Test
    fun `SSE recibe PAID tras auto match`(@TempDir temp: Path) = runBlocking {
        val svc = service(temp, InMemoryPaymentRepository(), InMemoryBridgeStore())
        val created = svc.create("SALE-Y-015", 700, "604", "YAPE_NOTIFICATION")

        val received = async {
            withTimeout(5_000) { svc.events(created.id)!!.first() }
        }
        delay(200)
        svc.labInject(pe.willian.qrlab.server.LabBridgeInjectRequest(700, "Persona Demo", "381"))

        assertEquals("PAID", received.await().status)
    }

    @Test
    fun `SSE recibe PAID tras resolve-yape`(@TempDir temp: Path) = runBlocking {
        val svc = service(temp, InMemoryPaymentRepository(), InMemoryBridgeStore())
        val a = svc.create("SALE-Y-016", 700, "604", "YAPE_NOTIFICATION")
        svc.create("SALE-Y-017", 700, "604", "YAPE_NOTIFICATION")
        svc.labInject(pe.willian.qrlab.server.LabBridgeInjectRequest(700, "Persona Demo", "381"))

        val received = async {
            withTimeout(5_000) { svc.events(a.id)!!.first() }
        }
        delay(200)
        svc.resolveYape(a.id, "381")

        assertEquals("PAID", received.await().status)
    }

    // ---- 17/18. persistencia ----

    @Test
    fun `reinicio SQLite conserva incoming events`(@TempDir temp: Path) = runBlocking {
        val db = temp.resolve("bridge.db")
        val createdId = SqlitePaymentRepository(db).use { repo ->
            SqliteBridgeStore(db).use { store ->
                val svc = service(temp, repo, store)
                val created = svc.create("SALE-Y-018", 700, "604", "YAPE_NOTIFICATION")
                svc.labInject(pe.willian.qrlab.server.LabBridgeInjectRequest(700, "Persona Demo", "381"))
                created.id
            }
        }

        SqlitePaymentRepository(db).use { reopened ->
            SqliteBridgeStore(db).use { reopenedStore ->
                val svcB = service(temp, reopened, reopenedStore)
                assertEquals("PAID", svcB.get(createdId)?.status)
                val events = svcB.labBridgeEvents()
                assertEquals(1, events.size)
                assertEquals("MATCHED", events.single().matchStatus)
                assertEquals(createdId, events.single().matchedPaymentId)
            }
        }
    }

    @Test
    fun `duplicada después de reinicio no cobra dos veces`(@TempDir temp: Path) = runBlocking {
        val db = temp.resolve("bridge2.db")
        val raw = RawBridgeNotification(
            bridgeEventId = "EVT-RE-001",
            packageName = "com.lab.yape",
            notificationKey = "k-re",
            postedAt = Instant.now().toString(),
            title = "Yape",
            text = "Recibiste S/ 7.00",
        )
        val paymentId = SqlitePaymentRepository(db).use { repo ->
            SqliteBridgeStore(db).use { store ->
                val svc = service(temp, repo, store)
                val created = svc.create("SALE-Y-019", 700, "604", "YAPE_NOTIFICATION")
                val first = svc.bridgeReceive(raw)
                assertFalse(first.second)
                created.id
            }
        }

        SqlitePaymentRepository(db).use { reopened ->
            SqliteBridgeStore(db).use { reopenedStore ->
                val svcB = service(temp, reopened, reopenedStore)
                val again = svcB.bridgeReceive(raw)

                assertTrue(again.second)
                assertEquals(1, reopenedStore.count())
                val payment = reopened.findById(paymentId)!!
                assertEquals(PaymentStatus.PAID, payment.status)
                assertTrue(payment.providerOperationReference?.startsWith("BRIDGE-EVENT-") == true)
            }
        }
    }

    // ---- 19. migración ----

    @Test
    fun `migración SQLite conserva datos sin payment_method`(@TempDir temp: Path) {
        val db = temp.resolve("legacy.db")
        // DB legada: esquema anterior sin columna payment_method.
        java.sql.DriverManager.getConnection("jdbc:sqlite:${db.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """CREATE TABLE payment_intents (
                        id TEXT PRIMARY KEY, reference TEXT NOT NULL, amount_minor INTEGER NOT NULL,
                        currency TEXT NOT NULL, status TEXT NOT NULL, qr_payload TEXT,
                        created_at TEXT NOT NULL, expires_at TEXT, paid_at TEXT,
                        provider_operation_reference TEXT)""",
                )
                statement.executeUpdate(
                    """INSERT INTO payment_intents VALUES (
                        'PAY-000001', 'SALE-LEGACY', 700, '604', 'PENDING', 'qr-legacy',
                        '2026-09-21T00:00:00Z', NULL, NULL, NULL)""",
                )
            }
        }

        SqlitePaymentRepository(db).use { repo ->
            val found = repo.findById("PAY-000001")

            assertEquals("SALE-LEGACY", found?.reference)
            assertEquals("qr-legacy", found?.qrPayload)
            assertEquals(PaymentStatus.PENDING, found?.status)
            assertEquals(PaymentMethod.SYNTHETIC_QR, found?.paymentMethod)
        }
    }

    // ---- 20/21. static qr y vista lab ----

    @Test
    fun `static-qr sin configurar responde 404`(@TempDir temp: Path) = testApplication {
        application { paymentModule(service(temp, InMemoryPaymentRepository(), InMemoryBridgeStore())) }

        val response = client.get("/api/yape/static-qr")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `static-qr configurado genera PNG exacto`(@TempDir temp: Path) = testApplication {
        val payloadFile = temp.resolve("static-qr.txt")
        Files.writeString(payloadFile, "LAB-STATIC-QR-TEST-PAYLOAD-12345")
        application {
            paymentModule(service(temp, InMemoryPaymentRepository(), InMemoryBridgeStore(), payloadFile))
        }

        val response = client.get("/api/yape/static-qr")

        assertEquals(HttpStatusCode.OK, response.status)
        val png = temp.resolve("static-leido.png")
        Files.write(png, response.body<ByteArray>())
        assertEquals("LAB-STATIC-QR-TEST-PAYLOAD-12345", QrImageWriter.read(png))

        // Y el create YAPE expone staticQrUrl.
        val created = client.createYape("SALE-Y-020", 700)
        assertEquals("/api/yape/static-qr", created.staticQrUrl)
    }

    @Test
    fun `vista lab no expone código ni hmac`(@TempDir temp: Path) = testApplication {
        application { paymentModule(service(temp, InMemoryPaymentRepository(), InMemoryBridgeStore())) }
        client.createYape("SALE-Y-021", 700)
        client.createYape("SALE-Y-022", 700)
        client.inject(700, "Persona Demo", "381")

        val body = client.get("/api/lab/bridge/events").bodyAsText()

        assertFalse(body.contains("381"))
        assertFalse(body.contains("hmac", ignoreCase = true))
        assertTrue(body.contains("Persona Demo"))
        assertTrue(body.contains("S/ 7.00"))
        assertTrue(MoneyFormatter.format(700) == "S/ 7.00")
    }

    // ---- parser ----

    @Test
    fun `heurística parsea S slash 7 coma 50`() {
        val parsed = ConfigurableNotificationParser().parse(
            RawBridgeNotification(
                bridgeEventId = "E1",
                packageName = "com.lab.yape",
                postedAt = "2026-09-21T10:00:00Z",
                text = "Recibiste S/7,50 de prueba",
            ),
        )

        assertEquals(750, parsed?.amountMinor)
        assertNull(parsed?.payerName)
        assertNull(parsed?.securityCode)
    }

    @Test
    fun `heurística extrae código de seguridad de 3 dígitos`() {
        val parsed = ConfigurableNotificationParser().parse(
            RawBridgeNotification(
                bridgeEventId = "E2",
                packageName = "com.lab.yape",
                postedAt = "2026-09-21T10:00:00Z",
                text = "Te enviaron S/ 7.00. Código de seguridad 381",
            ),
        )

        assertEquals(700, parsed?.amountMinor)
        assertEquals("381", parsed?.securityCode)
    }

    // Shim local para no acoplar el test de ventana al DTO del server.
    private data class LabBridgeInjectRequestShim(val amountMinor: Long) {
        fun toRequest() = pe.willian.qrlab.server.LabBridgeInjectRequest(amountMinor, null, null)
    }
}
