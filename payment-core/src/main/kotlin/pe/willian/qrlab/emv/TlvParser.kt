package pe.willian.qrlab.emv

/**
 * Error de parseo TLV con posición exacta.
 *
 * @param message descripción del problema.
 * @param offset posición del payload donde se detectó el problema.
 */
class TlvParseException(message: String, val offset: Int) : IllegalArgumentException("$message (offset $offset)")

/** Resultado del parseo: éxito con campos, o fallo con mensaje y offset. */
sealed interface TlvParseResult {
    data class Success(val fields: List<TlvField>) : TlvParseResult
    data class Failure(val message: String, val offset: Int) : TlvParseResult
}

/**
 * Parser genérico de payloads con estructura TLV de 2+2+N:
 * TAG (2 caracteres numéricos) + LONGITUD (2 decimales) + VALOR (N caracteres).
 *
 * - Detecta payload truncado y longitud inválida con errores descriptivos.
 * - Conserva offsets de cada campo.
 * - Nunca ignora caracteres restantes: todo resto que no forme un TLV completo es error.
 * - Detecta TLV anidado solo si TODO el value parsea como TLV; si no, queda como dato opaco.
 */
object TlvParser {

    /** Parsea [payload] completo. Nunca lanza: devuelve [TlvParseResult.Failure] ante cualquier error. */
    fun parse(payload: String): TlvParseResult {
        if (payload.isEmpty()) {
            return TlvParseResult.Failure("payload vacío: se esperaba al menos un campo TLV", 0)
        }
        val fields = mutableListOf<TlvField>()
        var offset = 0
        while (offset < payload.length) {
            val remaining = payload.length - offset
            if (remaining < 4) {
                return TlvParseResult.Failure(
                    "payload truncado: restan $remaining caracteres en offset $offset, " +
                        "se necesitan mínimo 4 para TAG(2)+LONGITUD(2)",
                    offset,
                )
            }
            val tag = payload.substring(offset, offset + 2)
            if (!tag.all { it.isDigit() }) {
                return TlvParseResult.Failure(
                    "tag inválido en offset $offset: \"$tag\" no son 2 caracteres numéricos",
                    offset,
                )
            }
            val lengthText = payload.substring(offset + 2, offset + 4)
            val length = lengthText.toIntOrNull()
            if (length == null) {
                return TlvParseResult.Failure(
                    "longitud inválida en offset ${offset + 2}: \"$lengthText\" no son 2 decimales",
                    offset + 2,
                )
            }
            val valueStart = offset + 4
            val valueEnd = valueStart + length
            if (valueEnd > payload.length) {
                val available = payload.length - valueStart
                return TlvParseResult.Failure(
                    "payload truncado: campo tag \"$tag\" en offset $offset declara longitud $length " +
                        "pero solo quedan $available caracteres para el valor",
                    offset,
                )
            }
            val value = payload.substring(valueStart, valueEnd)
            val children = tryNested(value)
            fields.add(
                TlvField(
                    tag = tag,
                    length = length,
                    value = value,
                    startOffset = offset,
                    endOffsetExclusive = valueEnd,
                    children = children,
                ),
            )
            offset = valueEnd
        }
        return TlvParseResult.Success(fields)
    }

    /**
     * Intenta parsear [value] como TLV completo para detección de anidado.
     * Devuelve los subcampos solo si TODO el contenido parsea sin errores;
     * null en cualquier otro caso (dato opaco).
     */
    private fun tryNested(value: String): List<TlvField>? {
        if (value.length < 4) return null
        return when (val nested = parse(value)) {
            is TlvParseResult.Success -> nested.fields
            is TlvParseResult.Failure -> null
        }
    }
}
