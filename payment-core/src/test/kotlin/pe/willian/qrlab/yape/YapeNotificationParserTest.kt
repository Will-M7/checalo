package pe.willian.qrlab.yape

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests sintéticos del parser extendido (MODELO 2).
 * Formato Yape observado en laboratorio; nada de datos privados reales.
 */
class YapeNotificationParserTest {

    private fun texts(
        title: String? = null,
        text: String? = null,
        bigText: String? = null,
        subText: String? = null,
        summaryText: String? = null,
    ) = YapeNotificationParser.NotificationTexts(title, text, bigText, subText, summaryText)

    @Test
    fun `formato yape observado extrae nombre monto y origen nativo`() {
        val parsed = YapeNotificationParser.parse(
            texts(
                title = "Confirmación de Pago",
                text = "Yape! JHON MAYTA AROTAYPE te envió un pago por S/ 1.00",
            ),
        )

        assertEquals(listOf(100L), parsed.amounts)
        assertEquals("JHON MAYTA AROTAYPE", parsed.payerName)
        assertNull(parsed.securityCode)
        assertEquals(YapeSourceType.YAPE_NATIVE, parsed.sourceType)
    }

    @Test
    fun `codigo explicito de tres digitos se extrae`() {
        val parsed = YapeNotificationParser.parse(
            texts(text = "Yape! Ana Torres te envió un pago por S/ 7.00. Código de seguridad: 381"),
        )

        assertEquals("381", parsed.securityCode)
        assertEquals(YapeSourceType.YAPE_NATIVE, parsed.sourceType)
        assertEquals(listOf(700L), parsed.amounts)
    }

    @Test
    fun `un monto no se confunde con codigo de seguridad`() {
        val parsed = YapeNotificationParser.parse(
            texts(text = "Yape! Ana Torres te envió un pago por S/ 381"),
        )

        assertNull(parsed.securityCode)
        assertEquals(listOf(38100L), parsed.amounts)
    }

    @Test
    fun `formato desconocido queda en UNKNOWN sin inventar bancos`() {
        val parsed = YapeNotificationParser.parse(
            texts(title = "Aviso", text = "Transferencia recibida exitosamente"),
        )

        assertNull(parsed.payerName)
        assertNull(parsed.securityCode)
        assertTrue(parsed.amounts.isEmpty())
        assertEquals(YapeSourceType.UNKNOWN, parsed.sourceType)
    }

    @Test
    fun `el patron del nombre es configurable y testeable`() {
        val parsed = YapeNotificationParser.parse(
            texts(text = "De: Maria Lopez, monto S/ 5.00"),
            payerPattern = Regex("""De: (.+?),"""),
        )

        assertEquals("Maria Lopez", parsed.payerName)
        assertEquals(listOf(500L), parsed.amounts)
    }

    @Test
    fun `el patron del codigo es configurable`() {
        val parsed = YapeNotificationParser.parse(
            texts(text = "verificacion code=123 ok"),
            securityCodePattern = Regex("""code=(\d{3})"""),
        )

        assertEquals("123", parsed.securityCode)
        assertEquals(YapeSourceType.YAPE_NATIVE, parsed.sourceType)
    }

    @Test
    fun `los montos siguen saliendo de YapeAmountParser sin cambios`() {
        val parsed = YapeNotificationParser.parse(
            texts(
                title = "Confirmación de Pago",
                text = "Yape! Ana te envió un pago por S/ 10.50",
                bigText = "También S/7,00",
            ),
        )

        assertEquals(listOf(1050L, 700L), parsed.amounts)
        assertEquals("Ana", parsed.payerName)
    }
}
