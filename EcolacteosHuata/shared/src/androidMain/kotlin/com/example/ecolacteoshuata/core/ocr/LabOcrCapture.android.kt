package com.example.ecolacteoshuata.core.ocr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual class LabOcrCapture internal constructor(
    actual val disponible: Boolean,
    /** Caso de uso de CameraX que [LabCamaraPreview] enlaza a la cámara trasera. */
    internal val imageCapture: ImageCapture,
    private val permiso: MutableState<Boolean>,
    internal val pedirPermiso: () -> Unit,
    private val onLanzar: () -> Unit,
) {
    internal val permisoConcedido: Boolean get() = permiso.value
    actual fun lanzar() = onLanzar()
}

private suspend fun reconocerTexto(context: Context, uri: Uri): List<String> = suspendCancellableCoroutine { cont ->
    try {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val lineas = result.textBlocks.flatMap { bloque -> bloque.lines.map { it.text } }
                cont.resume(lineas)
            }
            .addOnFailureListener { error -> cont.resumeWithException(error) }
    } catch (error: Exception) {
        cont.resumeWithException(error)
    }
}

/** Decodifica la foto reducida (para no gastar memoria) y la endereza según su EXIF. */
private fun cargarFoto(context: Context, uri: Uri): ImageBitmap? = try {
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
    var escala = 1
    while (limites.outWidth / (escala * 2) >= 1280 || limites.outHeight / (escala * 2) >= 1280) escala *= 2
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = escala })
    }
    val grados = context.contentResolver.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    bitmap?.let {
        val derecho = if (grados == 0f) it else Bitmap.createBitmap(it, 0, 0, it.width, it.height, Matrix().apply { postRotate(grados) }, true)
        derecho.asImageBitmap()
    }
} catch (_: Exception) {
    null
}

private fun crearArchivoTemporal(context: Context): File {
    val carpeta = File(context.cacheDir, "lab_fotos").apply { mkdirs() }
    return File(carpeta, "lab_${System.currentTimeMillis()}.jpg")
}

private fun tienePermisoCamara(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
actual fun rememberLabOcrCapture(
    onTextoReconocido: (lineas: List<String>, foto: ImageBitmap?) -> Unit,
    onError: (String) -> Unit,
): LabOcrCapture {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alReconocer by rememberUpdatedState(onTextoReconocido)
    val alFallar by rememberUpdatedState(onError)
    val permiso = remember { mutableStateOf(tienePermisoCamara(context)) }

    val permisoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        permiso.value = concedido
        if (!concedido) alFallar("Se necesita permiso de cámara para escanear el resultado")
    }

    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }

    return remember {
        LabOcrCapture(
            disponible = true,
            imageCapture = imageCapture,
            permiso = permiso,
            pedirPermiso = { permisoLauncher.launch(Manifest.permission.CAMERA) },
        ) {
            if (!tienePermisoCamara(context)) {
                permisoLauncher.launch(Manifest.permission.CAMERA)
                alFallar("Permite el acceso a la cámara y vuelve a presionar el botón")
                return@LabOcrCapture
            }
            val archivo = crearArchivoTemporal(context)
            imageCapture.takePicture(
                ImageCapture.OutputFileOptions.Builder(archivo).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(resultado: ImageCapture.OutputFileResults) {
                        val uri = Uri.fromFile(archivo)
                        scope.launch {
                            try {
                                val foto = withContext(Dispatchers.IO) { cargarFoto(context, uri) }
                                alReconocer(reconocerTexto(context, uri), foto)
                            } catch (error: Exception) {
                                alFallar(error.message ?: "No se pudo leer el texto de la foto")
                            }
                        }
                    }

                    override fun onError(error: ImageCaptureException) {
                        alFallar(error.message ?: "No se pudo tomar la foto")
                    }
                },
            )
        }
    }
}

@Composable
actual fun LabCamaraPreview(captura: LabOcrCapture, modifier: Modifier) {
    if (!captura.permisoConcedido) {
        // Pide el permiso al mostrar el visor; si lo niegan, tocar el visor lo vuelve a pedir.
        LaunchedEffect(Unit) { captura.pedirPermiso() }
        Box(modifier.clickable { captura.pedirPermiso() }, contentAlignment = Alignment.Center) {
            Text(
                "Toca para permitir el acceso a la cámara",
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            // TextureView: respeta las esquinas redondeadas y el scroll del formulario.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, captura) {
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val futuro = ProcessCameraProvider.getInstance(context)
        var proveedor: ProcessCameraProvider? = null
        futuro.addListener({
            try {
                proveedor = futuro.get().also {
                    it.unbind(preview, captura.imageCapture)
                    it.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, captura.imageCapture)
                }
            } catch (_: Exception) {
                // Sin cámara trasera disponible: el visor se queda en negro y el formulario sigue editable.
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { proveedor?.unbind(preview, captura.imageCapture) }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
