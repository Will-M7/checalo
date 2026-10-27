package pe.willian.qrlab.money

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** MoneyFormatter sintético con aritmética entera, sin Double. */
class MoneyFormatterTest {

    @Test
    fun `formatea montos sinteticos`() {
        assertEquals("S/ 5.00", MoneyFormatter.format(500))
        assertEquals("S/ 7.00", MoneyFormatter.format(700))
        assertEquals("S/ 10.50", MoneyFormatter.format(1050))
        assertEquals("S/ 0.00", MoneyFormatter.format(0))
        assertEquals("S/ 0.01", MoneyFormatter.format(1))
    }
}
