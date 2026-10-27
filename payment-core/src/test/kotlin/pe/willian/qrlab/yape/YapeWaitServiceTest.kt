package pe.willian.qrlab.yape

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Tests sintéticos de la detección Yape (base del Modelo 1: auto match con
 * exactamente un candidato; convivencia de ventas del Modelo 2).
 * Sin Android, sin datos privados, sin APIs de Yape.
 */
class YapeWaitServiceTest {

    private val allowedPackage = "pe.willian.qrlab.fakeyape"

    private fun service(packageName: String? = allowedPackage) =
        YapeWaitService(allowedPackage = packageName)

    private fun notification(
        packageName: String,
        vararg texts: String?,
    ): YapeRawNotificationRequest {
        val (title, text, bigText, subText, summaryText) = listOf<String?>(
            texts.getOrNull(0),
            texts.getOrNull(1),
            texts.getOrNull(2),
            texts.getOrNull(3),
            texts.getOrNull(4),
        )
        return YapeRawNotificationRequest(
            packageName = packageName,
            postedAt = "2026-09-21T12:00:00Z",
            title = title,
            text = text,
            bigText = bigText,
            subText = subText,
            summaryText = summaryText,
        )
    }

    // --- 1. Espera única -----------------------------------------------------

    @Test
    fun `crear espera devuelve WAITING con display`() {
        val wait = service().createWait(700)
        assertEquals("WAITING", wait.status)
        assertEquals(700, wait.amountMinor)
        assertEquals("S/ 7.00", wait.amountDisplay)
        assertTrue(wait.id.startsWith("YW-"))
    }

    @Test
    fun `monto cero o negativo no crea espera`() {
        val yape = service()
        assertThrows<IllegalArgumentException> { yape.createWait(0) }
        assertThrows<IllegalArgumentException> { yape.createWait(-100) }
        assertNull(yape.current())
    }

    @Test
    fun `crear una nueva espera la mantiene WAITING junto a la anterior`() {
        // MODELO 2: varias ventas coexisten; crear una NO cancela la otra
        // (esto reemplaza la limitación de espera única del Modelo 1).
        val yape = service()
        val first = yape.createWait(700)
        val second = yape.createWait(500)

        assertNotEquals(first.id, second.id)
        assertEquals(second.id, yape.current()?.id)
        assertEquals("WAITING", yape.find(first.id)?.status)
        assertEquals("WAITING", yape.find(second.id)?.status)
        assertEquals(2, yape.listWaits().size)
    }

    // --- 2. Matching ---------------------------------------------------------

    @Test
    fun `package correcto y monto correcto marca PAID`() {
        val yape = service()
        val wait = yape.createWait(700)

        val debug = yape.receiveRaw(notification(allowedPackage, "Yape", "Has recibido S/ 7.00"))

        assertEquals(YapeMatchResult.PAYMENT_MATCHED, debug.result)
        val current = yape.find(wait.id)!!
        assertEquals("PAID", current.status)
        assertTrue(current.receivedAt != null)
        assertEquals(allowedPackage, current.sourcePackage)
    }

    @Test
    fun `monto incorrecto no marca PAID`() {
        val yape = service()
        val wait = yape.createWait(700)

        val debug = yape.receiveRaw(notification(allowedPackage, "Yape", "Has recibido S/ 50.00"))

        assertEquals(YapeMatchResult.AMOUNT_MISMATCH, debug.result)
        assertEquals("WAITING", yape.find(wait.id)?.status)
    }

    @Test
    fun `package incorrecto no marca PAID`() {
        val yape = service()
        val wait = yape.createWait(700)

        val debug = yape.receiveRaw(notification("com.otra.app", "Yape", "Has recibido S/ 7.00"))

        assertEquals(YapeMatchResult.PACKAGE_NOT_ALLOWED, debug.result)
        assertEquals("WAITING", yape.find(wait.id)?.status)
        assertNull(yape.find(wait.id)?.sourcePackage)
    }

    @Test
    fun `sin YAPE_ALLOWED_PACKAGE devuelve PACKAGE_NOT_CONFIGURED y no marca PAID`() {
        val yape = service(packageName = null)
        val wait = yape.createWait(700)

        val debug = yape.receiveRaw(notification("cualquier.package", "S/ 7.00"))

        assertEquals(YapeMatchResult.PACKAGE_NOT_CONFIGURED, debug.result)
        assertEquals("cualquier.package", debug.packageName)
        assertEquals("WAITING", yape.find(wait.id)?.status)
    }

