package pe.willian.qrlab

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pruebas de infraestructura (sin datos financieros reales ni parser EMV).
 *
 * - [smokeTest] verifica que JUnit 5 está operativo.
 * - [zxingRoundTrip] verifica que ZXing puede codificar un payload neutro
 *   a matriz QR y decodificarlo de vuelta en memoria.
 */
class SmokeTest {

    @Test
    fun smokeTest() {
        assertEquals(2, 1 + 1)
    }

    @Test
    fun zxingRoundTrip() {
        val payload = "qr-lab-infra-check-001"
        val size = 200

        val matrix: BitMatrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
        val image = MatrixToImageWriter.toBufferedImage(matrix)
        val source = BufferedImageLuminanceSource(image)
        val bitmap = BinaryBitmap(HybridBinarizer(source))

        val decoded = QRCodeReader().decode(bitmap).text

        assertEquals(payload, decoded)
    }
}
