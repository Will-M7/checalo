package com.example.ecolacteoshuata.core.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

actual class PdfExporter(private val context: Context) {
    actual fun export(doc: PdfDocumentModel, nombreArchivo: String): String {
        val pageWidth = 595
        val pageHeight = 842
        val marginX = 36f
        var y = 0f

        val pdfDocument = PdfDocument()
        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK }
        val subtitlePaint = Paint().apply { textSize = 11f; color = Color.DKGRAY }
        val headerPaint = Paint().apply { textSize = 10.5f; isFakeBoldText = true; color = Color.WHITE }
        val headerBgPaint = Paint().apply { color = Color.rgb(30, 148, 85) }
        val cellPaint = Paint().apply { textSize = 10f; color = Color.BLACK }
        val totalPaint = Paint().apply { textSize = 11f; isFakeBoldText = true; color = Color.BLACK }
        val footerPaint = Paint().apply { textSize = 9f; color = Color.GRAY }

        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas: Canvas = page.canvas
        y = 50f

        fun newPage() {
            pdfDocument.finishPage(page)
            pageNumber += 1
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = 50f
        }

        doc.empresa?.let { empresa ->
            canvas.drawText(empresa.razonSocial, marginX, y, titlePaint)
            y += 16f
            canvas.drawText("RUC: ${empresa.ruc}  ·  Tel: ${empresa.telefono}", marginX, y, subtitlePaint)
            y += 14f
            canvas.drawText(empresa.direccion, marginX, y, subtitlePaint)
            y += 24f
        }

        canvas.drawText(doc.titulo, marginX, y, titlePaint)
        y += 18f
        if (doc.subtitulo.isNotBlank()) {
            canvas.drawText(doc.subtitulo, marginX, y, subtitlePaint)
            y += 20f
        } else {
            y += 6f
        }

        doc.tablas.forEach { tabla ->
            val availableWidth = pageWidth - 2 * marginX
            val colWidth = availableWidth / tabla.columnas.size.coerceAtLeast(1)

            if (y > pageHeight - 100) newPage()
            canvas.drawRect(marginX, y, marginX + availableWidth, y + 20f, headerBgPaint)
            tabla.columnas.forEachIndexed { index, columna ->
                canvas.drawText(columna, marginX + index * colWidth + 4f, y + 14f, headerPaint)
            }
            y += 24f

            tabla.filas.forEach { fila ->
                if (y > pageHeight - 60) {
                    newPage()
                    canvas.drawRect(marginX, y, marginX + availableWidth, y + 20f, headerBgPaint)
                    tabla.columnas.forEachIndexed { index, columna ->
                        canvas.drawText(columna, marginX + index * colWidth + 4f, y + 14f, headerPaint)
                    }
                    y += 24f
                }
                fila.forEachIndexed { index, valor ->
                    canvas.drawText(valor, marginX + index * colWidth + 4f, y + 12f, cellPaint)
                }
                y += 18f
            }
            y += 16f
        }

        if (doc.totales.isNotEmpty()) {
            doc.totales.filter { it.isNotBlank() }.forEach { total ->
                if (y > pageHeight - 60) newPage()
                canvas.drawText(total, marginX, y, totalPaint)
                y += 16f
            }
            y += 8f
        }

        canvas.drawText(doc.pie, marginX, pageHeight - 30f, footerPaint)
        pdfDocument.finishPage(page)

        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        if (!directory.exists()) directory.mkdirs()
        val file = File(directory, "$nombreArchivo.pdf")
        FileOutputStream(file).use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        return file.absolutePath
    }
}
