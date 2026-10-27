package com.example.ecolacteoshuata.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.Ambar
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.EtiquetaMayusculaStyle
import com.example.ecolacteoshuata.core.theme.VerdeMenta
import com.example.ecolacteoshuata.domain.model.EstadoSincronizacion

@Composable
fun SyncBanner(estado: EstadoSincronizacion, pendientes: Int, modifier: Modifier = Modifier) {
    val (texto, color) = when (estado) {
        EstadoSincronizacion.SIN_SENAL -> "Sin señal — trabajando sin conexión" to Ambar
        EstadoSincronizacion.SINCRONIZANDO -> "Sincronizando datos…" to Color(0xFFFFFFFF).copy(alpha = 0.85f)
        EstadoSincronizacion.SINCRONIZADO -> "Sincronizado con el servidor" to VerdeMenta
        EstadoSincronizacion.MODO_LOCAL -> "Modo local (sin servidor)" to Color(0xFFFFFFFF).copy(alpha = 0.6f)
    }
    val transition = rememberInfiniteTransition(label = "sync-dot")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), repeatMode = RepeatMode.Reverse),
        label = "sync-dot-alpha",
    )

    Row(
        modifier = modifier
            .background(Blanco.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .graphicsLayer { this.alpha = alpha }
                .background(color, CircleShape)
        )
        Text(
            text = if (pendientes > 0) "$texto · $pendientes pendiente(s)" else texto,
            style = EtiquetaMayusculaStyle,
            color = Blanco,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
