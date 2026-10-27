package pe.willian.yapebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.willian.yapebridge.util.ExtrasConverter

class ExtrasConverterTest {

    @Test
    fun `valueToString returns null for null`() {
        assertNull(ExtrasConverter.valueToString(null))
    }

    @Test
    fun `valueToString keeps plain strings`() {
        assertEquals("Hola", ExtrasConverter.valueToString("Hola"))
    }

    @Test
    fun `valueToString handles CharSequence`() {
        val cs: CharSequence = StringBuilder("Yape pago")
        assertEquals("Yape pago", ExtrasConverter.valueToString(cs))
    }

    @Test
    fun `valueToString handles numbers and booleans`() {
        assertEquals("42", ExtrasConverter.valueToString(42))
        assertEquals("true", ExtrasConverter.valueToString(true))
    }

    @Test
    fun `valueToString handles arrays and collections`() {
        assertEquals("[a, b]", ExtrasConverter.valueToString(arrayOf("a", "b")))
        assertEquals("[1, 2]", ExtrasConverter.valueToString(listOf(1, 2)))
    }

    @Test
    fun `valueToString truncates very long values`() {
        val long = "x".repeat(ExtrasConverter.MAX_VALUE_LENGTH + 50)
        val out = ExtrasConverter.valueToString(long)!!
        assertTrue(out.endsWith("(truncated)"))
        assertTrue(out.length <= ExtrasConverter.MAX_VALUE_LENGTH + 20)
    }

    @Test
    fun `valueToString never throws on hostile toString`() {
        val hostile = object {
            override fun toString(): String = throw RuntimeException("boom")
        }
        val out = ExtrasConverter.valueToString(hostile)
        assertTrue(out!!.startsWith("<unprintable:"))
    }

    @Test
    fun `sanitizeExtrasMap drops blank keys and null values`() {
        val input = mapOf(
            "android.title" to "Pago",
            "" to "blank-key",
            "   " to "blank-key-2",
            "null-value" to null,
            "android.text" to "Monto S/ 10.00"
        )
        val out = ExtrasConverter.sanitizeExtrasMap(input)
        assertEquals(
            mapOf("android.title" to "Pago", "android.text" to "Monto S/ 10.00"),
            out
        )
    }

    @Test
    fun `sanitizeExtrasMap returns empty for empty input`() {
        assertTrue(ExtrasConverter.sanitizeExtrasMap(emptyMap()).isEmpty())
    }
}
