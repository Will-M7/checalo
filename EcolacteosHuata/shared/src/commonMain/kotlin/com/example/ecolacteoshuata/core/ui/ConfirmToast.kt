package com.example.ecolacteoshuata.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.VerdeProfundo
import kotlinx.coroutines.delay

/** Tipo de aviso flotante: cambia color e ícono. */
enum class TipoAviso { EXITO, ERROR, INFO }

private fun estiloDe(tipo: TipoAviso): Triple<Brush, Color, ImageVector> = when (tipo) {
    TipoAviso.EXITO -> Triple(Brush.horizontalGradient(listOf(VerdeProfundo, Color(0xFF17663D))), Color(0xFF8FE3B4), Icons.Filled.CheckCircle)
    TipoAviso.ERROR -> Triple(Brush.horizontalGradient(listOf(Color(0xFF7A1F1F), Color(0xFFD14343))), Color(0xFFFFC2B8), Icons.Filled.ErrorOutline)
    TipoAviso.INFO -> Triple(Brush.horizontalGradient(listOf(Color(0xFF0E4A63), Color(0xFF1E9BC8))), Color(0xFFB8E8F8), Icons.Filled.Info)
}

/**
 * Aviso flotante que entra desde abajo con un rebote, con ícono y degradado según [tipo], y
 * se va solo a los pocos segundos.
 */
@Composable
fun ConfirmToastHost(
    mensaje: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tipo: TipoAviso = TipoAviso.EXITO,
) {
    // Se recuerda el último texto para que no desaparezca mientras se anima la salida.
    var ultimo by remember { mutableStateOf("") }
    if (mensaje != null) ultimo = mensaje
    LaunchedEffect(mensaje) {
        if (mensaje != null) {
            delay(2800)
            onDismiss()
        }
    }
    val (fondo, colorIcono, icono) = estiloDe(tipo)
    Box(modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = mensaje != null,
            enter = slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { it } +
                scaleIn(initialScale = 0.9f) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            val forma = RoundedCornerShape(22.dp)
            Row(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .shadow(14.dp, forma)
                    .background(fondo, forma)
                    .padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).background(Blanco.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icono, contentDescription = null, tint = colorIcono, modifier = Modifier.size(22.dp))
                }
                Text(ultimo, color = Blanco, style = CuerpoMedioStyle, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}
