package pe.willian.qrlab.synthetic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pe.willian.qrlab.money.MoneyFormatter
import pe.willian.qrlab.qr.QrImageWriter
import pe.willian.qrlab.qr.QrPayloadAnalyzer
import java.nio.file.Path

/**
 * Builder sintético QR LAB. Todo ficticio: merchant "QR LAB",
 * MCC 9999, ciudad "Lab". Sin fixtures reales, sin samples/private/.
 */
class SyntheticPaymentQrBuilderTest {

    private val forbidden = listOf("26", "39", "80", "90", "91", "92")

    private fun tagsOf(payload: String): List<String> =
        QrPayloadAnalyzer.analyze(payload).fields.map { it.tag }

    @Test
    fun `amount 500 con CRC valido y merchant ficticio`() {
        val payload = SyntheticPaymentQrBuilder.buildWithLabDefaults(500)
        val analysis = QrPayloadAnalyzer.analyze(payload)

        assertTrue(analysis.crcValid == true)
        assertEquals("500", analysis.fields.first { it.tag == "54" }.value)
        assertEquals("604", analysis.fields.first { it.tag == "53" }.value)
        assertEquals("PE", analysis.fields.first { it.tag == "58" }.value)
        assertEquals("Lab", analysis.fields.first { it.tag == "60" }.value)
        assertTrue(analysis.fields.first { it.tag == "59" }.value.contains("QR LAB"))
        assertEquals(MoneyFormatter.format(500), "S/ 5.00")
        assertFalse(tagsOf(payload).any { it in forbidden })
    }

    @Test
    fun `amount 700 con CRC valido`() {
        val payload = SyntheticPaymentQrBuilder.buildWithLabDefaults(700)
        val analysis = QrPayloadAnalyzer.analyze(payload)

        assertTrue(analysis.crcValid == true)
        assertEquals("700", analysis.fields.first { it.tag == "54" }.value)
        assertFalse(tagsOf(payload).any { it in forbidden })
    }

    @Test
    fun `amount 1050 con CRC valido`() {
        val payload = SyntheticPaymentQrBuilder.buildWithLabDefaults(1050)
        val analysis = QrPayloadAnalyzer.analyze(payload)

        assertTrue(analysis.crcValid == true)
        assertEquals("1050", analysis.fields.first { it.tag == "54" }.value)
        assertEquals("S/ 10.50", MoneyFormatter.format(1050))
        assertFalse(tagsOf(payload).any { it in forbidden })
    }

    @Test
    fun `modificar payload tras CRC invalida`() {
        val payload = SyntheticPaymentQrBuilder.buildWithLabDefaults(700)
        val tampered = payload.dropLast(1) + if (payload.last() != '0') '0' else '1'

        assertFalse(QrPayloadAnalyzer.analyze(tampered).crcValid == true)
    }

    @Test
    fun `PNG round-trip exacto`(@TempDir temp: Path) {
        val payload = SyntheticPaymentQrBuilder.buildWithLabDefaults(700)
        val png = temp.resolve("qr-7.png")

        QrImageWriter.write(payload, png)
        assertTrue(java.nio.file.Files.exists(png))

        assertEquals(payload, QrImageWriter.read(png))
    }
}
