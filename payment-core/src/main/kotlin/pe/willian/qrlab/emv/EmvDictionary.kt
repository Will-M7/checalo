package pe.willian.qrlab.emv

/**
 * Diccionario descriptivo básico para tags de nivel superior.
 * Genérico: no asume ninguna billetera concreta.
 */
object EmvDictionary {

    /** Significado de cada tag de nivel superior conocido. */
    private val topLevelMeanings: Map<String, String> = mapOf(
        "00" to "Payload Format Indicator",
        "01" to "Point of Initiation Method",
        "52" to "Merchant Category Code",
        "53" to "Transaction Currency",
        "54" to "Transaction Amount",
        "58" to "Country Code",
        "59" to "Merchant Name",
        "60" to "Merchant City",
        "63" to "CRC",
    )

    /** Significado del tag, o null si no está en el diccionario. */
    fun meaningOf(tag: String): String? = topLevelMeanings[tag]

    /**
     * Decodifica Point of Initiation Method (tag 01):
     * "11" -> STATIC, "12" -> DYNAMIC, otro -> null.
     */
    fun decodePointOfInitiation(value: String): String? = when (value) {
        "11" -> "STATIC"
        "12" -> "DYNAMIC"
        else -> null
    }

    /** Decodifica moneda (tag 53): "604" -> PEN, otro -> null. */
    fun decodeCurrency(value: String): String? = when (value) {
        "604" -> "PEN"
        else -> null
    }

    /**
     * Decodificación dependiente del tag para el valor dado.
     * Devuelve null cuando el tag no tiene decodificación conocida o el valor no coincide.
     */
    fun decodeValue(tag: String, value: String): String? = when (tag) {
        "01" -> decodePointOfInitiation(value)
        "53" -> decodeCurrency(value)
        else -> null
    }
}
