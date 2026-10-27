package com.example.ecolacteoshuata.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.AcentoCeleste
import com.example.ecolacteoshuata.core.theme.AcentoCoral
import com.example.ecolacteoshuata.core.theme.AcentoVerde
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.VerdeClaro
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.domain.model.NotificacionUsuario
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** "hace 5 min", "hace 2 h", "hace 3 días", como diffForHumans() de Laravel. */
@OptIn(ExperimentalTime::class)
private fun haceCuanto(iso: String): String {
    val instante = runCatching { Instant.parse(iso) }.getOrNull() ?: return iso.take(16).replace('T', ' ')
    val minutos = (Clock.System.now() - instante).inWholeMinutes
    return when {
        minutos < 1 -> "hace un momento"
        minutos < 60 -> "hace $minutos min"
        minutos < 60 * 24 -> "hace ${minutos / 60} h"
        minutos < 60 * 24 * 30 -> (minutos / (60 * 24)).let { "hace $it ${if (it == 1L) "día" else "días"}" }
        else -> (minutos / (60 * 24 * 30)).let { "hace $it ${if (it == 1L) "mes" else "meses"}" }
    }
}

/** La campana de Laravel: no leídas resaltadas, al tocar una se marca leída. */
@Composable
fun NotificacionesSheet(
    notificaciones: List<NotificacionUsuario>,
    noLeidas: Int,
    onMarcarLeida: (NotificacionUsuario) -> Unit,
    onMarcarTodas: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { TituloSeccion("Notificaciones") }
                if (noLeidas > 0) {
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onMarcarTodas)
                            .background(VerdeClaro).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.DoneAll, contentDescription = null, tint = VerdeOscuro, modifier = Modifier.size(16.dp))
                        Text(
                            "Marcar todas como leídas",
                            style = CuerpoMedioStyle.copy(fontSize = 12.sp),
                            color = VerdeOscuro,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
            if (noLeidas > 0) {
                Text(
                    if (noLeidas == 1) "Tienes 1 notificación sin leer" else "Tienes $noLeidas notificaciones sin leer",
                    style = CuerpoStyle,
                    color = GrisTexto,
                    modifier = Modifier.padding(top = 2.dp, start = 12.dp),
                )
            }
            if (notificaciones.isEmpty()) {
                EmptyState("Sin notificaciones", "No tienes notificaciones.", icono = Icons.Filled.NotificationsNone)
            } else {
                LazyColumn(modifier = Modifier.padding(top = 10.dp, bottom = 12.dp).fillMaxWidth().heightIn(max = 520.dp)) {
                    items(notificaciones, key = { it.id }) { n -> FilaNotificacion(n, onClick = { onMarcarLeida(n) }) }
                }
            }
        }
    }
}

@Composable
private fun FilaNotificacion(n: NotificacionUsuario, onClick: () -> Unit) {
    val (icono, color) = when {
        n.esAsesoramiento -> Icons.Filled.Lightbulb to AcentoCeleste
        n.esComunicado -> Icons.Filled.Campaign to AcentoVerde
        else -> Icons.Filled.Place to AcentoCoral
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (n.leida) Color.Transparent else VerdeClaro.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(12.dp)
            .alpha(if (n.leida) 0.6f else 1f),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(n.titulo, style = CuerpoMedioStyle, color = Negro, fontWeight = if (n.leida) FontWeight.Normal else FontWeight.Bold)
            if (n.mensaje.isNotBlank()) Text(n.mensaje, style = CuerpoStyle, color = GrisTexto, modifier = Modifier.padding(top = 2.dp))
            Text(haceCuanto(n.creadaEn), style = CuerpoStyle.copy(fontSize = 11.sp), color = GrisTexto, modifier = Modifier.padding(top = 4.dp))
        }
        if (!n.leida) Box(Modifier.padding(start = 8.dp, top = 6.dp).size(9.dp).clip(CircleShape).background(AcentoVerde))
    }
}
