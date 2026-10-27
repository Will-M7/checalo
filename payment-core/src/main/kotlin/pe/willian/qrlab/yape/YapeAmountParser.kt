package pe.willian.qrlab.yape

/**
 * Parser de montos del MODELO 1 (prueba de concepto).
 *
 * Combina title/text/bigText/subText/summaryText y extrae TODOS los montos
 * candidatos con forma "S/ 7", "S/7", "S/ 7.00", "S/7.00", "S/ 7,00", "S/7,00".
 *
 * La conversión es únicamente String -> Long (minor units). NO usa Double
 * en ningún punto.
 */
object YapeAmountParser {

    /** "S/" opcional espacios, dígitos enteros,decimales opcionales de 1-2 dígitos. */
    private val AMOUNT = Regex("""S/\s*(\d+)(?:[.,](\d{1,2}))?""")

    /** Extrae todos los montos candidatos (minor units) de los textos de la notificación. */
    fun parse(
        title: String? = null,
        text: String? = null,
        bigText: String? = null,
        subText: String? = null,
        summaryText: String? = null,
    ): List<Long> = parse(listOfNotNull(title, text, bigText, subText, summaryText))

    /** Extrae todos los montos candidatos (minor units) de una lista de textos. */
    fun parse(texts: List<String?>): List<Long> =
        texts.filterNotNull().flatMap { parseOne(it) }

    /** Montos de UN texto, en orden de aparición (con repetidos). */
    private fun parseOne(text: String): List<Long> =
        AMOUNT.findAll(text).map { match -> toMinor(match.groupValues[1], match.groupValues[2]) }.toList()

    /**
     * Conversión entera: "7" -> 700, "10.50"/"10,50" -> 1050.
     * Solo String y Long, sin Double.
     */
    private fun toMinor(whole: String, decimals: String): Long {
        val cents: Long = when (decimals.length) {
            0 -> 0L
            1 -> decimals.toLong() * 10
            else -> decimals.toLong()
        }
        return whole.toLong() * 100 + cents
    }
}
