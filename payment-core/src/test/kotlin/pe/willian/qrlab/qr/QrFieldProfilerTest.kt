package pe.willian.qrlab.qr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Perfilador descriptivo con fixtures 100 % sintéticos (sin datos reales).
 * Solo verifica cómputo local: Base64, bytes, prefijos y apariencia numérica.
 */
class QrFieldProfilerTest {

    @Test
    fun `Base64 valido de 32 bytes informa bytes y sha256`() {
        // 32 bytes en cero -> 44 caracteres Base64 con un padding '='.
        val base64of32Zeros = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

        val profile = QrFieldProfiler.profile(base64of32Zeros)

        assertTrue(profile.isBase64)
        assertEquals(CharClass.OTHER, profile.charClass)
        assertTrue(profile.isLikelyBase64Shaped)
        assertEquals(44, profile.charLength)
        assertEquals(32, profile.decodedByteLength)
        assertTrue(profile.decodedSha256Hex != null && profile.decodedSha256Hex.length == 64)
    }

    @Test
    fun `numeric-only no se presenta prioritariamente como Base64`() {
        val profile = QrFieldProfiler.profile("12345678")

        assertEquals(CharClass.NUMERIC, profile.charClass)
        assertTrue(profile.isBase64SyntaxCompatible)
        assertFalse(profile.isLikelyBase64Shaped)
    }

    @Test
    fun `patron numerico generico sin inferir fecha`() {
        val pattern = QrFieldProfiler.numericPattern(listOf("123451", "123481", "123471"))

        assertTrue(pattern != null)
        assertEquals(4, pattern!!.prefixLength)
        assertEquals("1234", pattern.prefix)
        assertEquals(1, pattern.suffixLength)
        assertEquals("1", pattern.suffix)
        assertEquals(listOf(0, 1, 2, 3, 5), pattern.constantPositions)
        assertEquals(listOf(4), pattern.variablePositions)
    }

    @Test
    fun `Base64 invalido no decodifica`() {
        val profile = QrFieldProfiler.profile("!!!no-base64!!!")

        assertFalse(profile.isBase64)
        assertNull(profile.decodedByteLength)
        assertNull(profile.decodedSha256Hex)
    }

    @Test
    fun `prefijo comun entre valores`() {
        val result = QrFieldProfiler.commonPrefix(listOf("ABC123", "ABC456", "ABC789"))

        assertEquals(3, result.prefixLength)
        assertEquals("ABC", result.prefix)
        assertEquals(listOf("123", "456", "789"), result.suffixes)
    }

    @Test
    fun `valores sin prefijo comun`() {
        val result = QrFieldProfiler.commonPrefix(listOf("ABC", "XYZ"))

        assertEquals(0, result.prefixLength)
        assertEquals("", result.prefix)
        assertEquals(listOf("ABC", "XYZ"), result.suffixes)
    }

    @Test
    fun `apariencia numerica no convierte a fecha`() {
        assertEquals(
            "POSSIBLE_STRUCTURED_NUMERIC_VALUE",
            QrFieldProfiler.numericAppearanceHint("20010101"),
        )
        assertNull(QrFieldProfiler.numericAppearanceHint("ABC"))
    }
}
