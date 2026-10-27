package com.example.ecolacteoshuata.core.ocr

import com.example.ecolacteoshuata.domain.model.ResultadoTipo

data class LabOcrDraft(
    val resultado: ResultadoTipo?,
    val densidad: Double?,
    val acidez: Double?,
    val temperatura: Double?,
    val observaciones: String,
    val grasa: Double? = null,
    val sng: Double? = null,
    val proteina: Double? = null,
    val lactosa: Double? = null,
    val sales: Double? = null,
    val totalSolidos: Double? = null,
    val aguaAnadida: Double? = null,
    val puntoCongelacion: Double? = null,
    val ph: Double? = null,
    /** "SN: 48359" → "48359". */
    val sn: String? = null,
    /** "Mode: 1" → "1". */
    val modo: String? = null,
    /** Hora y fecha que imprime el analizador al final del ticket ("09:42  06/06/26"). */
    val hora: String? = null,
    val fecha: String? = null,
)

/**
 * Convierte el texto crudo reconocido por OCR en una foto del ticket del analizador (SmartScan /
 * Lactoscan) en un borrador editable. Reconoce las etiquetas en español y las abreviaturas en
 * inglés que imprimen los analizadores (FAT, SNF, DEN, PRO, LAC, SALT, TS, W, FP, T, pH).
 */
object ResultadoLaboratorioOcrParser {
    private val numero = Regex("-?[0-9]+(?:[.,][0-9]+)?")

    /**
     * Busca la primera línea donde aparezca alguna de las [claves] como palabra completa (y
     * ninguna de las [excluir]) y devuelve el número que le sigue en esa línea o en la siguiente.
     */
    private fun valor(lineas: List<String>, claves: List<String>, excluir: List<String> = emptyList()): Double? {
        val patron = Regex("(?<![a-záéíóúñ])(${claves.joinToString("|") { Regex.escape(it) }})(?![a-záéíóúñ])")
        lineas.forEachIndexed { i, linea ->
            val minuscula = linea.lowercase()
            if (excluir.any { minuscula.contains(it) }) return@forEachIndexed
            val coincidencia = patron.find(minuscula) ?: return@forEachIndexed
            val resto = minuscula.substring(coincidencia.range.last + 1)
            val encontrado = numero.find(resto) ?: lineas.getOrNull(i + 1)?.let { numero.find(it) }
            encontrado?.value?.replace(',', '.')?.toDoubleOrNull()?.let { return it }
        }
        return null
    }

    fun parsear(lineas: List<String>): LabOcrDraft {
        val textoCompleto = lineas.joinToString(" ").lowercase()
        val resultado = when {
            textoCompleto.contains("soleado") -> ResultadoTipo.SOLEADO
            textoCompleto.contains("acidific") -> ResultadoTipo.ACIDIFICADO
            textoCompleto.contains("aguado") -> ResultadoTipo.AGUADO
            textoCompleto.contains("no conforme") -> ResultadoTipo.NO_CONFORME
            textoCompleto.contains("conforme") -> ResultadoTipo.CONFORME
            else -> null
        }
        return LabOcrDraft(
            resultado = resultado,
            densidad = valor(lineas, listOf("densidad", "density", "den")),
            acidez = valor(lineas, listOf("acidez", "acidity", "acid")),
            temperatura = valor(lineas, listOf("temperatura", "temp", "t")),
            observaciones = lineas.joinToString("\n"),
            grasa = valor(lineas, listOf("grasa", "fat"), excluir = listOf("no gras", "snf")),
            sng = valor(lineas, listOf("sng", "snf", "solidos no grasos", "sólidos no grasos")),
            proteina = valor(lineas, listOf("proteina", "proteína", "protein", "pro")),
            lactosa = valor(lineas, listOf("lactosa", "lactose", "lac")),
            sales = valor(lineas, listOf("sales", "salts", "salt")),
            totalSolidos = valor(lineas, listOf("total solidos", "total sólidos", "solidos totales", "sólidos totales", "total solids", "ts")),
            aguaAnadida = valor(lineas, listOf("agua añadida", "agua anadida", "agua", "added water", "water", "w")),
            puntoCongelacion = valor(lineas, listOf("punto de congelacion", "punto de congelación", "punto cong", "freezing point", "fp")),
            ph = valor(lineas, listOf("ph")),
            sn = valor(lineas, listOf("sn", "s/n", "serie"))?.let { entero(it) },
            modo = valor(lineas, listOf("mode", "modo"))?.let { entero(it) },
            hora = Regex("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b").find(textoCompleto)?.let { "${it.groupValues[1].padStart(2, '0')}:${it.groupValues[2]}" },
            fecha = Regex("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})\\b").find(textoCompleto)?.let {
                "${it.groupValues[1].padStart(2, '0')}/${it.groupValues[2].padStart(2, '0')}/${it.groupValues[3]}"
            },
        )
    }

    /** 48359.0 → "48359"; si trae decimales se deja tal cual. */
    private fun entero(v: Double): String = if (v == kotlin.math.floor(v)) v.toLong().toString() else v.toString()
}
