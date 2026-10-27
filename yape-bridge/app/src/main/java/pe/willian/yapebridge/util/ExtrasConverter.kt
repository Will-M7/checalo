package pe.willian.yapebridge.util

/**
 * Conversión segura de extras a String.
 *
 * Funciones puras de Kotlin (sin dependencias del framework Android)
 * para que puedan probarse con `./gradlew test` sin Robolectric.
 */
object ExtrasConverter {

    /** Longitud máxima por valor para evitar registros gigantes en memoria. */
    const val MAX_VALUE_LENGTH = 1000

    /**
     * Convierte un valor arbitrario proveniente de Notification.extras
     * a una representación textual segura.
     *
     * Retorna null solo si [value] es null (el caller decide si omitir la clave).
     * Nunca lanza excepciones: cualquier toString() riesgoso se captura.
     */
    fun valueToString(value: Any?): String? {
        if (value == null) return null
        val raw: String = try {
            when (value) {
                is String -> value
                is CharSequence -> value.toString()
                is Boolean, is Number -> value.toString()
                is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { item ->
                    valueToString(item) ?: "null"
                }
                is Collection<*> -> value.joinToString(prefix = "[", postfix = "]") { item ->
                    valueToString(item) ?: "null"
                }
                else -> value.toString()
            }
        } catch (_: Exception) {
            return "<unprintable:${value.javaClass.simpleName}>"
        }
        return if (raw.length > MAX_VALUE_LENGTH) {
            raw.take(MAX_VALUE_LENGTH) + "…(truncated)"
        } else {
            raw
        }
    }

    /**
     * Convierte un mapa crudo de extras a Map<String, String>.
     * - Omite claves nulas o en blanco.
     * - Omite valores nulos.
     * - Nunca lanza.
     */
    fun sanitizeExtrasMap(input: Map<String, Any?>): Map<String, String> {
        if (input.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>(input.size)
        for ((key, value) in input) {
            if (key.isBlank()) continue
            val str = try {
                valueToString(value)
            } catch (_: Exception) {
                "<unprintable>"
            }
            if (str != null) {
                out[key] = str
            }
        }
        return out
    }
}
