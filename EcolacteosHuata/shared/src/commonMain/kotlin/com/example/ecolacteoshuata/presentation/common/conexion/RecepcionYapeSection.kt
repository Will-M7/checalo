package com.example.ecolacteoshuata.presentation.common.conexion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.ui.*
import com.example.ecolacteoshuata.domain.platform.YapeReception
import org.koin.compose.koinInject

/** Small opt-in section inside the existing configuration sheet; no extra role screen. */
@Composable
fun RecepcionYapeSection() {
    val reception = koinInject<YapeReception>()
    val s by reception.state.collectAsState()
    var disclosure by remember { mutableStateOf(false) }
    LaunchedEffect(reception) { reception.refresh() }
    if (!s.authorized) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("RECEPCIÓN YAPE", style = MaterialTheme.typography.titleMedium)
        Text("Estado: ${s.status}")
        Text("Receptor: ${s.receiver}")
        Text("Última comunicación: ${s.lastCommunication ?: "Sin confirmar"}")
        Text("Eventos pendientes: ${s.pending} · Para revisión: ${s.review}")
        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!s.enabled) PrimaryButton("Activar recepción", { disclosure = true }, habilitado = !s.busy)
        else {
            TextButton(onClick = reception::pause) { Text("Desactivar recepción") }
            if (!s.permission) TextButton(onClick = reception::permissions) { Text("Permiso de notificaciones de Android") }
        }
        TextButton(onClick = reception::refresh) { Text("Consultar autorización y conexión") }
    }
    if (disclosure) AlertDialog(onDismissRequest = { disclosure = false },
        title = { Text("Permitir recepción Yape en Huata") },
        text = { Text("Android concederá acceso a las notificaciones. Huata procesa únicamente las de Yape para este receptor autorizado: texto, titular/remitente, importe, fecha y código cuando exista. Las guarda cifradas y las envía al servidor de la organización y Payment Core para conciliación, incluso con Huata cerrada. Las notificaciones de otras aplicaciones se descartan. Una notificación no confirma dinero. Puedes desactivar esta función y retirar el permiso en Ajustes. ¿Aceptas activar esta recepción?") },
        confirmButton = { TextButton(onClick = { disclosure=false; reception.activate() }) { Text("Acepto y abrir permiso Android") } },
        dismissButton = { TextButton(onClick = { disclosure=false }) { Text("Cancelar") } })
}
