package com.example.ecolacteoshuata.core.pdf

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import platform.UIKit.*

@OptIn(ExperimentalForeignApi::class)
actual class PdfExporter {
    actual fun export(doc: PdfDocumentModel, nombreArchivo: String): String {
        val pageWidth = 595.0
        val pageHeight = 842.0
        val margin = 36.0

        val format = UIGraphicsPDFRendererFormat()
        val renderer = UIGraphicsPDFRenderer(bounds = CGRectMake(0.0, 0.0, pageWidth, pageHeight), format = format)

        val titleAttrs = mapOf<Any?, Any?>(NSFontAttributeName to UIFont.boldSystemFontOfSize(16.0))
        val subtitleAttrs = mapOf<Any?, Any?>(
            NSFontAttributeName to UIFont.systemFontOfSize(11.0),
            NSForegroundColorAttributeName to UIColor.darkGrayColor,
        )
        val headerAttrs = mapOf<Any?, Any?>(NSFontAttributeName to UIFont.boldSystemFontOfSize(10.5))
        val cellAttrs = mapOf<Any?, Any?>(NSFontAttributeName to UIFont.systemFontOfSize(10.0))
        val totalAttrs = mapOf<Any?, Any?>(NSFontAttributeName to UIFont.boldSystemFontOfSize(11.0))
        val footerAttrs = mapOf<Any?, Any?>(
            NSFontAttributeName to UIFont.systemFontOfSize(9.0),
            NSForegroundColorAttributeName to UIColor.grayColor,
        )

        fun draw(text: String, x: Double, y: Double, attrs: Map<Any?, Any?>) {
            @Suppress("CAST_NEVER_SUCCEEDS")
            val nsString = text as NSString
            nsString.drawAtPoint(CGPointMake(x, y), withAttributes = attrs)
        }

        val data = renderer.PDFDataWithActions { rendererContext ->
            val context = requireNotNull(rendererContext)
            context.beginPage()
            var y = 50.0

            doc.empresa?.let { empresa ->
                draw(empresa.razonSocial, margin, y, titleAttrs)
                y += 20.0
                draw("RUC: ${empresa.ruc}   Tel: ${empresa.telefono}", margin, y, subtitleAttrs)
                y += 16.0
                draw(empresa.direccion, margin, y, subtitleAttrs)
                y += 26.0
            }

            draw(doc.titulo, margin, y, titleAttrs)
            y += 22.0
            if (doc.subtitulo.isNotBlank()) {
                draw(doc.subtitulo, margin, y, subtitleAttrs)
                y += 20.0
            }

            doc.tablas.forEach { tabla ->
                val availableWidth = pageWidth - 2 * margin
                val colWidth = availableWidth / tabla.columnas.size.coerceAtLeast(1)
                if (y > pageHeight - 100) {
                    context.beginPage()
                    y = 50.0
                }
                tabla.columnas.forEachIndexed { index, columna ->
                    draw(columna, margin + index * colWidth, y, headerAttrs)
                }
                y += 18.0
                tabla.filas.forEach { fila ->
                    if (y > pageHeight - 60) {
                        context.beginPage()
                        y = 50.0
                    }
                    fila.forEachIndexed { index, valor ->
                        draw(valor, margin + index * colWidth, y, cellAttrs)
                    }
                    y += 16.0
                }
                y += 14.0
            }

            doc.totales.filter { it.isNotBlank() }.forEach { total ->
                if (y > pageHeight - 60) {
                    context.beginPage()
                    y = 50.0
                }
                draw(total, margin, y, totalAttrs)
                y += 16.0
            }

            draw(doc.pie, margin, pageHeight - 30.0, footerAttrs)
        }

        @Suppress("UNCHECKED_CAST")
        val documentsPath = (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String) ?: NSTemporaryDirectory()
        val filePath = "$documentsPath/$nombreArchivo.pdf"
        data.writeToFile(filePath, atomically = true)
        return filePath
    }
}
