package pe.willian.qrlab.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Escritura y lectura de QR sintéticos con ZXing.
 *
 * Solo opera sobre payloads generados por el laboratorio;
 * nunca toca samples/private/.
 */
object QrImageWriter {

    /** Escribe [payload] como PNG en [output] (crea directorios padre). */
    fun write(payload: String, output: Path, size: Int = 512) {
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
        output.parent?.let { Files.createDirectories(it) }
        MatrixToImageWriter.writeToPath(matrix, "PNG", output)
    }

    /** Lee el payload exacto contenido en el PNG de [input]. */
    fun read(input: Path): String {
        val image = ImageIO.read(input.toFile())
            ?: throw IllegalArgumentException("no se pudo leer imagen PNG: $input")
        val bitmap = BinaryBitmap(HybridBinarizer(BufferedImageLuminanceSource(image)))
        return QRCodeReader().decode(bitmap).text
    }
}
