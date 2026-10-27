package pe.willian.qrlab.qr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pe.willian.qrlab.crc.Crc16CcittFalse

/**
 * Analizador de payloads: CRC válido/inválido y formato del informe.
 * El fixture es sintético; su CRC se calcula en la preparación del test.
 */
class QrPayloadAnalyzerTest {

    /** Tallo sintético sin datos reales: termina en "6304" sin el CRC final. */
    private val stem = "0002010102125802PE6004Lima53036046304"

    private fun payloadWithCrc(crc: String) = stem + crc

    @Test
    fun `CRC valido`() {
        val payload = payloadWithCrc(Crc16CcittFalse.computeHex(stem))

        val analysis = QrPayloadAnalyzer.analyze(payload)

        assertEquals(payload, analysis.payload)
        assertEquals(6, analysis.fields.size)
        assertEquals("63", analysis.fields.last().tag)
        assertTrue(analysis.crcValid == true)
        assertEquals(analysis.crcCalculated, analysis.crcExpected)
    }

    @Test
    fun `CRC invalido`() {
        val validCrc = Crc16CcittFalse.computeHex(stem)
        // Corrompe el último carácter garantizando un valor distinto.
        val corruptLast = if (validCrc.last() != '0') '0' else '1'
        val payload = payloadWithCrc(validCrc.dropLast(1) + corruptLast)

        val analysis = QrPayloadAnalyzer.analyze(payload)

        assertFalse(analysis.crcValid == true)
        assertEquals(validCrc, analysis.crcCalculated)
        assertEquals(payload.takeLast(4), analysis.crcExpected)
    }

    @Test
    fun `sin campo 63 no hay CRC`() {
        val analysis = QrPayloadAnalyzer.analyze("5802PE6004Lima")

        assertNull(analysis.crcExpected)
        assertNull(analysis.crcCalculated)
        assertNull(analysis.crcValid)
    }

    @Test
    fun `informe contiene campos significados decodificacion y CRC`() {
        val payload = payloadWithCrc(Crc16CcittFalse.computeHex(stem))

        val report = QrPayloadAnalyzer.analyzeAndRender(payload)

        assertTrue(report.startsWith("QR PAYLOAD ANALYSIS"))
        assertTrue(report.contains("Tag 00"))
        assertTrue(report.contains("Meaning: Payload Format Indicator"))
        assertTrue(report.contains("Tag 01"))
        assertTrue(report.contains("Decoded: DYNAMIC"))
        assertTrue(report.contains("Tag 58"))
        assertTrue(report.contains("Meaning: Country Code"))
        assertTrue(report.contains("Tag 53"))
        assertTrue(report.contains("Decoded: PEN"))
        assertTrue(report.contains("CRC"))
        assertTrue(report.contains("Valid     : true"))
    }
}
