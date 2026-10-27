package pe.willian.qrlab.emv

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Detección de TLV anidado: solo cuando TODO el value parsea como TLV.
 * Muestras sintéticas, sin datos reales.
 */
class NestedTlvTest {

    @Test
    fun `TLV anidado valido expone children`() {
        // Interior sintético: tag 00, longitud 2, valor "AB".
        val inner = "0002AB"
        // Exterior: tag 26, longitud 6, valor = interior completo.
        val payload = "2606$inner"
        assertEquals("26060002AB", payload)

        val result = TlvParser.parse(payload)
        assertTrue(result is TlvParseResult.Success)
        val outer = (result as TlvParseResult.Success).fields.single()
        assertEquals("26", outer.tag)
        assertEquals(inner, outer.value)
        assertNotNull(outer.children)
        assertTrue(outer.isNested)
        val children = outer.children!!
        assertEquals(1, children.size)
        assertEquals("00", children[0].tag)
        assertEquals("AB", children[0].value)
    }

    @Test
    fun `falso anidado queda como dato opaco`() {
        // "12345678" empieza con dígitos que parecen tag/longitud ("12"/"34"),
        // pero declara 34 caracteres y solo hay 4: no es TLV completo.
        val payload = "590812345678"

        val result = TlvParser.parse(payload)
        assertTrue(result is TlvParseResult.Success)
        val field = (result as TlvParseResult.Success).fields.single()
        assertEquals("59", field.tag)
        assertEquals("12345678", field.value)
        assertNull(field.children, "un anidado parcial no debe inferirse")
    }

    @Test
    fun `valor no numerico nunca es anidado`() {
        val result = TlvParser.parse("5802PE")
        assertTrue(result is TlvParseResult.Success)
        val field = (result as TlvParseResult.Success).fields.single()
        assertNull(field.children)
    }
}
