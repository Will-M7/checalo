package pe.willian.qrlab.qr

import pe.willian.qrlab.emv.EmvDictionary
import pe.willian.qrlab.emv.TlvField

/**
 * Matriz comparativa de N muestras QR ya parseadas.
 *
 * Solo lectura: no modifica ni genera payloads, no fabrica campos,
 * no infiere criptografía ni etiquetas de token/firma.
 *
 * Clasificaciones posibles (experimentales, válidas solo para las
 * muestras observadas):
 * - CONSTANT: mismo valor en todas las muestras.
 * - PER_GENERATION_VARIABLE: dos muestras con el mismo importe (54)
 *   tienen valores diferentes en este path. No implica token/firma.
 * - AMOUNT_CORRELATED: los valores se particionan exactamente igual
 *   que el importe (54): mismo importe -> mismo valor, distinto
 *   importe -> distinto valor. Experimental, no regla universal.
 * - DERIVED_CHECKSUM: tag 63 (CRC): su valor se deriva del payload
 *   completo, no se clasifica como variable por generación.
 * - DATE_CORRELATED (experimental, solo conjunto actual): path 91/01 con
 *   valores numéricos de 8 caracteres que varían junto a las fechas de
 *   las muestras observadas. No define el significado semántico de FECVCTO.
 * - MISSING_IN_SOME_SAMPLES: ausente en al menos una muestra.
 * - INSUFFICIENT_EVIDENCE: difiere pero no hay respaldo para otra clase
 *   de variación; no afirma nada sobre el significado semántico del campo.
 */
enum class SampleFieldClass {
    CONSTANT,
    PER_GENERATION_VARIABLE,
    AMOUNT_CORRELATED,
    DERIVED_CHECKSUM,
    DATE_CORRELATED,
    MISSING_IN_SOME_SAMPLES,
    INSUFFICIENT_EVIDENCE,
}

/** Una fila de la matriz: un path con su valor por muestra y su clase. */
data class MatrixRow(
    val path: String,
    val values: List<String?>,
    val classification: SampleFieldClass,
)

/** Matriz completa con etiquetas de muestra. */
data class SampleMatrix(
    val labels: List<String>,
    val analyses: List<QrAnalysis>,
    val rows: List<MatrixRow>,
    /** Grupos de índices con payload EXACTAMENTE igual. Solo grupos de tamaño > 1. */
    val duplicateGroups: List<List<Int>>,
)

object QrSampleMatrix {

    /** Orden preferido de visualización (pedido en la tarea); el resto va al final ordenado. */
    val preferredOrder: List<String> = listOf(
        "00", "01",
        "26/00", "26/01", "26/02",
        "52", "53", "54", "58", "59", "60",
        "80/00", "80/01",
        "90/00", "90/01",
        "91/00", "91/01",
        "92/00", "92/01",
        "63",
    )

