package pe.willian.qrlab.yape

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests de resolución de casos ambiguos (MODELO 2): por código HMAC y por
 * nombre exacto normalizado. Sin fuzzy matching, sin datos privados reales.
 */
class YapeResolveTest {

    private val allowedPackage = "pe.willian.qrlab.fakeyape"

    private fun service() = YapeWaitService(
        allowedPackage = allowedPackage,
        matchSecret = "test-secret",
        matchWindowSeconds = 600,
    )

    /** Dos ventas de S/7.00 + una notificación ambigua CON código. */
    private fun ambiguousWithCode(yape: YapeWaitService): Pair<YapeWaitCreatedDto, YapeWaitCreatedDto> {
        val w1 = yape.createWait(700)
        val w2 = yape.createWait(700)
        yape.labInject(YapeLabInjectRequest(700, securityCode = "381"))
        return w1 to w2
    }

    /** Dos ventas de S/7.00 + notificación ambigua del formato Yape observado (nombre, sin código). */
    private fun ambiguousWithName(yape: YapeWaitService): Pair<YapeWaitCreatedDto, YapeWaitCreatedDto> {
        val w1 = yape.createWait(700)
        val w2 = yape.createWait(700)
        yape.receiveRaw(
            YapeRawNotificationRequest(
                packageName = allowedPackage,
                postedAt = "2026-09-22T12:00:00Z",
                title = "Confirmación de Pago",
                text = "Yape! JHON MAYTA AROTAYPE te envió un pago por S/ 7.00",
            ),
        )
        return w1 to w2
    }

    // --- Resolución por código -------------------------------------------------

    @Test
    fun `codigo correcto paga solo una de las dos ventas`() {
        val yape = service()
        val (w1, w2) = ambiguousWithCode(yape)

        val resolved = yape.resolve(w1.id, "381", null)

        assertEquals("PAID", resolved.status)
        assertEquals("WAITING", yape.find(w2.id)?.status)
        assertEquals(1, yape.notifications().count { it.matchStatus == "MATCHED" })
        assertEquals(w1.id, yape.notifications().single().matchedPaymentId)
        // El panel ambiguo desaparece tras resolver.
        assertNull(yape.state.value.ambiguous)
    }

    @Test
    fun `codigo incorrecto no paga ninguna y no revela el esperado`() {
        val yape = service()
        val (w1, w2) = ambiguousWithCode(yape)

        val error = assertThrows<IllegalStateException> { yape.resolve(w1.id, "999", null) }

        assertFalse((error.message ?: "").contains("381"))
        assertEquals("WAITING", yape.find(w1.id)?.status)
        assertEquals("WAITING", yape.find(w2.id)?.status)
        assertTrue(yape.state.value.waits.none { it.status == "PAID" })
        assertEquals("AMBIGUOUS", yape.notifications().single().matchStatus)
    }

    @Test
    fun `codigo no resuelve si la ambigua solo tiene nombre`() {
        val yape = service()
        val (w1, _) = ambiguousWithName(yape)

        assertThrows<IllegalStateException> { yape.resolve(w1.id, "381", null) }
        assertEquals("WAITING", yape.find(w1.id)?.status)
    }

    @Test
    fun `codigo con formato invalido se rechaza`() {
        val yape = service()
        val (w1, _) = ambiguousWithCode(yape)

        assertThrows<IllegalArgumentException> { yape.resolve(w1.id, "12", null) }
        assertThrows<IllegalArgumentException> { yape.resolve(w1.id, "1234", null) }
        assertEquals("WAITING", yape.find(w1.id)?.status)
    }

    // --- Resolución por nombre --------------------------------------------------

    @Test
    fun `nombre correcto sin codigo paga una venta`() {
        val yape = service()
        val (w1, w2) = ambiguousWithName(yape)
        assertTrue(yape.state.value.ambiguous?.requiresPayerName == true)

        val resolved = yape.resolve(w1.id, null, " jhon  MAYTA arotaype ")

        assertEquals("PAID", resolved.status)
        assertEquals("WAITING", yape.find(w2.id)?.status)
        assertEquals("jhon mayta arotaype", yape.notifications().single().payerName)
    }

    @Test
    fun `nombre incorrecto no paga ninguna`() {
        val yape = service()
        val (w1, w2) = ambiguousWithName(yape)

        assertThrows<IllegalStateException> { yape.resolve(w1.id, null, "Otra Persona") }

        assertEquals("WAITING", yape.find(w1.id)?.status)
        assertEquals("WAITING", yape.find(w2.id)?.status)
    }

    @Test
    fun `fuzzy y parcial NO coinciden`() {
        val yape = service()
        val (w1, _) = ambiguousWithName(yape)

        // Sin Levenshtein ni parecidos.
        assertThrows<IllegalStateException> { yape.resolve(w1.id, null, "jon mayta arotaype") }
        // Sin contains parcial.
        assertThrows<IllegalStateException> { yape.resolve(w1.id, null, "mayta") }
        assertThrows<IllegalStateException> { yape.resolve(w1.id, null, "jhon mayta") }
        assertEquals("WAITING", yape.find(w1.id)?.status)
    }

    @Test
    fun `acentos mayusculas y espacios se normalizan`() {
        val yape = service()
        val w1 = yape.createWait(700)
        yape.createWait(700)
        yape.labInject(YapeLabInjectRequest(700, payerName = "José Pérez", securityCode = null))

        val resolved = yape.resolve(w1.id, null, "  JOSE   PEREZ ")

        assertEquals("PAID", resolved.status)
        assertEquals("jose perez", yape.notifications().single().payerName)
    }

    @Test
    fun `nombre no resuelve si la ambigua tiene codigo`() {
        val yape = service()
        val (w1, _) = ambiguousWithCode(yape)

        // Solo notificaciones SIN código participan por nombre (§8).
        assertThrows<IllegalStateException> { yape.resolve(w1.id, null, "persona uno") }
        assertEquals("WAITING", yape.find(w1.id)?.status)
    }

    // --- Validaciones de entrada ------------------------------------------------

    @Test
    fun `resolve exige al menos un dato`() {
        val yape = service()
        val (w1, _) = ambiguousWithCode(yape)

        assertThrows<IllegalArgumentException> { yape.resolve(w1.id, null, null) }
        assertThrows<IllegalArgumentException> { yape.resolve(w1.id, "  ", "   ") }
        assertEquals("WAITING", yape.find(w1.id)?.status)
    }

    @Test
    fun `resolve de venta inexistente lanza no encontrado`() {
        val yape = service()
        assertThrows<NoSuchElementException> { yape.resolve("YW-999999", "381", null) }
    }

    @Test
    fun `venta pagada ya no admite otra resolucion`() {
        val yape = service()
        val (w1, _) = ambiguousWithCode(yape)
        yape.resolve(w1.id, "381", null)

        val error = assertThrows<IllegalStateException> { yape.resolve(w1.id, "381", null) }
        assertTrue((error.message ?: "").contains("WAITING"))
        assertEquals(1, yape.state.value.waits.count { it.status == "PAID" })
    }
}
