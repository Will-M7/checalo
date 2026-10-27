package pe.willian.qrlab.yape

/**
 * Parser de textos de notificación del MODELO 2.
 *
 * Extiende [YapeAmountParser] SIN modificarlo: los montos siguen saliendo
 * de allí; aquí se agrega extracción OPCIONAL de:
 * - payerName: del formato Yape observado
 *     Title: "Confirmación de Pago"
 *     Text : "Yape! <NOMBRE> te envió un pago por S/ 1.00"
 * - securityCode: únicamente si aparece EXPLÍCITAMENTE un código de
 *   seguridad de exactamente 3 dígitos.
 *
 * Los patrones son parámetros con default: la lógica queda testeable y
 * configurable cuando se observe un formato real distinto. NO se inventa
 * ningún formato de transferencia bancaria: lo que no encaje queda
 * [YapeSourceType.UNKNOWN].
 *
 * La conversión de montos sigue siendo String -> Long (sin Double).
 */
object YapeNotificationParser {

    /** Formato Yape observado: "Yape! <NOMBRE> te envió un pago por ...". */
    val DEFAULT_PAYER_PATTERN: Regex =
        Regex("""(?i)Yape!\s+(.+?)\s+te envió un pago por""")

    /**
     * Código de seguridad SOLO cuando se menciona explícitamente
     * (p. ej. "código: 381" / "Código de seguridad 381").
     * Un importe o una hora con 3 dígitos NUNCA se toman como código.
     */
    val DEFAULT_SECURITY_CODE_PATTERN: Regex =
        Regex("""(?i)(?:c[oó]digo(?:\s+de\s+seguridad)?)\s*:?\s*(\d{3})(?!\d)""")

    /** Longitud máxima razonable de un nombre de pagador extraído. */
    private const val MAX_PAYER_NAME_LENGTH = 80

    /** Textos crudos de una notificación (campos del bridge). */
    data class NotificationTexts(
        val title: String? = null,
        val text: String? = null,
        val bigText: String? = null,
        val subText: String? = null,
        val summaryText: String? = null,
    ) {
        fun asList(): List<String?> = listOf(title, text, bigText, subText, summaryText)
    }

    /** Resultado de parsear una notificación (sin persistir nada todavía). */
    data class ParsedNotification(
        val amounts: List<Long>,
        /** Nombre tal cual apareció en el texto (se normaliza al guardar). */
        val payerName: String?,
        /** Código de 3 dígitos EN CLARO: solo vive en memoria, jamás se guarda. */
        val securityCode: String?,
        val sourceType: YapeSourceType,
    )

    /** Parsea montos + nombre + código + origen de los textos de la notificación. */
    fun parse(
        texts: NotificationTexts,
        payerPattern: Regex = DEFAULT_PAYER_PATTERN,
        securityCodePattern: Regex = DEFAULT_SECURITY_CODE_PATTERN,
        securityCodeOverride: String? = null,
    ): ParsedNotification {
        val amounts = YapeAmountParser.parse(texts.asList())
        val payerName = parsePayerName(texts, payerPattern)
        val securityCode = securityCodeOverride?.takeIf { it.matches(Regex("\\d{3}")) }
            ?: parseSecurityCode(texts, securityCodePattern)
        return ParsedNotification(
            amounts = amounts,
            payerName = payerName,
            securityCode = securityCode,
            sourceType = if (securityCode != null || payerName != null) {
                YapeSourceType.YAPE_NATIVE
            } else {
                YapeSourceType.UNKNOWN
            },
        )
    }

    /**
     * Nombre del pagador para el formato Yape observado.
     * Devuelve null si el texto no encaja: NO se inventan formatos.
     */
    fun parsePayerName(texts: NotificationTexts, payerPattern: Regex = DEFAULT_PAYER_PATTERN): String? {
        for (field in texts.asList()) {
            if (field.isNullOrBlank()) continue
            val match = payerPattern.find(field) ?: continue
            val name = match.groupValues[1].trim()
            if (name.isEmpty()) continue
            return name.take(MAX_PAYER_NAME_LENGTH)
        }
        return null
    }

    /**
     * Código de seguridad EXPLÍCITO de exactamente 3 dígitos, o null.
     * No se busca en números sueltos: solo en mención explícita de "código".
     */
    fun parseSecurityCode(
        texts: NotificationTexts,
        securityCodePattern: Regex = DEFAULT_SECURITY_CODE_PATTERN,
    ): String? {
        for (field in texts.asList()) {
            if (field.isNullOrBlank()) continue
            val match = securityCodePattern.find(field) ?: continue
            val code = match.groupValues[1]
            if (code.length == 3) return code
        }
        return null
    }
}
