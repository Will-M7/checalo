package pe.willian.qrlab.qr

import java.security.MessageDigest
import java.util.Base64

/** Clase de caracteres de un valor, prioritaria sobre Base64. */
enum class CharClass {
    NUMERIC,
    OTHER,
}

/** Perfil descriptivo de un valor opaco. Solo lectura, sin semántica. */
data class OpaqueProfile(
    val charLength: Int,
    val charClass: CharClass,
    val isBase64: Boolean,
    val isBase64SyntaxCompatible: Boolean,
    val isLikelyBase64Shaped: Boolean,
    val decodedByteLength: Int?,
    val decodedSha256Hex: String?,
)

/** Prefijo común genérico entre N valores del mismo path. */
data class PrefixProfile(
    val prefixLength: Int,
    val prefix: String,
    val suffixes: List<String>,
)

/** Patrón numérico genérico: prefijo, sufijo y posiciones. Sin inferir fecha ni secuencia. */
data class NumericPattern(
    val prefixLength: Int,
    val prefix: String,
    val suffixLength: Int,
    val suffix: String,
    val constantPositions: List<Int>,
    val variablePositions: List<Int>,
)

/**
 * Análisis descriptivo genérico de valores de campos.
 *
 * Solo lectura y cómputo local:
 * - clase de caracteres (NUMERIC primero), longitud;
 * - compatibilidad sintáctica Base64 y forma probable, bytes y SHA-256;
 * - prefijo común y sufijos; patrón numérico (prefijo/sufijo/posiciones).
 *
 * Un valor numeric-only nunca se presenta prioritariamente como Base64,
 * aunque sea compatible sintácticamente. No interpreta semánticamente
 * el contenido, no lo revierte, no intenta generarlo ni fabrica campos.
 */
object QrFieldProfiler {

    private val base64Syntax = Regex("^[A-Za-z0-9+/]*={0,2}$")

    /** Perfil de [value] con clase prioritaria y marcadores Base64 secundarios. */
    fun profile(value: String): OpaqueProfile {
        val charClass = if (isNumericOnly(value)) CharClass.NUMERIC else CharClass.OTHER
        if (!looksLikeBase64(value)) {
            return OpaqueProfile(value.length, charClass, false, false, false, null, null)
        }
        return try {
            val decoded = Base64.getDecoder().decode(value)
            val digest = MessageDigest.getInstance("SHA-256").digest(decoded)
            OpaqueProfile(
                charLength = value.length,
                charClass = charClass,
                isBase64 = true,
                isBase64SyntaxCompatible = true,
                isLikelyBase64Shaped = isLikelyBase64Shaped(value, charClass),
                decodedByteLength = decoded.size,
                decodedSha256Hex = digest.joinToString("") { "%02x".format(it) },
            )
        } catch (e: IllegalArgumentException) {
            OpaqueProfile(value.length, charClass, false, false, false, null, null)
        }
    }

    /** true si [value] son solo dígitos (no vacío). */
    fun isNumericOnly(value: String): Boolean = value.isNotEmpty() && value.all { it.isDigit() }

    /** true si [value] cumple sintaxis Base64 estricta (alfabeto, padding y múltiplo de 4). */
    fun looksLikeBase64(value: String): Boolean {
        if (value.length < 4 || value.length % 4 != 0) return false
        if (!base64Syntax.matches(value)) return false
        return true
    }

    /**
     * Forma probablemente Base64 (no solo sintaxis): compatible, no numérico y con
     * rasgo de forma (longitud>=16, '+', '/', '=' o mezcla mayúsculas/minúsculas).
     */
    fun isLikelyBase64Shaped(value: String, charClass: CharClass = if (isNumericOnly(value)) CharClass.NUMERIC else CharClass.OTHER): Boolean {
        if (charClass == CharClass.NUMERIC) return false
        if (!looksLikeBase64(value)) return false
        if (value.length >= 16) return true
        if ('+' in value || '/' in value || value.endsWith("=")) return true
        val hasUpper = value.any { it.isUpperCase() }
        val hasLower = value.any { it.isLowerCase() }
        return hasUpper && hasLower
    }

    /** Prefijo común más largo entre [values]. Totalmente genérico, sin timestamps. */
    fun commonPrefix(values: List<String>): PrefixProfile {
        require(values.isNotEmpty()) { "se requiere al menos un valor" }
        var length = values[0].length
        for (v in values.drop(1)) {
            length = minOf(length, v.length)
            var i = 0
            while (i < length && values[0][i] == v[i]) i++
            length = i
            if (length == 0) break
        }
        val prefix = values[0].take(length)
        return PrefixProfile(length, prefix, values.map { it.drop(length) })
    }

    /**
     * Patrón numérico genérico entre valores numeric-only del mismo path:
     * prefijo común, sufijo común y posiciones constantes/variables
     * (sobre la longitud mínima). Null si algún valor no es numeric-only.
     * No infiere fecha, hora, secuencia ni timestamp.
     */
    fun numericPattern(values: List<String>): NumericPattern? {
        if (values.isEmpty() || values.any { !isNumericOnly(it) }) return null
        val minLen = values.minOf { it.length }
        var prefixLen = minLen
        for (v in values.drop(1)) {
            var i = 0
            while (i < prefixLen && values[0][i] == v[i]) i++
            prefixLen = i
            if (prefixLen == 0) break
        }
        var suffixLen = minLen - prefixLen
        var s = 0
        while (s < suffixLen) {
            val c = values[0][values[0].length - 1 - s]
            if (values.drop(1).all { it[it.length - 1 - s] == c }) s++ else break
        }
        suffixLen = s
        val constant = mutableListOf<Int>()
        val variable = mutableListOf<Int>()
        for (i in 0 until minLen) {
            val c = values[0][i]
            if (values.drop(1).all { it[i] == c }) constant.add(i) else variable.add(i)
        }
        return NumericPattern(
            prefixLength = prefixLen,
            prefix = values[0].take(prefixLen),
            suffixLength = suffixLen,
            suffix = if (suffixLen == 0) "" else values[0].takeLast(suffixLen),
            constantPositions = constant,
            variablePositions = variable,
        )
    }

    /**
     * Indicio puramente sintáctico de valor numérico estructurado.
     * Devuelve "POSSIBLE_STRUCTURED_NUMERIC_VALUE" si son todo dígitos
     * con longitud 8..20; null en otro caso. Nunca convierte a fecha/hora.
     */
    fun numericAppearanceHint(value: String): String? =
        if (value.length in 8..20 && isNumericOnly(value)) {
            "POSSIBLE_STRUCTURED_NUMERIC_VALUE"
        } else {
            null
        }
}
