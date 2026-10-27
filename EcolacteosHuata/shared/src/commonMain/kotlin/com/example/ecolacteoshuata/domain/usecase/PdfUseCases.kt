package com.example.ecolacteoshuata.domain.usecase

import com.example.ecolacteoshuata.core.pdf.PdfDocumentModel
import com.example.ecolacteoshuata.core.pdf.PdfEmpresaInfo
import com.example.ecolacteoshuata.core.pdf.PdfExporter
import com.example.ecolacteoshuata.core.pdf.PdfTabla
import com.example.ecolacteoshuata.core.util.formatDdMmmYyyyHm
import com.example.ecolacteoshuata.core.util.formatMoney
import com.example.ecolacteoshuata.domain.model.ResultadoLaboratorio
import com.example.ecolacteoshuata.domain.model.ReporteTabla
import com.example.ecolacteoshuata.domain.model.Venta
import com.example.ecolacteoshuata.domain.repository.EmpresaRepository

class GenerarBoletaPdfUseCase(
    private val pdfExporter: PdfExporter,
    private val empresaRepository: EmpresaRepository,
) {
    suspend operator fun invoke(venta: Venta): String {
        val empresa = empresaRepository.get()
        val filas = venta.items.map {
            listOf(it.nombre, it.cantidad.toString(), formatMoney(it.precioUnitario), formatMoney(it.subtotal))
        }
        val totales = buildList {
            add("Subtotal: ${formatMoney(venta.subtotal)}")
            if (venta.descuento > 0) add("Descuento (${venta.descuentoTipo.ifBlank { "aplicado" }}): ${formatMoney(venta.descuento)}")
            add("Total: ${formatMoney(venta.total)}")
            venta.vuelto?.let { add("Vuelto: ${formatMoney(it)}") }
        }
        val doc = PdfDocumentModel(
            titulo = "Boleta ${venta.numeroBoleta}",
            subtitulo = "${venta.nombreClienteMostrar} · ${venta.fecha.formatDdMmmYyyyHm()}",
            empresa = empresa?.let { PdfEmpresaInfo(it.razonSocial, it.ruc, it.telefono, it.direccion) },
            tablas = listOf(PdfTabla(listOf("Producto", "Cant.", "P. Unit.", "Subtotal"), filas)),
            totales = totales,
        )
        return pdfExporter.export(doc, "boleta_${venta.numeroBoleta}")
    }
}

class ExportarReportePdfUseCase(
    private val pdfExporter: PdfExporter,
    private val empresaRepository: EmpresaRepository,
) {
    suspend operator fun invoke(reporte: ReporteTabla): String {
        val empresa = empresaRepository.get()
        val doc = PdfDocumentModel(
            titulo = reporte.titulo,
            subtitulo = reporte.subtitulo,
            empresa = empresa?.let { PdfEmpresaInfo(it.razonSocial, it.ruc, it.telefono, it.direccion) },
            tablas = listOf(PdfTabla(reporte.columnas, reporte.filas)),
            totales = reporte.totales,
        )
        val nombreArchivo = reporte.titulo.lowercase().replace(Regex("[^a-z0-9]+"), "_")
        return pdfExporter.export(doc, nombreArchivo)
    }
}

/** PDF de un resultado de laboratorio (el botón de descarga de "Laboratorio" en Laravel). */
class GenerarResultadoLabPdfUseCase(
    private val pdfExporter: PdfExporter,
    private val empresaRepository: EmpresaRepository,
) {
    suspend operator fun invoke(resultado: ResultadoLaboratorio, proveedor: String, turno: String?): String {
        val empresa = empresaRepository.get()
        fun v(valor: Double?, decimales: Int = 2) = valor?.let { formatDecimal(it, decimales) } ?: "—"
        val filas = listOf(
            listOf("SN", resultado.sn ?: "—"),
            listOf("Mode", resultado.modo ?: "—"),
            listOf("Temp. (°C)", v(resultado.temperatura)),
            listOf("Grasa (%)", v(resultado.grasa)),
            listOf("SNG (%)", v(resultado.sng)),
            listOf("Densidad", v(resultado.densidad, 3)),
            listOf("Proteína (%)", v(resultado.proteina)),
            listOf("Lactosa (%)", v(resultado.lactosa)),
            listOf("Sales (%)", v(resultado.sales)),
            listOf("Total sólidos (%)", v(resultado.totalSolidos)),
            listOf("Agua añadida (%)", v(resultado.aguaAnadida)),
            listOf("Punto cong. (°C)", v(resultado.puntoCongelacion, 3)),
            listOf("pH", v(resultado.ph)),
            listOf("Hora del análisis", resultado.horaAnalisis ?: "—"),
            listOf("Fecha del análisis", resultado.fechaAnalisis ?: "—"),
        )
        val doc = PdfDocumentModel(
            titulo = "Resultado de laboratorio",
            subtitulo = listOfNotNull(proveedor, resultado.fecha.toString(), turno, "${v(resultado.litros)} L").joinToString(" · "),
            empresa = empresa?.let { PdfEmpresaInfo(it.razonSocial, it.ruc, it.telefono, it.direccion) },
            tablas = listOf(PdfTabla(listOf("Parámetro", "Valor"), filas)),
            totales = listOfNotNull(
                "Resultado: ${if (resultado.conforme) "Conforme" else "No conforme"}",
                resultado.observaciones.takeIf { it.isNotBlank() }?.let { "Observaciones: $it" },
            ),
        )
        return pdfExporter.export(doc, "laboratorio_${resultado.fecha}_${resultado.id.take(8)}")
    }
}

/** El número con [decimales] fijos, como `number_format` de Laravel. */
private fun formatDecimal(valor: Double, decimales: Int): String {
    var factor = 1L
    repeat(decimales) { factor *= 10 }
    val redondeado = kotlin.math.round(kotlin.math.abs(valor) * factor).toLong()
    val fraccion = (redondeado % factor).toString().padStart(decimales, '0')
    return "${if (valor < 0) "-" else ""}${redondeado / factor}.$fraccion"
}
