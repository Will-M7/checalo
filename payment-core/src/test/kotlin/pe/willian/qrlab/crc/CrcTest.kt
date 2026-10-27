package pe.willian.qrlab.crc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** CRC-16/CCITT-FALSE: vector canónico y formato de salida. */
class CrcTest {

    @Test
    fun `vector canonico 123456789 es 0x29B1`() {
        val crc = Crc16CcittFalse.compute("123456789".toByteArray(Charsets.US_ASCII))
        assertEquals(0x29B1, crc)
    }

    @Test
    fun `computeHex devuelve 4 hexadecimales en mayusculas`() {
        assertEquals("29B1", Crc16CcittFalse.computeHex("123456789"))
    }

    @Test
    fun `cadena vacia devuelve init FFFF`() {
        assertEquals("FFFF", Crc16CcittFalse.computeHex(""))
    }
}
