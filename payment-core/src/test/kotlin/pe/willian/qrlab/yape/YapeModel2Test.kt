package pe.willian.qrlab.yape

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import pe.willian.qrlab.payment.InMemoryPaymentRepository
import pe.willian.qrlab.payment.PaymentRepository
import pe.willian.qrlab.payment.PaymentStatus
import pe.willian.qrlab.payment.synthetic.SyntheticPaymentProvider
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.time.Instant

/**
 * Tests del MODELO 2: múltiples ventas WAITING (incluso mismo importe),
 * auto match con UN candidato, AMBIGUOUS con 2+, resolución y seguridad.
 * Sin Android, sin datos privados, sin APIs de Yape.
 */
class YapeModel2Test {

    private val allowedPackage = "pe.willian.qrlab.fakeyape"

    private fun service(
        packageName: String? = allowedPackage,
        clock: () -> Instant = Instant::now,
        windowSeconds: Long = 600,
    ) = YapeWaitService(
        allowedPackage = packageName,
        matchSecret = "test-secret",
        matchWindowSeconds = windowSeconds,
        clock = clock,
    )

    private fun notification(packageName: String, vararg texts: String?): YapeRawNotificationRequest {
        val (title, text, bigText, subText, summaryText) = listOf<String?>(
            texts.getOrNull(0),
            texts.getOrNull(1),
            texts.getOrNull(2),
            texts.getOrNull(3),
            texts.getOrNull(4),
        )
        return YapeRawNotificationRequest(
            packageName = packageName,
            postedAt = "2026-09-22T12:00:00Z",
            title = title,
            text = text,
            bigText = bigText,
            subText = subText,
            summaryText = summaryText,
        )
    }

