package pe.willian.qrlab.qr

import pe.willian.qrlab.emv.EmvDictionary
import pe.willian.qrlab.emv.TlvField

/** Estado de un tag al comparar dos payloads ya parseados. */
enum class FieldPresence {
    IN_BOTH_EQUAL,
    IN_BOTH_DIFFERENT,
    ONLY_IN_A,
    ONLY_IN_B,
}

/** Comparación de un tag (una ocurrencia) entre dos análisis. */
data class FieldComparison(
    val tag: String,
    val presence: FieldPresence,
    val valueA: String?,
    val valueB: String?,
    /** Comparación recursiva cuando ambos lados tienen TLV anidado en este campo. */
    val nested: List<FieldComparison>? = null,
)

/** Comparación completa entre dos análisis. Genérica: no conoce ningún proveedor. */
data class PayloadComparison(
    val labelA: String,
    val labelB: String,
    val fields: List<FieldComparison>,
)

/**
 * Compara DOS resultados ya parseados ([QrAnalysis]).
 *
 * No conoce Yape, Entel ni ningún proveedor: solo compara tags, valores
 * y, cuando ambos lados tienen hijos, desciende recursivamente al TLV anidado.
 * Los tags se emparejan por ocurrencia ante posibles repeticiones.
 */
object QrPayloadComparator {

    /** Máximo de caracteres de un valor a mostrar en la tabla antes de abreviar. */
    private const val MAX_VALUE_PREVIEW = 40

    fun compare(analysisA: QrAnalysis, analysisB: QrAnalysis, labelA: String, labelB: String): PayloadComparison {
        val byTagA = analysisA.fields.groupBy { it.tag }
        val byTagB = analysisB.fields.groupBy { it.tag }
        val tags = (byTagA.keys + byTagB.keys).sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
        val comparisons = tags.flatMap { tag -> compareOccurrences(tag, byTagA[tag].orEmpty(), byTagB[tag].orEmpty()) }
        return PayloadComparison(labelA, labelB, comparisons)
    }

    private fun compareOccurrences(tag: String, listA: List<TlvField>, listB: List<TlvField>): List<FieldComparison> {
        val common = minOf(listA.size, listB.size)
        val result = mutableListOf<FieldComparison>()
        for (i in 0 until common) {
            val a = listA[i]
            val b = listB[i]
            val nested = if (a.children != null && b.children != null) {
                compareFields(a.children, b.children)
            } else {
                null
            }
            result.add(
                FieldComparison(
                    tag = tag,
                    presence = if (a.value == b.value) FieldPresence.IN_BOTH_EQUAL else FieldPresence.IN_BOTH_DIFFERENT,
                    valueA = a.value,
                    valueB = b.value,
                    nested = nested,
                ),
            )
        }
        for (i in common until listA.size) {
            result.add(FieldComparison(tag, FieldPresence.ONLY_IN_A, listA[i].value, null))
        }
        for (i in common until listB.size) {
            result.add(FieldComparison(tag, FieldPresence.ONLY_IN_B, null, listB[i].value))
        }
        return result
    }

    private fun compareFields(fieldsA: List<TlvField>, fieldsB: List<TlvField>): List<FieldComparison> {
        val byTagA = fieldsA.groupBy { it.tag }
        val byTagB = fieldsB.groupBy { it.tag }
        val tags = (byTagA.keys + byTagB.keys).sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
        return tags.flatMap { tag -> compareOccurrences(tag, byTagA[tag].orEmpty(), byTagB[tag].orEmpty()) }
    }

    /** Tabla `Tag | <A> | <B>` con marcas de igualdad/diferencia y detalle anidado. */
    fun render(comparison: PayloadComparison): String = buildString {
        appendLine("QR PAYLOAD COMPARISON")
        appendLine("=====================")
        appendLine("A: ${comparison.labelA}")
        appendLine("B: ${comparison.labelB}")
        appendLine()
        appendLine("Tag | ${comparison.labelA} | ${comparison.labelB} | Estado")
        appendLine("----|${"-".repeat(comparison.labelA.length)}|${"-".repeat(comparison.labelB.length)}|-------")
        for (field in comparison.fields) {
            renderRow(field, indent = "")
        }
        appendLine()
        appendLine("Leyenda: = igual, != diferente, A solo en ${comparison.labelA}, B solo en ${comparison.labelB}.")
        appendLine("Los tags desconocidos se muestran como valores opacos sin interpretación.")
    }

    private fun StringBuilder.renderRow(field: FieldComparison, indent: String) {
        val mark = when (field.presence) {
            FieldPresence.IN_BOTH_EQUAL -> "="
            FieldPresence.IN_BOTH_DIFFERENT -> "!="
            FieldPresence.ONLY_IN_A -> "A"
            FieldPresence.ONLY_IN_B -> "B"
        }
        val meaning = EmvDictionary.meaningOf(field.tag)?.let { " ($it)" } ?: ""
        appendLine("$indent${field.tag}$meaning | ${preview(field.valueA)} | ${preview(field.valueB)} | $mark")
        field.nested?.forEach { renderRow(it, indent = "$indent  ") }
    }

    private fun preview(value: String?): String {
        if (value == null) return "-"
        if (value.length <= MAX_VALUE_PREVIEW) return value
        return value.take(MAX_VALUE_PREVIEW) + "...(len=${value.length})"
    }
}
