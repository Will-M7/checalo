package pe.willian.qrlab.emv

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Diccionario descriptivo básico: significados y decodificaciones conocidas. */
class EmvDictionaryTest {

    @Test
    fun `tags de nivel superior tienen significado`() {
        assertEquals("Payload Format Indicator", EmvDictionary.meaningOf("00"))
        assertEquals("Point of Initiation Method", EmvDictionary.meaningOf("01"))
        assertEquals("Merchant Category Code", EmvDictionary.meaningOf("52"))
        assertEquals("Transaction Currency", EmvDictionary.meaningOf("53"))
        assertEquals("Transaction Amount", EmvDictionary.meaningOf("54"))
        assertEquals("Country Code", EmvDictionary.meaningOf("58"))
        assertEquals("Merchant Name", EmvDictionary.meaningOf("59"))
        assertEquals("Merchant City", EmvDictionary.meaningOf("60"))
        assertEquals("CRC", EmvDictionary.meaningOf("63"))
    }

    @Test
    fun `tag desconocido no tiene significado`() {
        assertNull(EmvDictionary.meaningOf("99"))
    }

    @Test
    fun `Point of Initiation Method STATIC`() {
        assertEquals("STATIC", EmvDictionary.decodeValue("01", "11"))
    }

    @Test
    fun `Point of Initiation Method DYNAMIC`() {
        assertEquals("DYNAMIC", EmvDictionary.decodeValue("01", "12"))
    }

    @Test
    fun `Point of Initiation Method desconocido no decodifica`() {
        assertNull(EmvDictionary.decodeValue("01", "13"))
    }

    @Test
    fun `Currency 604 es PEN`() {
        assertEquals("PEN", EmvDictionary.decodeValue("53", "604"))
    }

    @Test
    fun `Currency desconocida no decodifica`() {
        assertNull(EmvDictionary.decodeValue("53", "840"))
    }
}
