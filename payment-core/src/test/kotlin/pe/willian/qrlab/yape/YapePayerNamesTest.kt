package pe.willian.qrlab.yape

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Normalización exacta del nombre del pagador (sin fuzzy matching). */
class YapePayerNamesTest {

    @Test
    fun `normaliza mayusculas tabs y espacios multiples`() {
        assertEquals("jhon mayta arotaype", YapePayerNames.normalize("  Jhon\t Mayta   Arotaype "))
    }

    @Test
    fun `normaliza acentos por composicion unicode`() {
        assertEquals("jose perez", YapePayerNames.normalize("José Pérez"))
        assertEquals("maria", YapePayerNames.normalize("MARÍA"))
    }

    @Test
    fun `coincide exactamente tras normalizar`() {
        assertTrue(YapePayerNames.matches("JHON MAYTA AROTAYPE", " jhon   mayta arotaype "))
        assertTrue(YapePayerNames.matches("José Pérez", "JOSE PEREZ"))
    }

    @Test
    fun `fuzzy o parcial NO coincide`() {
        // Sin Levenshtein ni parecidos: "jon" != "jhon".
        assertFalse(YapePayerNames.matches("jon mayta", "jhon mayta arotaype"))
        // Sin contains parcial: el fragmento no alcanza.
        assertFalse(YapePayerNames.matches("mayta", "jhon mayta arotaype"))
        assertFalse(YapePayerNames.matches("jhon mayta", "jhon mayta arotaype"))
    }

    @Test
    fun `nulos o vacios nunca coinciden`() {
        assertFalse(YapePayerNames.matches(null, "ana"))
        assertFalse(YapePayerNames.matches("ana", null))
        assertFalse(YapePayerNames.matches("", "   "))
        assertFalse(YapePayerNames.matches(null, null))
    }

    @Test
    fun `normalizar no revienta con basura`() {
        assertEquals("", YapePayerNames.normalize("    "))
        assertEquals("a b", YapePayerNames.normalize(" a \n b "))
    }
}
