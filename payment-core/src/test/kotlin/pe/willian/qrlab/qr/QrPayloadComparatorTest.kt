package pe.willian.qrlab.qr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Comparador de payloads ya parseados. Solo fixtures sintéticos,
 * sin datos reales ni conocimiento de proveedores.
 */
class QrPayloadComparatorTest {

    private fun analyze(payload: String) = QrPayloadAnalyzer.analyze(payload)

    @Test
    fun `payloads identicos resultan iguales`() {
        val comparison = QrPayloadComparator.compare(
            analyze("0002015802PE"),
            analyze("0002015802PE"),
            "A",
            "B",
        )

        assertEquals(2, comparison.fields.size)
        assertTrue(comparison.fields.all { it.presence == FieldPresence.IN_BOTH_EQUAL })
    }

    @Test
    fun `mismo tag con distinto valor se marca diferente`() {
        val comparison = QrPayloadComparator.compare(
            analyze("5802PE"),
            analyze("5802US"),
            "A",
            "B",
        )

        val field = comparison.fields.single()
        assertEquals("58", field.tag)
        assertEquals(FieldPresence.IN_BOTH_DIFFERENT, field.presence)
        assertEquals("PE", field.valueA)
        assertEquals("US", field.valueB)
    }

    @Test
    fun `tags exclusivos se detectan por lado`() {
        val comparison = QrPayloadComparator.compare(
            analyze("000201"),
            analyze("0002016004Lima"),
            "A",
            "B",
        )

        val byTag = comparison.fields.associateBy { it.tag }
        assertEquals(FieldPresence.IN_BOTH_EQUAL, byTag["00"]?.presence)
        assertEquals(FieldPresence.ONLY_IN_B, byTag["60"]?.presence)
        assertEquals("Lima", byTag["60"]?.valueB)
        assertNull(byTag["60"]?.valueA)
    }

    @Test
    fun `diferencias anidadas se detectan recursivamente`() {
        // Mismo tag 26 con interiores distintos: "0002AB" vs "0002CD".
        val comparison = QrPayloadComparator.compare(
            analyze("26060002AB"),
            analyze("26060002CD"),
            "A",
            "B",
        )

        val outer = comparison.fields.single()
        assertEquals(FieldPresence.IN_BOTH_DIFFERENT, outer.presence)
        val nested = outer.nested
        assertTrue(nested != null && nested.size == 1)
        assertEquals("00", nested!![0].tag)
        assertEquals(FieldPresence.IN_BOTH_DIFFERENT, nested[0].presence)
    }

    @Test
    fun `tabla contiene etiquetas y marcas`() {
        val report = QrPayloadComparator.render(
            QrPayloadComparator.compare(analyze("5802PE"), analyze("5802US"), "A", "B"),
        )

        assertTrue(report.startsWith("QR PAYLOAD COMPARISON"))
        assertTrue(report.contains("58"))
        assertTrue(report.contains("!="))
    }
}
