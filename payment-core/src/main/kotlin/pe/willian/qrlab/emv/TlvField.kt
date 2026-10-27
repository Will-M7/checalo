package pe.willian.qrlab.emv

/**
 * Campo TLV genérico (TAG de 2 caracteres, LONGITUD decimal de 2, VALOR de N).
 *
 * @param tag etiqueta de 2 caracteres numéricos (p. ej. "58").
 * @param length longitud declarada del [value] en caracteres.
 * @param value contenido crudo del campo (opaco salvo que [children] indique TLV anidado).
 * @param startOffset posición en el payload donde empieza este campo (su tag).
 * @param endOffsetExclusive posición donde termina este campo (inicio del siguiente).
 * @param children subcampos si TODO el [value] pudo parsearse como TLV; null si es opaco.
 * @param valid false si el campo se construyó a partir de un error recuperado (reservado;
 * el parser estricto actual falla en lugar de crear campos inválidos).
 */
data class TlvField(
    val tag: String,
    val length: Int,
    val value: String,
    val startOffset: Int,
    val endOffsetExclusive: Int,
    val children: List<TlvField>? = null,
    val valid: Boolean = true,
) {
    /** true cuando el valor contiene un TLV anidado completo. */
    val isNested: Boolean get() = children != null
}
