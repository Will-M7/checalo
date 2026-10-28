package com.example.ecolacteoshuata.presentation.common.conexion

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.ecolacteoshuata.core.theme.*
import com.example.ecolacteoshuata.domain.platform.YapeReception
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.koin.compose.koinInject

/** La solicitud llega con la sesión habitual. Solo una respuesta confirmada cambia el consentimiento. */
@Composable
fun SolicitudRecepcionYape() {
    val reception = koinInject<YapeReception>()
    val state by reception.state.collectAsState()
    val request = state.requests.firstOrNull() ?: return
    if (!state.online) return
    var qr by remember(request.id) { mutableStateOf<ImageBitmap?>(null) }
    var qrError by remember(request.id) { mutableStateOf(false) }
    var retry by remember(request.id) { mutableStateOf(0) }
    LaunchedEffect(request.id, retry) {
        qrError = false
        try { qr = reception.qr(request)?.decodeToImageBitmap(); qrError = qr == null }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { qrError = true }
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = Blanco,
        title = { Text("Solicitud de recepción Yape", style = TituloPantallaStyle) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("El administrador te ha seleccionado para recibir las notificaciones de pago de la siguiente cuenta Yape.", style = CuerpoStyle)
                Text(request.name, style = CuerpoMedioStyle)
                Text(request.phone, style = CuerpoStyle)
                qr?.let { Image(it, "QR de la cuenta receptora autorizada", Modifier.fillMaxWidth().height(180.dp)) }
                if (qrError) TextButton(onClick = { retry++ }) { Text("No se pudo cargar el QR. Reintentar") }
                else if (qr == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Android solicitará acceso a notificaciones en Ajustes. Huata procesa únicamente las de Yape: texto, remitente, importe, hora y código cuando exista. Las conserva cifradas y las envía al servidor de la organización y Payment Core para conciliación, incluso con la aplicación cerrada. Las demás notificaciones se descartan. Puedes retirar el acceso desde Ajustes de Android.", style = CuerpoStyle)
                Text("Aceptar autoriza el uso de tu teléfono. No demuestra titularidad bancaria ni confirma dinero recibido.", style = CuerpoMedioStyle)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { reception.decide(request.id, true) }, enabled = !state.busy && qr != null) { Text("ACEPTAR") } },
        dismissButton = { TextButton(onClick = { reception.decide(request.id, false) }, enabled = !state.busy) { Text("RECHAZAR") } },
    )
}
