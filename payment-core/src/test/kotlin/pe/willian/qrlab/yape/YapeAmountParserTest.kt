package pe.willian.qrlab.yape

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests sintéticos del parser de montos. Solo texto de laboratorio:
 * sin notificaciones reales ni datos privados.
 */
class YapeAmountParserTest {

    @Test
    fun `monto con espacio S soles 7 devuelve 700`() {
        assertEquals(listOf(700L), YapeAmountParser.parse(text = "Has recibido S/ 7"))
    }

    @Test
    fun `monto sin espacio S soles 7 devuelve 700`() {
        assertEquals(listOf(700L), YapeAmountParser.parse(text = "Has recibido S/7"))
    }

    @Test
    fun `monto con espacio S soles 7 punto 00 devuelve 700`() {
        assertEquals(listOf(700L), YapeAmountParser.parse(text = "S/ 7.00"))
    }

    @Test
    fun `monto sin espacio S soles 7 punto 00 devuelve 700`() {
        assertEquals(listOf(700L), YapeAmountParser.parse(text = "S/7.00"))
    }

    @Test
    fun `monto con espacio S soles 7 coma 00 devuelve 700`() {
        assertEquals(listOf(700L), YapeAmountParser.parse(text = "S/ 7,00"))
    }

    @Test
    fun `monto sin espacio S soles 7 coma 00 devuelve 700`() {
        assertEquals(listOf(700L), YapeAmountParser.parse(text = "S/7,00"))
    }

    @Test
    fun `monto S soles 10 punto 50 devuelve 1050`() {
        assertEquals(listOf(1050L), YapeAmountParser.parse(text = "S/ 10.50"))
    }

    @Test
    fun `monto S soles 10 coma 50 devuelve 1050`() {
        assertEquals(listOf(1050L), YapeAmountParser.parse(text = "S/10,50"))
    }

    @Test
    fun `combina title text bigText subText summaryText`() {
        val amounts = YapeAmountParser.parse(
            title = "Yape",
            text = "S/ 7.00",
            bigText = "Transferencia de S/12.30",
            subText = "S/ 7",
            summaryText = "hoy",
        )
        assertEquals(listOf(700L, 1230L, 700L), amounts)
    }

    @Test
    fun `sin montos candidatos devuelve lista vacia`() {
        assertTrue(YapeAmountParser.parse(text = "Transferencia recibida hoy").isEmpty())
        assertTrue(YapeAmountParser.parse(title = null, text = null, bigText = null).isEmpty())
    }

    @Test
    fun `texto sin prefijo S no genera candidatos`() {
        assertTrue(YapeAmountParser.parse(text = "7.00 soles").isEmpty())
    }
}
