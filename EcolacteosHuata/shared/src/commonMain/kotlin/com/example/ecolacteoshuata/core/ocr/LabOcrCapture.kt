package com.example.ecolacteoshuata.core.ocr

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

/** Punto de captura de foto + OCR para el registro de laboratorio. Solo Android por ahora. */
expect class LabOcrCapture {
    val disponible: Boolean

    /** Toma la foto de lo que se ve en [LabCamaraPreview] y la pasa por el OCR. */
    fun lanzar()
}

/**
 * [onTextoReconocido] recibe las líneas leídas y la foto tomada (para mostrarla en el
 * formulario); la foto puede ser null si no se pudo decodificar.
 */
@Composable
expect fun rememberLabOcrCapture(
    onTextoReconocido: (lineas: List<String>, foto: ImageBitmap?) -> Unit,
    onError: (String) -> Unit,
): LabOcrCapture

/** Vista en vivo de la cámara dentro de la misma pantalla (no abre la app de cámara). */
@Composable
expect fun LabCamaraPreview(captura: LabOcrCapture, modifier: Modifier = Modifier)
