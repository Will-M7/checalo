package pe.willian.qrlab.bridge

/**
 * Parser configurable basado en reglas externas ([YapeNotificationRules]).
 *
 * Prioridad por campo: regla externa si existe, si no heurística genérica
 * segura. Dinero siempre en minor units con String/enteros, nunca Double.
 *
 * Heurísticas genéricas (seguras, sin asumir de más):
 * - monto: expresiones tipo "S/ 7", "S/ 7.00", "S/7,50";
 * - código: "código" / "código de seguridad" seguido de exactamente 3 dígitos;
 * - pagador: sin heurística (null) salvo payerRegex configurado.
 */
class ConfigurableNotificationParser(
    private val rules: YapeNotificationRules? = null,
) : IncomingNotificationParser {

    override fun isPackageAllowed(packageName: String): Boolean {
        val allowed = rules?.allowedPackages.orEmpty()
        if (allowed.isEmpty()) return true
        return packageName.trim() in allowed
    }

    override fun parse(raw: RawBridgeNotification): ParsedIncomingPayment? {
        if (!isPackageAllowed(raw.packageName)) return null
        val combined = combinedText(raw)
        val amount = rules?.amountRegex?.let { extractAmount(it, combined) }
            ?: heuristicAmount(combined)
        val payer = rules?.payerRegex?.let { extractGroup(it, combined) }
        val code = rules?.securityCodeRegex?.let { extractCode(it, combined) }
            ?: heuristicSecurityCode(combined)
        return ParsedIncomingPayment(
            amountMinor = amount,
            payerName = payer?.trim()?.takeIf { it.isNotEmpty() },
            securityCode = code,
        )
    }

    private fun combinedText(raw: RawBridgeNotification): String =
        (listOf(raw.title, raw.text, raw.bigText, raw.subText, raw.summaryText) + raw.extras.values)
            .filterNotNull()
            .joinToString("\n")

    private fun extractAmount(regex: Regex, text: String): Long? {
        val match = regex.find(text) ?: return null
        val candidate = match.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: match.value
        return parseAmountToMinor(candidate)
    }

    private fun extractGroup(regex: Regex, text: String): String? {
        val match = regex.find(text) ?: return null
        return match.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: match.value
    }

    private fun extractCode(regex: Regex, text: String): String? {
        val match = regex.find(text) ?: return null
        val groups = match.groupValues.drop(1).filter { it.matches(Regex("^\\d{3}$")) }
        if (groups.isNotEmpty()) return groups.first()
        val whole = match.value.trim()
        return whole.takeIf { it.matches(Regex("^\\d{3}$")) }
    }

    companion object {
        private val HEURISTIC_AMOUNT = Regex("S/\\s*(\\d{1,6})([.,](\\d{1,2}))?", RegexOption.IGNORE_CASE)
        private val HEURISTIC_CODE =
            Regex("c[oó]digo(\\s+de\\s+seguridad)?[^0-9]{0,20}(\\d{3})(?!\\d)", RegexOption.IGNORE_CASE)

        /** Primera expresión de monto tipo S/; null si no hay ninguna legible. */
        fun heuristicAmount(text: String): Long? {
            val match = HEURISTIC_AMOUNT.find(text) ?: return null
            return parseAmountToMinor(match.value)
        }

        /** "código (de seguridad)" + exactamente 3 dígitos; null si no está claro. */
        fun heuristicSecurityCode(text: String): String? {
            val match = HEURISTIC_CODE.find(text) ?: return null
            return match.groupValues.getOrNull(2)?.takeIf { it.matches(Regex("^\\d{3}$")) }
        }

        /**
         * "S/ 7" -> 700, "S/ 7.00" -> 700, "S/7,50" -> 750.
         * Solo String/enteros, nunca Double. null si el formato no es válido.
         */
        fun parseAmountToMinor(raw: String): Long? {
            var cleaned = raw.replace(Regex("(?i)s/"), "").replace(" ", "").replace(",", ".")
            val match = Regex("^(\\d{1,9})(\\.(\\d{1,2}))?$").find(cleaned) ?: return null
            val soles = match.groupValues[1].toLongOrNull() ?: return null
            val cents = match.groupValues[3].ifEmpty { "00" }.padEnd(2, '0').toLongOrNull() ?: return null
            val total = soles * 100 + cents
            return if (total > 0) total else null
        }
    }
}
