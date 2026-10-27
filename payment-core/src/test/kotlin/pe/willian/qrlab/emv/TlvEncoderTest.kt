package pe.willian.qrlab.emv

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** TlvEncoder sintético: round-trip con TlvParser y rechazo sin truncar. */
class TlvEncoderTest {

    @Test
    fun `round-trip con TlvParser`() {
        val payload = TlvEncoder.encode("54", "700") + TlvEncoder.encode("59", "QR LAB S/ 7.00")

        val result = TlvParser.parse(payload)

        assertEquals(TlvParseResult.Success::class, result::class)
        val fields = (result as TlvParseResult.Success).fields
        assertEquals("700", fields.first { it.tag == "54" }.value)
        assertEquals("QR LAB S/ 7.00", fields.first { it.tag == "59" }.value)
    }

    @Test
    fun `rechaza valores demasiado largos sin truncar`() {
        assertThrows(IllegalArgumentException::class.java) {
            TlvEncoder.encode("59", "X".repeat(100))
        }
    }

    @Test
    fun `rechaza tag invalido`() {
        assertThrows(IllegalArgumentException::class.java) {
            TlvEncoder.encode("6A", "PE")
        }
    }
}
