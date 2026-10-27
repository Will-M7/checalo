package com.example.ecolacteoshuata.presentation.common.conexion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import org.koin.compose.viewmodel.koinViewModel

/** Colores e ícono de cada aviso. */
private data class EstiloAviso(val degradado: List<Color>, val detalle: String?)

private fun estiloDe(tipo: TipoAvisoConexion) = when (tipo) {
    TipoAvisoConexion.SIN_INTERNET -> EstiloAviso(listOf(Color(0xFF7A1F2B), Color(0xFFD14343)), "Revisa el Wi-Fi o los datos móviles.")
    TipoAvisoConexion.PERDIDA -> EstiloAviso(listOf(Color(0xFF5A1F2E), Color(0xFFE0674B)), "Lo que registres se guarda en el teléfono.")
    TipoAvisoConexion.RECONECTANDO -> EstiloAviso(listOf(Color(0xFF2E2A6B), Color(0xFF5B5BD6)), "Tus datos están a salvo mientras tanto.")
    TipoAvisoConexion.RESTABLECIDA -> EstiloAviso(listOf(Color(0xFF0F5A34), Color(0xFF1FA463)), "Subiendo lo pendiente al servidor.")
    TipoAvisoConexion.INESTABLE -> EstiloAviso(listOf(Color(0xFF7A4B06), Color(0xFFE09A12)), "El servidor tarda en responder.")
}

/**
 * Aviso flotante de conexión. Se coloca arriba de cualquier pantalla (login y módulos):
 * entra desde arriba con un rebote, cambia de color y animación según el estado y se va solo
 * cuando la conexión vuelve.
 */
@Composable
fun AvisoConexionHost(modifier: Modifier = Modifier) {
    val vm = koinViewModel<AvisoConexionViewModel>()
    val aviso by vm.aviso.collectAsState()
    AvisoConexion(aviso = aviso, onReintentar = vm::reintentar, onCerrar = vm::cerrar, modifier = modifier)
}

@Composable
fun AvisoConexion(
    aviso: TipoAvisoConexion?,
    onReintentar: () -> Unit,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Se recuerda el último aviso para que no desaparezca el contenido mientras sale animado.
    val ultimo = remember { mutableUltimo() }
    if (aviso != null) ultimo.valor = aviso

    AnimatedVisibility(
        visible = aviso != null,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { -it * 2 } + fadeIn(),
        exit = slideOutVertically(tween(320)) { -it * 2 } + fadeOut(),
    ) {
        val tipo = ultimo.valor ?: return@AnimatedVisibility
        AnimatedContent(
            targetState = tipo,
            transitionSpec = { (fadeIn(tween(220)) + scaleIn(initialScale = 0.94f)) togetherWith fadeOut(tween(160)) },
            label = "aviso-conexion",
        ) { actual -> Capsula(actual, onReintentar, onCerrar) }
    }
}

private class UltimoAviso { var valor: TipoAvisoConexion? = null }

private fun mutableUltimo() = UltimoAviso()

@Composable
private fun Capsula(tipo: TipoAvisoConexion, onReintentar: () -> Unit, onCerrar: () -> Unit) {
    val estilo = estiloDe(tipo)
    val forma = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 460.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .shadow(16.dp, forma, ambientColor = estilo.degradado.last(), spotColor = estilo.degradado.last())
            .clip(forma)
            .background(Brush.horizontalGradient(estilo.degradado))
            .border(1.dp, Blanco.copy(alpha = 0.18f), forma),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconoAnimado(tipo)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(tipo.mensaje, color = Blanco, style = CuerpoMedioStyle.copy(fontSize = 14.sp), fontWeight = FontWeight.Bold)
                    if (tipo == TipoAvisoConexion.RECONECTANDO) PuntosSuspensivos()
                }
                estilo.detalle?.let { Text(it, color = Blanco.copy(alpha = 0.82f), style = CuerpoStyle.copy(fontSize = 12.sp)) }
            }
            if (tipo.ofreceReintentar) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Blanco)
                        .clickable(onClick = onReintentar)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = estilo.degradado.first(), modifier = Modifier.size(15.dp))
                    Text(
                        "Inténtalo de nuevo.",
                        color = estilo.degradado.first(),
                        style = CuerpoMedioStyle.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            if (tipo != TipoAvisoConexion.RESTABLECIDA) {
                Box(
                    modifier = Modifier.padding(start = 4.dp).size(30.dp).clip(CircleShape).clickable(onClick = onCerrar),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Blanco.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                }
            }
        }
        // Reconectando: una línea de luz que recorre la cápsula.
        if (tipo == TipoAvisoConexion.RECONECTANDO) BarraBarrido()
    }
}

