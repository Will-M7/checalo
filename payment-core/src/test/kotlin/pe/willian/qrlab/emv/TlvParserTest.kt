package pe.willian.qrlab.emv

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pruebas del parser TLV genérico con muestras 100 % sintéticas
 * (sin datos financieros ni personales reales).
 */
class TlvParserTest {

    @Test
    fun `TLV simple 5802PE`() {
        val result = TlvParser.parse("5802PE")
        assertTrue(result is TlvParseResult.Success)
        val fields = (result as TlvParseResult.Success).fields
        assertEquals(1, fields.size)
        assertEquals(TlvField(tag = "58", length = 2, value = "PE", startOffset = 0, endOffsetExclusive = 6), fields[0])
        assertNull(fields[0].children)
    }

    @Test
    fun `varios TLV consecutivos conservan offsets`() {
        val result = TlvParser.parse("5802PE6004Lima")
        assertTrue(result is TlvParseResult.Success)
        val fields = (result as TlvParseResult.Success).fields
        assertEquals(2, fields.size)
        assertEquals("58", fields[0].tag)
        assertEquals("PE", fields[0].value)
        assertEquals(0, fields[0].startOffset)
        assertEquals(6, fields[0].endOffsetExclusive)
        assertEquals("60", fields[1].tag)
        assertEquals(4, fields[1].length)
        assertEquals("Lima", fields[1].value)
        assertEquals(6, fields[1].startOffset)
        assertEquals(14, fields[1].endOffsetExclusive)
    }

    @Test
    fun `longitud no numerica es error descriptivo`() {
        val result = TlvParser.parse("58ABPE")
        assertTrue(result is TlvParseResult.Failure)
        val failure = result as TlvParseResult.Failure
        assertEquals(2, failure.offset)
        assertTrue(failure.message.contains("longitud inválida"), "mensaje: ${failure.message}")
    }

    @Test
    fun `longitud declarada mayor que lo disponible es truncado`() {
        val result = TlvParser.parse("5803PE")
        assertTrue(result is TlvParseResult.Failure)
        val failure = result as TlvParseResult.Failure
        assertEquals(0, failure.offset)
        assertTrue(failure.message.contains("truncado"), "mensaje: ${failure.message}")
    }

    @Test
    fun `payload truncado sin valor completo`() {
        val result = TlvParser.parse("5802P")
        assertTrue(result is TlvParseResult.Failure)
        val failure = result as TlvParseResult.Failure
        assertTrue(failure.message.contains("truncado"), "mensaje: ${failure.message}")
    }

    @Test
    fun `resto corto que no forma TLV no se ignora`() {
        val result = TlvParser.parse("5802PE6")
        assertTrue(result is TlvParseResult.Failure)
        val failure = result as TlvParseResult.Failure
        assertEquals(6, failure.offset)
    }

    @Test
    fun `payload vacio es error`() {
        val result = TlvParser.parse("")
        assertTrue(result is TlvParseResult.Failure)
    }
}
