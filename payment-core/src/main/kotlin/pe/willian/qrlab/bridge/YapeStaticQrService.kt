package pe.willian.qrlab.bridge

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * QR estático del negocio (LAB).
 *
 * El payload vive SOLO en el archivo privado apuntado por
 * YAPE_STATIC_QR_PAYLOAD_FILE. Nunca se hardcodea un QR real en código
 * ni se guarda en tests. El PNG se genera con ZXing desde ESE payload
 * exacto: sin modificarlo, sin añadir monto, sin alterar CRC.
 */
class YapeStaticQrService(
    private val payloadFile: Path?,
) {

    /** Payload exacto del archivo privado, o null si no está configurado. */
    fun payloadOrNull(): String? {
        val file = payloadFile ?: return null
        if (!Files.isRegularFile(file)) return null
        return Files.readString(file, Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
    }

    /** PNG generado desde el payload exacto, o null si no está configurado. */
    fun pngBytes(size: Int = 512): ByteArray? {
        val payload = payloadOrNull() ?: return null
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
        val out = ByteArrayOutputStream()
        MatrixToImageWriter.writeToStream(matrix, "PNG", out)
        return out.toByteArray()
    }
}
