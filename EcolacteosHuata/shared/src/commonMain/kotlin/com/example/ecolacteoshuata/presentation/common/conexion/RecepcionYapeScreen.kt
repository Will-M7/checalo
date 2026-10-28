package com.example.ecolacteoshuata.presentation.common.conexion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.*
import com.example.ecolacteoshuata.core.ui.*
import com.example.ecolacteoshuata.domain.platform.YapeReception
import org.koin.compose.koinInject

@Composable
fun RecepcionYapeScreen() {
    val reception = koinInject<YapeReception>()
    val s by reception.state.collectAsState()
    LaunchedEffect(reception) { reception.refresh() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            TituloSeccion("Recepción Yape")
            SectionCard {
                Text("Autorización: ${if (s.authorized) "Aceptada y autorizada" else "Retirada o pendiente de consulta"}", style = CuerpoMedioStyle)
                Text("Estado: ${s.status}", style = CuerpoStyle)
                Text("Receptor: ${s.receiver.ifBlank { "Sin asociación vigente" }}", style = CuerpoStyle)
                Text(s.phone, style = CuerpoStyle)
                Text("Acceso a notificaciones: ${if (s.permission) "Concedido" else "Pendiente en Ajustes de Android"}", style = CuerpoStyle)
                Text("Listener: ${if (s.listener) "Conectado" else "Sin conexión al sistema"}", style = CuerpoStyle)
                Text("Última comunicación: ${s.lastCommunication ?: "Sin confirmar"}", style = CuerpoStyle)
                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = reception::refresh, enabled = !s.busy) { Text("Consultar estado y reintentar envío") }
                if (s.authorized && !s.permission) TextButton(onClick = reception::permissions) { Text("Conceder acceso en Ajustes de Android") }
            }
        }
        item {
            SectionCard {
                Text("Eventos pendientes de envío: ${s.pending}", style = CuerpoStyle)
                Text("Eventos enviados: ${s.sent}", style = CuerpoStyle)
                Text("Eventos en revisión: ${s.review}", style = CuerpoStyle)
                Text("Fallos de transmisión recuperables: ${s.recoverable}", style = CuerpoStyle)
                Text("Último evento detectado: ${s.recent.firstOrNull()?.time ?: "Sin eventos registrados"}", style = CuerpoStyle)
                Text("Una notificación detectada o enviada no equivale a un pago confirmado. La conciliación y los importes confirmados corresponden a Payment Core.", style = CuerpoMedioStyle)
            }
        }
        item { TituloSeccion("Notificaciones Yape recientes") }
        if (s.recent.isEmpty()) item { EmptyState("Sin notificaciones registradas", "Se mostrarán las notificaciones nuevas que Yape permita leer después de la autorización y el permiso.") }
        items(s.recent) { event ->
            SectionCard {
                Text(event.time, style = CuerpoMedioStyle)
                Text(when(event.status) {
                    "SENT" -> "Enviado para conciliación"
                    "REMOTE_REVIEW" -> "Recibido por Core para revisión"
                    "LOCAL_REVIEW", "LEGACY_REVIEW" -> "Conservado para revisión"
                    else -> "Pendiente de envío"
                }, style = CuerpoStyle)
                event.detail?.let { Text(it, style = CuerpoStyle) }
                Text("Intentos de transmisión: ${event.attempts}", style = CuerpoStyle)
            }
        }
    }
}
