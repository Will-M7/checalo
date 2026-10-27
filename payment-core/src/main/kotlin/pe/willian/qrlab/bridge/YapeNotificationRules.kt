package pe.willian.qrlab.bridge

import java.nio.file.Files
import java.nio.file.Path

/**
 * Reglas externas del parser, cargadas desde un archivo privado apuntado por
 * YAPE_NOTIFICATION_RULES_FILE. El archivo real NUNCA se versiona.
 *
 * Formato conceptual (clave=valor, '#' comentario):
 * ```
 * allowedPackage=com.ejemplo.yape
 * amountRegex=S/\\s*(\\d+[.,]\\d{2})
 * payerRegex=de\\s+(.+)
 * securityCodeRegex=código[^0-9]*(\\d{3})
 * ```
 *
 * Sin archivo de configuración, el parser usa solo la heurística genérica
 * segura para montos y códigos; nombre y código quedan null si no están
 * claros. Las reglas permiten reemplazar ese comportamiento en minutos.
 */
data class YapeNotificationRules(
    val allowedPackages: Set<String> = emptySet(),
    val amountRegex: Regex? = null,
    val payerRegex: Regex? = null,
    val securityCodeRegex: Regex? = null,
) {
    companion object {
        fun loadOrNull(file: Path?): YapeNotificationRules? {
            if (file == null) return null
            if (!Files.isRegularFile(file)) return null
            val allowed = mutableSetOf<String>()
            var amount: Regex? = null
            var payer: Regex? = null
            var code: Regex? = null
            Files.readAllLines(file, Charsets.UTF_8).forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                val key = trimmed.substringBefore("=").trim()
                val value = trimmed.substringAfter("=", "").trim()
                if (value.isEmpty()) return@forEach
                when (key) {
                    "allowedPackage" -> value.split(",").map { it.trim() }
                        .filter { it.isNotEmpty() }.forEach(allowed::add)
                    "amountRegex" -> compileOrNull(value)?.let { amount = it }
                    "payerRegex" -> compileOrNull(value)?.let { payer = it }
                    "securityCodeRegex" -> compileOrNull(value)?.let { code = it }
                }
            }
            return YapeNotificationRules(allowed, amount, payer, code)
        }

        private fun compileOrNull(pattern: String): Regex? {
            return try {
                Regex(pattern, RegexOption.IGNORE_CASE)
            } catch (_: Exception) {
                System.err.println("Aviso: regex inválida en reglas Yape, se ignora: $pattern")
                null
            }
        }
    }
}