    @Test
    fun `notificacion sin montos candidatos devuelve AMOUNT_NOT_FOUND`() {
        val yape = service()
        val wait = yape.createWait(700)

        val debug = yape.receiveRaw(notification(allowedPackage, "Transferencia recibida"))

        assertEquals(YapeMatchResult.AMOUNT_NOT_FOUND, debug.result)
        assertEquals("WAITING", yape.find(wait.id)?.status)
    }

    @Test
    fun `sin espera activa la notificacion se ignora para matching`() {
        val yape = service()

        val debug = yape.receiveRaw(notification(allowedPackage, "S/ 7.00"))

        assertEquals(YapeMatchResult.NO_ACTIVE_WAIT, debug.result)
        assertNull(yape.current())
    }

    @Test
    fun `evento duplicado no vuelve a procesar el pago`() {
        val yape = service()
        val wait = yape.createWait(700)
        val first = yape.receiveRaw(notification(allowedPackage, "S/ 7.00"))
        val receivedAt = yape.find(wait.id)?.receivedAt

        val second = yape.receiveRaw(notification(allowedPackage, "S/ 7.00"))

        assertEquals(YapeMatchResult.PAYMENT_MATCHED, first.result)
        assertEquals(YapeMatchResult.ALREADY_PAID, second.result)
        assertEquals("PAID", yape.find(wait.id)?.status)
        assertEquals(receivedAt, yape.find(wait.id)?.receivedAt)
    }

    // --- 3. Estado / eventos -------------------------------------------------

    @Test
    fun `estado publica espera y ultimo resultado sin raw text`() = runBlocking {
        val yape = service()
        val wait = yape.createWait(700)

        yape.receiveRaw(notification(allowedPackage, "S/ 7.00"))

        val state = yape.state.value
        assertEquals(wait.id, state.wait?.id)
        assertEquals("PAID", state.wait?.status)
        assertEquals(YapeMatchResult.PAYMENT_MATCHED, state.lastNotification?.result)
        assertEquals(listOf(700L), state.lastNotification?.detectedAmountsMinor)
        assertEquals(listOf("S/ 7.00"), state.lastNotification?.detectedAmountsDisplay)
        // Nunca se expone el texto completo de la notificación en el estado.
        val serialized = Json.encodeToString(YapeStateDto.serializer(), state)
        assertTrue(!serialized.contains("Has recibido"))
    }

    @Test
    fun `flujo de estado emite la espera creada`() = runBlocking {
        val yape = service()
        val created = yape.createWait(700)

        val state = withTimeout(5_000) { yape.state.first { it.wait?.id == created.id } }

        assertEquals("WAITING", state.wait?.status)
    }

    @Test
    fun `flujo de estado refleja PAID tras el matching`() = runBlocking {
        val yape = service()
        yape.createWait(700)

        val paid = async {
            withTimeout(5_000) { yape.state.first { it.wait?.status == "PAID" } }
        }
        delay(200)
        yape.receiveRaw(notification(allowedPackage, "S/ 7.00"))

        assertEquals("PAID", paid.await().wait?.status)
    }

    // --- 4. La pantalla SOLO reacciona al match exacto -----------------------

    @Test
    fun `package correcto con monto diferente no altera la UI y sigue WAITING`() {
        val yape = service()
        yape.createWait(700)
        val before = yape.state.value

        val debug = yape.receiveRaw(notification(allowedPackage, "Yape", "Has recibido S/ 5.00"))

        assertEquals(YapeMatchResult.AMOUNT_MISMATCH, debug.result)
        assertEquals("WAITING", yape.current()?.status)
        // La pantalla no cambia: misma espera, "última notificación" sin tocar.
        assertEquals(before, yape.state.value)
        assertNull(yape.state.value.lastNotification)
        assertEquals(mapOf(YapeMatchResult.AMOUNT_MISMATCH to 1L), yape.ignoredCounts())
    }

    @Test
    fun `package diferente con monto correcto no altera la UI y sigue WAITING`() {
        val yape = service()
        val wait = yape.createWait(700)
        val before = yape.state.value

        val debug = yape.receiveRaw(notification("com.whatsapp", "Yape", "Has recibido S/ 7.00"))

        assertEquals(YapeMatchResult.PACKAGE_NOT_ALLOWED, debug.result)
        assertEquals("WAITING", yape.find(wait.id)?.status)
        assertNull(yape.find(wait.id)?.sourcePackage)
        assertEquals(before, yape.state.value)
        assertNull(yape.state.value.lastNotification)
        assertEquals(mapOf(YapeMatchResult.PACKAGE_NOT_ALLOWED to 1L), yape.ignoredCounts())
    }

