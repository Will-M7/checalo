package com.example.ecolacteoshuata.core.ocr

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.posix.QOS_CLASS_USER_INITIATED
import platform.posix.memcpy

actual class LabOcrCapture(
    actual val disponible: Boolean,
    private val onLanzar: () -> Unit,
) {
    actual fun lanzar() = onLanzar()
}

/**
 * iOS: abre la cámara del iPhone (en el simulador, la galería), y lee el ticket con Vision
 * (VNRecognizeTextRequest). Las líneas van al mismo [ResultadoLaboratorioOcrParser] de Android.
 */
@Composable
actual fun rememberLabOcrCapture(
    onTextoReconocido: (lineas: List<String>, foto: ImageBitmap?) -> Unit,
    onError: (String) -> Unit,
): LabOcrCapture {
    val alReconocer by rememberUpdatedState(onTextoReconocido)
    val alFallar by rememberUpdatedState(onError)
    // El delegado se recuerda: UIImagePickerController solo guarda una referencia débil.
    val delegado = remember {
        DelegadoFotoTicket(
            alTomarFoto = { imagen -> leerTicket(imagen, { lineas, foto -> alReconocer(lineas, foto) }, { alFallar(it) }) },
            alCancelar = { alFallar("No se tomó la foto.") },
        )
    }
    return remember {
        LabOcrCapture(disponible = true) {
            val presentador = controladorVisible()
            if (presentador == null) {
                alFallar("No se pudo abrir la cámara.")
                return@LabOcrCapture
            }
            val selector = UIImagePickerController()
            selector.sourceType = if (UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            } else {
                // Simulador (sin cámara): se elige una foto del ticket de la galería.
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
            }
            selector.delegate = delegado
            presentador.presentViewController(selector, animated = true, completion = null)
        }
    }
}

/** En iOS no hay vista previa embebida: el botón abre la cámara del sistema a pantalla completa. */
@Composable
actual fun LabCamaraPreview(captura: LabOcrCapture, modifier: Modifier) {
    Box(modifier)
}

private class DelegadoFotoTicket(
    private val alTomarFoto: (UIImage) -> Unit,
    private val alCancelar: () -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val imagen = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true) {
            if (imagen != null) alTomarFoto(imagen) else alCancelar()
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}

/** El controlador que está en pantalla (el de Compose o lo que tenga encima). */
private fun controladorVisible(): UIViewController? {
    var actual = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (actual?.presentedViewController != null) actual = actual.presentedViewController
    return actual
}

/** Lee el texto del ticket fuera del hilo principal y devuelve el resultado en el principal. */
@OptIn(ExperimentalForeignApi::class)
private fun leerTicket(
    original: UIImage,
    alReconocer: (List<String>, ImageBitmap?) -> Unit,
    alFallar: (String) -> Unit,
) {
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0u)) {
        val imagen = enOrientacionNormal(original)
        val cgImage = imagen.CGImage
        if (cgImage == null) {
            dispatch_async(dispatch_get_main_queue()) { alFallar("No se pudo leer la foto.") }
            return@dispatch_async
        }
        val solicitud = VNRecognizeTextRequest(completionHandler = null)
        solicitud.recognitionLevel = VNRequestTextRecognitionLevelAccurate
        solicitud.usesLanguageCorrection = false
        solicitud.recognitionLanguages = listOf("es-ES", "en-US")
        val ok = VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any?>())
            .performRequests(listOf(solicitud), error = null)

        // Vision usa coordenadas con origen abajo: de arriba hacia abajo = mayor Y primero.
        val lineas = (solicitud.results.orEmpty())
            .filterIsInstance<VNRecognizedTextObservation>()
            .sortedByDescending { it.boundingBox.useContents { origin.y + size.height } }
            .mapNotNull { (it.topCandidates(1u).firstOrNull() as? VNRecognizedText)?.string }
        val foto = aImageBitmap(imagen)
        dispatch_async(dispatch_get_main_queue()) {
            if (!ok) alFallar("No se pudo leer el texto del ticket.") else alReconocer(lineas, foto)
        }
    }
}

/** Redibuja la foto "derecha" (la cámara la guarda girada y lo indica solo en sus metadatos). */
@OptIn(ExperimentalForeignApi::class)
private fun enOrientacionNormal(imagen: UIImage): UIImage {
    val (ancho, alto) = imagen.size.useContents { width to height }
    return UIGraphicsImageRenderer(size = imagen.size).imageWithActions {
        imagen.drawInRect(CGRectMake(0.0, 0.0, ancho, alto))
    }
}

/** UIImage -> ImageBitmap de Compose (vía JPEG y Skia), para mostrar la foto en el formulario. */
@OptIn(ExperimentalForeignApi::class)
private fun aImageBitmap(imagen: UIImage): ImageBitmap? {
    val datos: NSData = UIImageJPEGRepresentation(imagen, 0.8) ?: return null
    val bytes = ByteArray(datos.length.toInt())
    if (bytes.isEmpty()) return null
    bytes.usePinned { memcpy(it.addressOf(0), datos.bytes, datos.length) }
    return runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
}