    /**
     * Aplana top-level y un solo nivel anidado como "26/02".
     *
     * Solo se expande el primer nivel de los tags contenedores (sin significado
     * hoja en [EmvDictionary], p. ej. 26/80/90/91/92). No se desciende a
     * nietos: valores opacos como "260921..." o "6800" pueden parsear
     * casualmente como TLV y generar paths fantasma (p. ej. "26/02/04",
     * "63/68"); limitar la profundidad evita esos falsos positivos.
     * Los tags hoja (00/01/52/53/54/58/59/60/63) nunca se expanden.
     */
    fun flatten(analysis: QrAnalysis): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (field in analysis.fields) {
            out.putIfAbsent(field.tag, field.value)
            val children = field.children
            if (children != null && EmvDictionary.meaningOf(field.tag) == null) {
                for (child in children) {
                    out.putIfAbsent("${field.tag}/${child.tag}", child.value)
                }
            }
        }
        return out
    }

    /** Construye la matriz a partir de análisis ya parseados y sus etiquetas. */
    fun build(analyses: List<QrAnalysis>, labels: List<String>): SampleMatrix {
        require(analyses.size == labels.size) { "analyses y labels deben tener el mismo tamaño" }
        require(analyses.isNotEmpty()) { "se requiere al menos una muestra" }
        val flatMaps = analyses.map { flatten(it) }
        val allPaths = LinkedHashSet<String>()
        flatMaps.forEach { allPaths.addAll(it.keys) }
        val ordered = allPaths.sortedWith { a, b ->
            val ia = preferredOrder.indexOf(a)
            val ib = preferredOrder.indexOf(b)
            when {
                ia >= 0 && ib >= 0 -> ia.compareTo(ib)
                ia >= 0 -> -1
                ib >= 0 -> 1
                else -> a.compareTo(b)
            }
        }
        val amountValues = flatMaps.map { it["54"] }
        val rows = ordered.map { path ->
            val values = flatMaps.map { it[path] }
            MatrixRow(path, values, classify(path, values, amountValues))
        }
        return SampleMatrix(labels, analyses, rows, findDuplicateGroups(analyses))
    }

    private fun classify(path: String, values: List<String?>, amountValues: List<String?>): SampleFieldClass {
        if (values.any { it == null }) return SampleFieldClass.MISSING_IN_SOME_SAMPLES
        // Tag 63 (CRC): se deriva del payload completo, no es variable por generación.
        if (path == "63") return SampleFieldClass.DERIVED_CHECKSUM
        val nonNull = values.filterNotNull()
        val distinct = nonNull.distinct()
        if (distinct.size == 1) return SampleFieldClass.CONSTANT
        // 91/01 (experimental, solo conjunto actual): numérico de 8 con variación.
        // No define el significado semántico de FECVCTO.
        if (path == "91/01" && nonNull.all { it.length == 8 && QrFieldProfiler.isNumericOnly(it) }) {
            return SampleFieldClass.DATE_CORRELATED
        }
        // AMOUNT_CORRELATED (experimental): partición idéntica a la del importe 54.
        // Requiere importes presentes y al menos 2 importes distintos.
        if (pathAllowsAmountCorrelation(amountValues)) {
            var matches = true
            for (i in values.indices) {
                for (j in i + 1 until values.size) {
                    val sameAmount = amountValues[i] == amountValues[j]
                    val sameValue = values[i] == values[j]
                    if (sameAmount != sameValue) {
                        matches = false
                        break
                    }
                }
                if (!matches) break
            }
            if (matches) return SampleFieldClass.AMOUNT_CORRELATED
        }
        // PER_GENERATION_VARIABLE: mismo importe pero distinto valor.
        for (i in values.indices) {
            for (j in i + 1 until values.size) {
                if (amountValues[i] != null && amountValues[i] == amountValues[j] && values[i] != values[j]) {
                    return SampleFieldClass.PER_GENERATION_VARIABLE
                }
            }
        }
        return SampleFieldClass.INSUFFICIENT_EVIDENCE
    }

    private fun pathAllowsAmountCorrelation(amountValues: List<String?>): Boolean {
        if (amountValues.any { it == null }) return false
        return amountValues.filterNotNull().distinct().size > 1
    }

    /**
     * Duplicados por igualdad EXACTA del payload completo.
     * No usa únicamente CRC: dos payloads distintos podrían colisionar en CRC.
     */
    fun findDuplicateGroups(analyses: List<QrAnalysis>): List<List<Int>> {
        val byPayload = LinkedHashMap<String, MutableList<Int>>()
        analyses.forEachIndexed { index, analysis ->
            byPayload.getOrPut(analysis.payload) { mutableListOf() }.add(index)
        }
        return byPayload.values.filter { it.size > 1 }.map { it.toList() }
    }

    /** Informe en texto con CRC, matriz, clases, perfiles, duplicados, hechos e hipótesis. */
    fun render(matrix: SampleMatrix): String = buildString {
        appendLine("QR SAMPLE MATRIX")
        appendLine("=================")
        appendLine("Muestras: ${matrix.labels.joinToString(", ")}")
        appendLine()
        appendLine("CRC POR MUESTRA")
        appendLine("---------------")
        matrix.analyses.forEachIndexed { index, analysis ->
            appendLine("${matrix.labels[index]}: Expected=${analysis.crcExpected} Calculated=${analysis.crcCalculated} Valid=${analysis.crcValid}")
        }
        appendLine()
        appendLine("MATRIZ (Path | ${matrix.labels.joinToString(" | ") { it }} | Clase)")
        appendLine("-----")
        for (row in matrix.rows) {
            val cells = row.values.map { it ?: "-" }
            appendLine("${row.path} | ${cells.joinToString(" | ")} | ${row.classification}")
        }
        appendLine()
        fun section(title: String, filter: (MatrixRow) -> Boolean) {
            appendLine(title)
            appendLine("-".repeat(title.length))
            val selected = matrix.rows.filter(filter).map { it.path }
            if (selected.isEmpty()) appendLine("(ninguno)") else selected.forEach { appendLine(it) }
            appendLine()
        }
        section("CONSTANTES (CONSTANT)", { it.classification == SampleFieldClass.CONSTANT })
        section(
            "CORRELACIONADOS CON IMPORTE (AMOUNT_CORRELATED, experimental)",
            { it.classification == SampleFieldClass.AMOUNT_CORRELATED },
        )
        section(
            "VARIABLES POR GENERACION (PER_GENERATION_VARIABLE)",
            { it.classification == SampleFieldClass.PER_GENERATION_VARIABLE },
        )
        section("CHECKSUM DERIVADO (DERIVED_CHECKSUM)", { it.classification == SampleFieldClass.DERIVED_CHECKSUM })
        section(
            "CORRELACIONADO CON FECHA OBSERVADA (DATE_CORRELATED, experimental, sin semantica)",
            { it.classification == SampleFieldClass.DATE_CORRELATED },
        )
        section("AUSENTES EN ALGUNA MUESTRA (MISSING_IN_SOME_SAMPLES)", { it.classification == SampleFieldClass.MISSING_IN_SOME_SAMPLES })
        section(
            "EVIDENCIA INSUFICIENTE (INSUFFICIENT_EVIDENCE, solo clasificacion de variacion)",
            { it.classification == SampleFieldClass.INSUFFICIENT_EVIDENCE },
        )
        appendLine("PERFILES DESCRIPTIVOS POR PATH VARIABLE (solo lectura, sin semantica)")
        appendLine("---------------------------------------------------------------------")
        appendLine("Numeric-only primero: Character class NUMERIC, longitud y patron.")
        appendLine("Base64 solo como marcador secundario; bytes y SHA-256 sin interpretar.")
        appendLine("No se revierte ni reproduce contenido; no se generan campos.")
        appendLine()
        val profiled = matrix.rows.filter {
            it.classification == SampleFieldClass.PER_GENERATION_VARIABLE ||
                it.classification == SampleFieldClass.AMOUNT_CORRELATED ||
                it.classification == SampleFieldClass.DATE_CORRELATED ||
                it.classification == SampleFieldClass.INSUFFICIENT_EVIDENCE ||
                it.classification == SampleFieldClass.DERIVED_CHECKSUM ||
                (it.classification == SampleFieldClass.MISSING_IN_SOME_SAMPLES && it.values.filterNotNull().distinct().size > 1)
        }
        if (profiled.isEmpty()) {
            appendLine("(sin paths variables para perfilar)")
            appendLine()
        } else {
            for (row in profiled) {
                appendLine("Path ${row.path} [${row.classification}]")
                val present = row.values.filterNotNull()
                val allNumeric = present.isNotEmpty() && present.all { QrFieldProfiler.isNumericOnly(it) }
                if (allNumeric) {
                    val lengths = present.map { it.length }.distinct()
                    appendLine("  Character class: NUMERIC")
                    appendLine("  Length=${lengths.joinToString(",")}")
                    appendLine("  Base64 syntax compatible: ${present.map { QrFieldProfiler.profile(it).isBase64SyntaxCompatible }.distinct().joinToString(",")}")
                }
                row.values.forEachIndexed { idx, v ->
                    if (v == null) {
                        appendLine("  ${matrix.labels[idx]}: - (ausente)")
                    } else if (allNumeric) {
                        appendLine("  ${matrix.labels[idx]}: chars=${v.length}")
                    } else {
                        val p = QrFieldProfiler.profile(v)
                        if (p.isBase64) {
                            appendLine("  ${matrix.labels[idx]}: chars=${p.charLength} base64=true likelyShaped=${p.isLikelyBase64Shaped} decodedBytes=${p.decodedByteLength} sha256=${p.decodedSha256Hex}")
                        } else {
                            appendLine("  ${matrix.labels[idx]}: chars=${p.charLength} base64=false")
                        }
                    }
                }
                if (present.size >= 2) {
                    if (allNumeric) {
                        val np = QrFieldProfiler.numericPattern(present)
                        if (np != null) {
                            appendLine("  Common prefix length=${np.prefixLength}")
                            appendLine("  Common prefix=${np.prefix}")
                            appendLine("  Common suffix length=${np.suffixLength}")
                            appendLine("  Common suffix=${np.suffix}")
                            appendLine("  Constant positions=${np.constantPositions.joinToString(",")}")
                            appendLine("  Variable positions=${np.variablePositions.joinToString(",")}")
                        }
                    } else {
                        val cp = QrFieldProfiler.commonPrefix(present)
                        appendLine("  Common prefix length=${cp.prefixLength}")
                        appendLine("  Common prefix=${cp.prefix}")
                        row.values.forEachIndexed { idx, v ->
                            if (v == null) {
                                appendLine("  Suffix ${matrix.labels[idx]}: - (ausente)")
                            } else {
                                appendLine("  Suffix ${matrix.labels[idx]}: ${v.drop(cp.prefixLength)}")
                            }
                        }
                    }
                }
                if (row.path == "26/02" && allNumeric) {
                    appendLine("  Note: The first six digits correlate with the observed sample date in the currently available dataset.")
                }
                if (row.path == "91/01" && row.classification == SampleFieldClass.DATE_CORRELATED) {
                    appendLine("  Note: formato numerico de 8 caracteres; los valores observados correlacionan con las fechas de las muestras del conjunto actual. Sin definir el significado semantico de FECVCTO.")
                }
                appendLine()
            }
        }
        appendLine("DUPLICADOS POR IGUALDAD EXACTA DEL PAYLOAD")
        appendLine("------------------------------------------")
        if (matrix.duplicateGroups.isEmpty()) {
            appendLine("Sin duplicados: todos los payloads difieren byte por byte.")
        } else {
            for (group in matrix.duplicateGroups) {
                appendLine(group.map { matrix.labels[it] }.joinToString(", ") + " : payload idéntico")
            }
        }
        appendLine()
        appendLine("HECHOS OBSERVADOS (solo lo visto en estas muestras)")
        appendLine("---------------------------------------------------")
        appendLine("Ver matriz y CRC arriba; cada fila indica igualdad o diferencia literal de valores.")
        appendLine("Duplicados: solo por igualdad exacta del payload, no solo por CRC.")
        appendLine("63 es DERIVED_CHECKSUM: se deriva del payload completo.")
        appendLine()
        appendLine("HIPOTESIS / CLASIFICACION EXPERIMENTAL (no reglas universales)")
        appendLine("---------------------------------------------------------------")
        appendLine("CONSTANT: mismo valor en todas las muestras observadas.")
        appendLine("PER_GENERATION_VARIABLE: mismo importe (54) con distinto valor; no se etiqueta como token, firma ni identificador confirmado; no se infiere criptografía.")
        appendLine("AMOUNT_CORRELATED: partición idéntica a la del importe (54) en estas muestras; experimental, no regla universal para todos los proveedores.")
        appendLine("DERIVED_CHECKSUM: tag 63 conocido como CRC del payload completo.")
        appendLine("DATE_CORRELATED: path 91/01 numerico de 8 con variacion junto a fechas observadas; experimental, sin definir el significado semantico de FECVCTO.")
        appendLine("MISSING_IN_SOME_SAMPLES: ausente en al menos una muestra.")
        appendLine("INSUFFICIENT_EVIDENCE: solo clasificacion de variacion; no afirma conocimiento del significado semantico del campo.")
    }
}
