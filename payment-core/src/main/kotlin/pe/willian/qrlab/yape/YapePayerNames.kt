package pe.willian.qrlab.yape

import java.text.Normalizer

/**
 * Normalización EXACTA del nombre del pagador para resolver casos
 * ambiguos (MODELO 2).
 *
 * Reglas (comparación exacta tras normalizar):
 * - trim de extremos,
 * - minúsculas,
 * - espacios múltiples (incl. tabs/saltos) -> un solo espacio,
 * - normalización Unicode: se descomponen y remueven los acentos/diacríticos.
 *
 * NO fuzzy matching, NO Levenshtein, NO contains parcial: o coincide
 * exactamente tras normalizar, o no coincide.
 */
object YapePayerNames {

    /** Normaliza un nombre para comparación exacta. Nunca devuelve null. */
    fun normalize(raw: String): String {
        val decomposed = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
        val withoutMarks = decomposed.replace(MARKS, "")
        val squashed = withoutMarks.trim().lowercase().replace(SPACES, " ")
        return Normalizer.normalize(squashed, Normalizer.Form.NFC)
    }

    /** ¿Ambos nombres coinciden exactamente tras normalizar? */
    fun matches(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        return normalize(a) == normalize(b)
    }

    private val MARKS = Regex("""\p{M}+""")
    private val SPACES = Regex("""\s+""")
}
