package pe.willian.qrlab.qr

import pe.willian.qrlab.crc.Crc16CcittFalse
import pe.willian.qrlab.emv.EmvDictionary
import pe.willian.qrlab.emv.TlvField
import pe.willian.qrlab.emv.TlvParseException
import pe.willian.qrlab.emv.TlvParseResult
import pe.willian.qrlab.emv.TlvParser

/** Resultado del análisis de un payload QR. */
data class QrAnalysis(
    val payload: String,
    val fields: List<TlvField>,
    val crcExpected: String?,
    val crcCalculated: String?,
    val crcValid: Boolean?,
)

/**
 * Analiza un payload QR de pago con estructura TLV y produce un informe en texto.
 * Solo lectura: no modifica ni genera payloads financieros.
 */
object QrPayloadAnalyzer {

    /** Analiza [payload] o lanza [TlvParseException] si la estructura es inválida. */
    fun analyze(payload: String): QrAnalysis {
        val fields = when (val result = TlvParser.parse(payload)) {
            is TlvParseResult.Success -> result.fields
            is TlvParseResult.Failure -> throw TlvParseException(result.message, result.offset)
        }
        val crcField = fields.firstOrNull { it.tag == "63" }
        return if (crcField == null) {
            QrAnalysis(payload, fields, null, null, null)
        } else {
            // El CRC se calcula sobre todo el payload hasta incluir "6304"
            // (tag+longitud del campo 63), excluyendo los 4 caracteres del CRC final.
            val stemEnd = crcField.startOffset + 4
            val stem = payload.substring(0, stemEnd)
            val expected = crcField.value.uppercase()
            val calculated = Crc16CcittFalse.computeHex(stem)
            QrAnalysis(payload, fields, expected, calculated, expected == calculated)
        }
    }

    /** Analiza y devuelve directamente el informe formateado. */
    fun analyzeAndRender(payload: String): String = render(analyze(payload))

    /** Formatea un análisis con el formato "QR PAYLOAD ANALYSIS". */
    fun render(analysis: QrAnalysis): String = buildString {
        appendLine("QR PAYLOAD ANALYSIS")
        appendLine("===================")
        appendLine()
        for (field in analysis.fields) {
            renderField(field, indent = "")
            appendLine()
        }
        appendLine("CRC")
        if (analysis.crcExpected == null) {
            appendLine("Presente : false")
            appendLine("Detalle  : sin campo 63")
        } else {
            appendLine("Expected : ${analysis.crcExpected}")
            appendLine("Calculated: ${analysis.crcCalculated}")
            appendLine("Valid     : ${analysis.crcValid}")
        }
    }

    private fun StringBuilder.renderField(field: TlvField, indent: String) {
        appendLine("${indent}Tag ${field.tag}")
        appendLine("${indent}Length : ${field.length}")
        appendLine("${indent}Value  : ${field.value}")
        EmvDictionary.meaningOf(field.tag)?.let { appendLine("${indent}Meaning: $it") }
        EmvDictionary.decodeValue(field.tag, field.value)?.let { appendLine("${indent}Decoded: $it") }
        val children = field.children
        if (children != null) {
            appendLine("${indent}Nested : true (${children.size} subcampos)")
            for (child in children) {
                renderField(child, indent = "$indent  ")
            }
        }
    }
}
