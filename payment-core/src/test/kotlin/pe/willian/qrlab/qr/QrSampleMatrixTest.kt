package pe.willian.qrlab.qr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Matriz de muestras con fixtures 100 % sintéticos (sin datos reales).
 * Verifica clases CONSTANT / PER_GENERATION_VARIABLE / AMOUNT_CORRELATED /
 * DERIVED_CHECKSUM / MISSING_IN_SOME_SAMPLES y duplicados por igualdad exacta.
 */
class QrSampleMatrixTest {

    private fun analyze(payload: String) = QrPayloadAnalyzer.analyze(payload)

    @Test
    fun `clasifica constante correlacionada y variable por generacion`() {
        val a = analyze("00020154035005802PE5903AAA26060002AB")
        val b = analyze("00020154035005802PE5903AAA26060002CD")
        val c = analyze("00020154037005802PE5903BBB26060002EF")

        val matrix = QrSampleMatrix.build(listOf(a, b, c), listOf("A", "B", "C"))
        val byPath = matrix.rows.associateBy { it.path }

        assertEquals(SampleFieldClass.CONSTANT, byPath["00"]?.classification)
        assertEquals(SampleFieldClass.CONSTANT, byPath["58"]?.classification)
        assertEquals(SampleFieldClass.AMOUNT_CORRELATED, byPath["54"]?.classification)
        assertEquals(SampleFieldClass.AMOUNT_CORRELATED, byPath["59"]?.classification)
        assertEquals(SampleFieldClass.PER_GENERATION_VARIABLE, byPath["26/00"]?.classification)
        assertTrue(matrix.duplicateGroups.isEmpty())
    }

    @Test
    fun `ausente en alguna muestra`() {
        val a = analyze("00020154035005903AAA")
        val b = analyze("0002015403500")

        val matrix = QrSampleMatrix.build(listOf(a, b), listOf("A", "B"))
        val byPath = matrix.rows.associateBy { it.path }

        assertEquals(SampleFieldClass.MISSING_IN_SOME_SAMPLES, byPath["59"]?.classification)
    }

    @Test
    fun `duplicados solo por igualdad exacta del payload`() {
        val payload = "00020154035005802PE"
        val a = analyze(payload)
        val b = analyze(payload)
        val c = analyze("00020154037005802PE")

        val matrix = QrSampleMatrix.build(listOf(a, b, c), listOf("A", "B", "C"))

        assertEquals(1, matrix.duplicateGroups.size)
        assertEquals(listOf(0, 1), matrix.duplicateGroups[0])
    }

    @Test
    fun `CRC se clasifica como checksum derivado aunque difiera`() {
        val stemA = "00020154035006304"
        val stemB = "00020154037006304"
        val payloadA = stemA + pe.willian.qrlab.crc.Crc16CcittFalse.computeHex(stemA)
        val payloadB = stemB + pe.willian.qrlab.crc.Crc16CcittFalse.computeHex(stemB)

        val matrix = QrSampleMatrix.build(
            listOf(analyze(payloadA), analyze(payloadB)),
            listOf("A", "B"),
        )
        val byPath = matrix.rows.associateBy { it.path }

        assertEquals(SampleFieldClass.DERIVED_CHECKSUM, byPath["63"]?.classification)
    }

    @Test
    fun `91-01 numerico de 8 con variacion es DATE_CORRELATED`() {
        // 91 contenedor sintético: 00=AB + 01=8 dígitos distintos (no reales).
        val payloadA = "00020191180002AB010811111111"
        val payloadB = "00020191180002AB010822222222"

        val matrix = QrSampleMatrix.build(
            listOf(analyze(payloadA), analyze(payloadB)),
            listOf("A", "B"),
        )
        val byPath = matrix.rows.associateBy { it.path }

        assertEquals(SampleFieldClass.DATE_CORRELATED, byPath["91/01"]?.classification)
    }
}
