package com.example.ecolacteoshuata.core.pdf

data class PdfEmpresaInfo(
    val razonSocial: String,
    val ruc: String,
    val telefono: String,
    val direccion: String,
)

data class PdfTabla(
    val columnas: List<String>,
    val filas: List<List<String>>,
)

data class PdfDocumentModel(
    val titulo: String,
    val subtitulo: String = "",
    val empresa: PdfEmpresaInfo? = null,
    val tablas: List<PdfTabla>,
    val totales: List<String> = emptyList(),
    val pie: String = "Ecolácteos Huata - Huata, Puno",
)

expect class PdfExporter {
    fun export(doc: PdfDocumentModel, nombreArchivo: String): String
}