    @Test
    fun `package y monto correctos publican PAYMENT_MATCHED en la pantalla`() {
        val yape = service()
        yape.createWait(700)

        val debug = yape.receiveRaw(notification(allowedPackage, "Yape", "Has recibido S/ 7.00"))

        assertEquals(YapeMatchResult.PAYMENT_MATCHED, debug.result)
        val state = yape.state.value
        assertEquals("PAID", state.wait?.status)
        assertEquals(YapeMatchResult.PAYMENT_MATCHED, state.lastNotification?.result)
        assertTrue(yape.ignoredCounts().isEmpty())
    }

    @Test
    fun `varias notificaciones incorrectas seguidas y luego la correcta pagan una sola vez`() {
        val yape = service()
        val wait = yape.createWait(700)

        // WhatsApp con el monto exacto: se ignora por package.
        yape.receiveRaw(notification("com.whatsapp", "Yape", "Has recibido S/ 7.00"))
        // Yape con otro monto: se ignora por monto.
        yape.receiveRaw(notification(allowedPackage, "Yape", "Has recibido S/ 5.00"))
        // Yape sin monto candidato: se ignora igual.
        yape.receiveRaw(notification(allowedPackage, "Transferencia recibida"))

        assertEquals("WAITING", yape.find(wait.id)?.status)
        assertNull(yape.state.value.lastNotification)
        assertEquals(
            mapOf(
                YapeMatchResult.PACKAGE_NOT_ALLOWED to 1L,
                YapeMatchResult.AMOUNT_MISMATCH to 1L,
                YapeMatchResult.AMOUNT_NOT_FOUND to 1L,
            ),
            yape.ignoredCounts(),
        )

        // Ahora la notificación correcta: paga y se publica UNA vez.
        val matched = yape.receiveRaw(notification(allowedPackage, "Yape", "Has recibido S/ 7.00"))
        val paidState = yape.state.value

        assertEquals(YapeMatchResult.PAYMENT_MATCHED, matched.result)
        assertEquals("PAID", yape.find(wait.id)?.status)
        assertEquals(YapeMatchResult.PAYMENT_MATCHED, paidState.lastNotification?.result)

        // El duplicado ya no vuelve a pagar ni mueve la pantalla.
        val duplicate = yape.receiveRaw(notification(allowedPackage, "Yape", "Has recibido S/ 7.00"))
        assertEquals(YapeMatchResult.ALREADY_PAID, duplicate.result)
        assertEquals(paidState, yape.state.value)
        assertEquals(4, yape.ignoredCounts().values.sum())
    }

    @Test
    fun `despues de PAID ninguna notificacion vuelve a alterar la pantalla`() {
        val yape = service()
        val wait = yape.createWait(700)
        yape.receiveRaw(notification(allowedPackage, "S/ 7.00"))
        val paidState = yape.state.value
        val receivedAt = yape.find(wait.id)?.receivedAt

        yape.receiveRaw(notification(allowedPackage, "S/ 7.00")) // duplicado
        yape.receiveRaw(notification(allowedPackage, "S/ 9.00")) // monto otro
        yape.receiveRaw(notification("com.otra.app", "S/ 7.00")) // package otro

        assertEquals("PAID", yape.find(wait.id)?.status)
        assertEquals(receivedAt, yape.find(wait.id)?.receivedAt)
        assertEquals(paidState, yape.state.value)
        assertEquals(YapeMatchResult.PAYMENT_MATCHED, yape.state.value.lastNotification?.result)
        assertEquals(3, yape.ignoredCounts().values.sum())
    }

    @Test
    fun `log de eventos ignorados tiene formato minimo sin texto privado`() {
        val yape = service()
        yape.createWait(700)

        val output = captureStdout {
            yape.receiveRaw(notification("com.otra.app", "Yape", "Juan te envió S/ 7.00"))
            yape.receiveRaw(notification(allowedPackage, "Yape", "Juan te envió S/ 5.00"))
            yape.receiveRaw(notification(allowedPackage, "Yape", "Has recibido S/ 7.00"))
        }

        assertTrue(output.contains("YAPE BRIDGE | ignored package=com.otra.app"))
        assertTrue(output.contains("YAPE BRIDGE | ignored amount=500 expected=700"))
        assertTrue(output.contains("YAPE BRIDGE | packageName=$allowedPackage"))
        assertTrue(output.contains("result=PAYMENT_MATCHED"))
        // Nunca el texto privado de la notificación.
        assertFalse(output.contains("Juan"))
        assertFalse(output.contains("envió"))
    }

    /** Captura System.out mientras corre [block] (println de JVM). */
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
}
