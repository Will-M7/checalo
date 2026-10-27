package pe.willian.qrlab.emv

/**
 * Codificador TLV genérico (TAG de 2 + LONGITUD decimal de 2 + VALOR).
 *
 * Inverso lógico de [TlvParser] para payloads sintéticos del laboratorio:
 * todo lo que produce debe poder volver a leerse con [TlvParser].
 * Nunca trunca: los valores que no caben en 2 dígitos de longitud se rechazan.
 */
object TlvEncoder {

    private val tagSyntax = Regex("^[0-9]{2}$")

    /** Codifica un campo como TAG+LENGTH+VALUE. Lanza si el tag o la longitud son inválidos. */
    fun encode(tag: String, value: String): String {
        require(tagSyntax.matches(tag)) { "tag inválido \"$tag\": se requieren 2 caracteres numéricos" }
        require(value.length <= 99) {
            "valor demasiado largo para tag \"$tag\": ${value.length} caracteres, máximo 99 (sin truncamiento)"
        }
        return tag + "%02d".format(value.length) + value
    }
}