/** Ícono en burbuja con una animación distinta para cada estado. */
@Composable
private fun IconoAnimado(tipo: TipoAvisoConexion) {
    val infinita = rememberInfiniteTransition(label = "icono-conexion")
    val pulso by infinita.animateFloat(0f, 1f, infiniteRepeatable(tween(1400)), label = "pulso")
    val giro by infinita.animateFloat(0f, 360f, infiniteRepeatable(tween(1100)), label = "giro")
    val vaiven by infinita.animateFloat(-10f, 10f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "vaiven")

    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        // Ondas que se expanden alrededor del ícono.
        if (tipo == TipoAvisoConexion.PERDIDA || tipo == TipoAvisoConexion.RESTABLECIDA) {
            Box(
                Modifier.size(44.dp).scale(0.6f + pulso * 0.6f).alpha(1f - pulso)
                    .border(2.dp, Blanco, CircleShape),
            )
        }
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(Blanco.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            when (tipo) {
                TipoAvisoConexion.SIN_INTERNET ->
                    Icon(Icons.Filled.WifiOff, null, tint = Blanco, modifier = Modifier.size(20.dp).rotate(vaiven))
                TipoAvisoConexion.PERDIDA ->
                    Icon(Icons.Filled.WifiOff, null, tint = Blanco, modifier = Modifier.size(20.dp).alpha(0.5f + (1f - pulso) * 0.5f))
                TipoAvisoConexion.RECONECTANDO ->
                    Icon(Icons.Filled.Sync, null, tint = Blanco, modifier = Modifier.size(20.dp).rotate(-giro))
                TipoAvisoConexion.RESTABLECIDA -> CheckRebote()
                TipoAvisoConexion.INESTABLE -> BarrasSenal()
            }
        }
    }
}

/** Wi-Fi con un check que aparece con rebote. */
@Composable
private fun CheckRebote() {
    val escala = remember { Animatable(0f) }
    LaunchedEffect(Unit) { escala.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow)) }
    Box(contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Wifi, null, tint = Blanco, modifier = Modifier.size(20.dp))
        Icon(
            Icons.Filled.CheckCircle,
            null,
            tint = Color(0xFF8FE3B4),
            modifier = Modifier.size(13.dp).align(Alignment.BottomEnd).scale(escala.value),
        )
    }
}

/** Cuatro barras de señal que suben y bajan a destiempo: la señal va y viene. */
@Composable
private fun BarrasSenal() {
    val infinita = rememberInfiniteTransition(label = "barras")
    val fases = (0 until 4).map { i ->
        infinita.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(520 + i * 170), RepeatMode.Reverse),
            label = "barra-$i",
        )
    }
    Canvas(Modifier.size(width = 20.dp, height = 18.dp)) {
        val ancho = size.width / 7f
        fases.forEachIndexed { i, fase ->
            val maximo = size.height * (0.4f + i * 0.2f)
            val alto = maximo * fase.value
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(x = i * ancho * 2f, y = size.height - alto),
                size = Size(ancho, alto),
                cornerRadius = CornerRadius(ancho / 2f),
            )
        }
    }
}

/** "Reconectando" + puntos que aparecen uno a uno. */
@Composable
private fun PuntosSuspensivos() {
    val fase by rememberInfiniteTransition(label = "puntos").animateFloat(0f, 4f, infiniteRepeatable(tween(1200)), label = "puntos-fase")
    Row(modifier = Modifier.padding(start = 2.dp, bottom = 3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { i ->
            Box(Modifier.size(4.dp).alpha(if (fase > i + 1) 1f else 0.25f).background(Blanco, CircleShape))
        }
    }
}

/** Una franja de luz que recorre la base de la cápsula de lado a lado. */
@Composable
private fun BarraBarrido() {
    val avance by rememberInfiniteTransition(label = "barrido").animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(1300)), label = "barrido-x")
    Canvas(Modifier.fillMaxWidth().height(3.dp)) {
        drawRect(Color.White.copy(alpha = 0.15f))
        val ancho = size.width * 0.3f
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White, Color.Transparent),
                startX = size.width * avance - ancho / 2,
                endX = size.width * avance + ancho / 2,
            ),
            topLeft = Offset(size.width * avance - ancho / 2, 0f),
            size = Size(ancho, size.height),
        )
    }
}