    private fun captureStdout(block: () -> Unit): String {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer, true, Charsets.UTF_8))
        try {
            block()
        } finally {
            System.setOut(original)
        }
        return buffer.toString(Charsets.UTF_8)
    }

    // --- Múltiples ventas y ambigüedad --------------------------------------

    @Test
    fun `dos ventas WAITING mismo monto y notificacion dan AMBIGUOUS sin pagar nada`() {
        val yape = service()
        val w1 = yape.createWait(700)
        val w2 = yape.createWait(700)

        val debug = yape.receiveRaw(notification(allowedPackage, "Yape", "Has recibido S/ 7.00"))

        assertEquals(YapeMatchResult.AMBIGUOUS, debug.result)
        assertEquals("AMBIGUOUS", debug.matchStatus)
        // Ninguna venta se marca PAID automáticamente.
        assertEquals("WAITING", yape.find(w1.id)?.status)
        assertEquals("WAITING", yape.find(w2.id)?.status)
        // Notificación persistida como AMBIGUOUS y estado publicado para la UI.
        assertEquals("AMBIGUOUS", yape.notifications().single().matchStatus)
        val ambiguous = yape.state.value.ambiguous
        assertEquals("S/ 7.00", ambiguous?.amountDisplay)
        assertEquals(listOf(w1.id, w2.id), ambiguous?.paymentIds)
        // Texto crudo jamás en el estado.
        assertFalse(
            kotlinx.serialization.json.Json.encodeToString(YapeStateDto.serializer(), yape.state.value)
                .contains("Has recibido"),
        )
    }

    @Test
    fun `tres ventas el pago unico auto matchea solo esa venta`() {
        val yape = service()
        val a = yape.createWait(700)
        val b = yape.createWait(700)
        val c = yape.createWait(1200)

        val debug = yape.receiveRaw(notification(allowedPackage, "S/ 12.00"))

        assertEquals(YapeMatchResult.PAYMENT_MATCHED, debug.result)
        assertEquals("PAID", yape.find(c.id)?.status)
        assertEquals("WAITING", yape.find(a.id)?.status)
        assertEquals("WAITING", yape.find(b.id)?.status)
        assertTrue(yape.state.value.waits.any { it.status == "PAID" })
    }

    @Test
    fun `monto diferente no participa del matching`() {
        val yape = service()
        val w1 = yape.createWait(700)
        val w2 = yape.createWait(700)
        val before = yape.state.value

        val debug = yape.receiveRaw(notification(allowedPackage, "S/ 9.00"))

        assertEquals(YapeMatchResult.AMOUNT_MISMATCH, debug.result)
        assertEquals("WAITING", yape.find(w1.id)?.status)
        assertEquals("WAITING", yape.find(w2.id)?.status)
        // La pantalla no cambia y la notificación queda UNMATCHED persistida.
        assertEquals(before, yape.state.value)
        assertEquals("UNMATCHED", yape.notifications().single().matchStatus)
        assertEquals(mapOf(YapeMatchResult.AMOUNT_MISMATCH to 1L), yape.ignoredCounts())
    }

    @Test
    fun `venta CANCELLED no participa`() {
        val yape = service()
        val w1 = yape.createWait(700)
        yape.cancelWait(w1.id)

        val debug = yape.receiveRaw(notification(allowedPackage, "S/ 7.00"))

        assertEquals(YapeMatchResult.NO_ACTIVE_WAIT, debug.result)
        assertEquals("CANCELLED", yape.find(w1.id)?.status)
        assertTrue(yape.state.value.waits.none { it.status == "PAID" })
    }

    @Test
    fun `pago sintetico del motor no participa`(@TempDir temp: Path) {
        val repository: PaymentRepository = InMemoryPaymentRepository()
        val provider = SyntheticPaymentProvider(repository, temp)
        provider.createPayment("SALE-SYN", 700)

        val yape = service()
        val debug = yape.receiveRaw(notification(allowedPackage, "S/ 7.00"))

        assertEquals(YapeMatchResult.NO_ACTIVE_WAIT, debug.result)
        assertTrue(yape.listWaits().isEmpty())
        // El Payment Engine queda intacto: nada pasó a PAID.
        assertTrue(repository.findAll().none { it.status == PaymentStatus.PAID })
    }

    @Test
    fun `cero candidatos deja la notificacion UNMATCHED`() {
        val yape = service()

        val debug = yape.receiveRaw(notification(allowedPackage, "S/ 7.00"))

        assertEquals(YapeMatchResult.NO_ACTIVE_WAIT, debug.result)
        val stored = yape.notifications().single()
        assertEquals("UNMATCHED", stored.matchStatus)
        assertEquals(700L, stored.amountMinor)
        assertNull(stored.matchedPaymentId)
    }

    @Test
    fun `venta fuera de la ventana no participa pero la nueva si`() {
        var now = Instant.parse("2026-09-22T12:00:00Z")
        val yape = service(clock = { now })
        val old = yape.createWait(700)

        now = now.plusSeconds(601) // default window: 600s
        val expired = yape.receiveRaw(notification(allowedPackage, "S/ 7.00"))
        assertEquals(YapeMatchResult.NO_ACTIVE_WAIT, expired.result)
        assertEquals("WAITING", yape.find(old.id)?.status)

        val fresh = yape.createWait(700)
        val matched = yape.receiveRaw(notification(allowedPackage, "S/ 7.00"))
        assertEquals(YapeMatchResult.PAYMENT_MATCHED, matched.result)
        assertEquals("PAID", yape.find(fresh.id)?.status)
        assertEquals("WAITING", yape.find(old.id)?.status)
    }

    // --- Inyección LAB por el MISMO matcher ---------------------------------

    @Test
    fun `inyecciones LAB pasan por el mismo matcher real`() {
        val yape = service()
        val w1 = yape.createWait(700)
        val w2 = yape.createWait(700)

        // A) con código de seguridad
        val a = yape.labInject(YapeLabInjectRequest(700, payerName = "Persona Uno", securityCode = "381"))
        // B) sin código, solo nombre
        val b = yape.labInject(YapeLabInjectRequest(700, payerName = "Persona Dos", securityCode = null))

        assertEquals(YapeMatchResult.AMBIGUOUS, a.result)
        assertEquals(YapeMatchResult.AMBIGUOUS, b.result)
        assertEquals("WAITING", yape.find(w1.id)?.status)
        assertEquals("WAITING", yape.find(w2.id)?.status)
        assertEquals(2, yape.notifications().count { it.matchStatus == "AMBIGUOUS" })
        val ambiguous = yape.state.value.ambiguous
        assertTrue(ambiguous?.requiresSecurityCode == true)
        assertTrue(ambiguous?.requiresPayerName == true)
        assertEquals(2, ambiguous?.notificationCount)
    }

    @Test
    fun `inyeccion LAB en modo diagnostico jamas paga`() {
        val yape = service(packageName = null)
        yape.createWait(700)

        val debug = yape.labInject(YapeLabInjectRequest(700, payerName = "Persona Uno", securityCode = "381"))

        assertEquals(YapeMatchResult.PACKAGE_NOT_CONFIGURED, debug.result)
        assertTrue(yape.state.value.waits.none { it.status == "PAID" })
    }

    // --- Resolución: reglas de seguridad -------------------------------------

    @Test
    fun `nombre no puede crear PAID sin notificacion real`() {
        val yape = service()
        yape.createWait(700)
        yape.createWait(700)

        assertThrows<IllegalStateException> {
            yape.resolve(yape.listWaits()[0].id, null, "Persona Uno")
        }

        assertTrue(yape.state.value.waits.all { it.status == "WAITING" })
        assertTrue(yape.notifications().isEmpty())
    }

    @Test
    fun `una notificacion solo puede pagar una venta`() {
        val yape = service()
        val w1 = yape.createWait(700)
        val w2 = yape.createWait(700)
        yape.labInject(YapeLabInjectRequest(700, securityCode = "381"))

        val resolved = yape.resolve(w1.id, "381", null)
        assertEquals("PAID", resolved.status)
        assertEquals("WAITING", yape.find(w2.id)?.status)
        assertEquals(1, yape.notifications().count { it.matchStatus == "MATCHED" })

        // La misma notificación ya no puede pagar la segunda venta.
        assertThrows<IllegalStateException> { yape.resolve(w2.id, "381", null) }
        assertEquals("WAITING", yape.find(w2.id)?.status)
    }

    @Test
    fun `una venta solo puede recibir una notificacion`() {
        val yape = service()
        val w1 = yape.createWait(700)
        yape.createWait(700)
        yape.labInject(YapeLabInjectRequest(700, securityCode = "381"))
        yape.resolve(w1.id, "381", null)

        // w1 ya está PAID: no admite otra resolución.
        assertThrows<IllegalStateException> { yape.resolve(w1.id, "381", null) }
        assertEquals(1, yape.notifications().count { it.matchedPaymentId == w1.id })
    }

    @Test
    fun `fallback manual LAB paga solo con seleccion explicita de un evento`() {
        val yape = service()
        val w1 = yape.createWait(700)
        val w2 = yape.createWait(700)
        // Ambigua SIN código ni nombre: no hay dato automático posible.
        yape.labInject(YapeLabInjectRequest(700, payerName = null, securityCode = null))
        val ambiguous = yape.notifications().single()

        // Automático: ni código ni nombre resuelven nada.
        assertThrows<IllegalStateException> { yape.resolve(w1.id, "381", null) }
        assertThrows<IllegalStateException> { yape.resolve(w1.id, null, "Persona Uno") }
        assertEquals("WAITING", yape.find(w1.id)?.status)

        // Selección manual explícita (LAB): sí, con evento real ambiguo.
        val resolved = yape.manualResolve(w1.id, ambiguous.id)
        assertEquals("PAID", resolved.status)
        assertEquals("WAITING", yape.find(w2.id)?.status)
        assertEquals("MATCHED", yape.notifications().single().matchStatus)
        assertNull(yape.state.value.ambiguous)
    }

    @Test
    fun `manual resolve exige una notificacion ambigua compatible`() {
        val yape = service()
        val w1 = yape.createWait(700)
        yape.createWait(700)
        yape.labInject(YapeLabInjectRequest(700, securityCode = "381"))
        val notificationId = yape.notifications().single().id

        // Monto distinto incompatible.
        val other = yape.createWait(1500)
        assertThrows<IllegalStateException> { yape.manualResolve(other.id, notificationId) }
        // Notificación inexistente.
        assertThrows<NoSuchElementException> { yape.manualResolve(w1.id, "YN-999999") }
    }

    // --- Concurrencia y SSE ---------------------------------------------------

    @Test
    fun `concurrencia de resolucion no genera doble PAID`() = runBlocking {
        val yape = service()
        val w1 = yape.createWait(700)
        val w2 = yape.createWait(700)
        yape.labInject(YapeLabInjectRequest(700, securityCode = "381"))

        val results = (1..8)
            .map { async(Dispatchers.Default) { runCatching { yape.resolve(w1.id, "381", null) } } }
            .awaitAll()

        assertEquals(1, results.count { it.isSuccess })
        assertEquals("PAID", yape.find(w1.id)?.status)
        assertEquals("WAITING", yape.find(w2.id)?.status)
        assertEquals(1, yape.notifications().count { it.matchStatus == "MATCHED" })
        assertEquals(1, yape.state.value.waits.count { it.status == "PAID" })
    }

    @Test
    fun `SSE emite el pago una sola vez`() = runBlocking {
        val yape = service()
        yape.createWait(700)
        val w1 = yape.createWait(700)

        val emissions = mutableListOf<YapeStateDto>()
        val job = launch { yape.state.collect { emissions.add(it) } }
        delay(100)

        yape.labInject(YapeLabInjectRequest(700, securityCode = "381")) // -> AMBIGUOUS
        delay(100)
        yape.resolve(w1.id, "381", null) // -> PAID
        delay(200)
        job.cancel()

        assertTrue(emissions.any { it.ambiguous != null })
        assertEquals(1, emissions.count { state -> state.waits.count { it.status == "PAID" } == 1 })
        assertEquals(1, yape.state.value.waits.count { it.status == "PAID" })
    }

    // --- Privacidad HMAC ------------------------------------------------------

    @Test
    fun `security code nunca queda en claro en log ni en el store`() {
        val now = Instant.parse("2026-09-22T10:00:00Z")
        val store = InMemoryYapeLabStore()
        val yape = YapeWaitService(
            allowedPackage = allowedPackage,
            store = store,
            matchSecret = "test-secret",
            clock = { now },
        )
        val w1 = yape.createWait(700)
        yape.createWait(700)

        val output = captureStdout {
            yape.labInject(YapeLabInjectRequest(700, payerName = "Persona Uno", securityCode = "381"))
            yape.resolve(w1.id, "381", null)
        }

        // Nunca el código en claro en la consola.
        assertFalse(output.contains("381"))
        assertFalse(output.contains("Persona Uno"))

        // Se inspecciona la ENTIDAD persistida (el DTO de vista no expone HMAC).
        val stored = store.findAllNotifications().single()
        // Solo HMAC:64 hex, distinto del código y determinístico por secreto.
        val hmac = stored.securityCodeHmac
        assertTrue(hmac != null && hmac.length == 64)
        assertNotEquals("381", hmac)
        // Nombre guardado normalizado, no el texto original con mayúsculas.
        assertEquals("persona uno", stored.payerName)
        // Ningún campo en claro contiene el código.
        listOf(
            stored.id,
            stored.payerName ?: "",
            stored.sourcePackage,
            stored.sourceType.name,
            stored.matchStatus.name,
            stored.matchedPaymentId ?: "",
        ).forEach { field -> assertFalse(field.contains("381"), "campo con código plano: $field") }
    }

    @Test
    fun `reinicio conserva eventos ambiguos y la resolucion sigue funcionando`(@TempDir temp: Path) {
        val db = temp.resolve("yape-model2.db")

        val store1 = SqliteYapeLabStore(db)
        val first = YapeWaitService(allowedPackage = allowedPackage, store = store1, matchSecret = "test-secret")
        val w1 = first.createWait(700)
        val w2 = first.createWait(700)
        first.labInject(YapeLabInjectRequest(700, payerName = "Persona Uno", securityCode = "381"))
        assertEquals("AMBIGUOUS", first.notifications().single().matchStatus)
        // Simula apagado: se cierra el store antes de "reiniciar".
        store1.close()

        val store2 = SqliteYapeLabStore(db)
        try {
            val second = YapeWaitService(allowedPackage = allowedPackage, store = store2, matchSecret = "test-secret")
            // Las ventas y el evento ambiguo sobreviven al reinicio.
            assertEquals(2, second.listWaits().size)
            assertTrue(second.listWaits().all { it.status == "WAITING" })
            assertEquals("AMBIGUOUS", second.notifications().single().matchStatus)
            assertTrue(second.state.value.ambiguous != null)
            // Los IDs persistidos se conservan y la secuencia continúa sin colisionar.
            assertEquals(listOf(w1.id, w2.id), second.listWaits().map { it.id })
            val extra = second.createWait(1200)
            assertTrue(extra.id != w1.id && extra.id != w2.id)

            val resolved = second.resolve(second.listWaits().first { it.amountMinor == 700L }.id, "381", null)
            assertEquals("PAID", resolved.status)
            assertEquals(1, second.notifications().count { it.matchStatus == "MATCHED" })
        } finally {
            store2.close()
        }
    }
}
