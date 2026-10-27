package com.example.ecolacteoshuata.presentation.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import kotlinx.coroutines.delay

/** Lo que va diciendo el botón mientras se inicia sesión. */
private val PASOS = listOf("Verificando usuario", "Conectando con el servidor", "Preparando tus datos")

/**
 * Botón "Iniciar sesión". Mientras carga, en vez del círculo de siempre muestra tres gotas de
 * leche que rebotan, un mensaje que va cambiando y un brillo que recorre el botón.
 */
@Composable
fun BotonIngresar(cargando: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(if (cargando) 2.dp else 10.dp, forma, ambientColor = VerdePrimario, spotColor = VerdePrimario)
            .clip(forma)
            .background(Brush.horizontalGradient(listOf(VerdeOscuro, VerdePrimario, Color(0xFF2E9E62))))
            .clickable(enabled = !cargando, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (cargando) BrilloRecorriendo()
        AnimatedContent(
            targetState = cargando,
            transitionSpec = { (fadeIn(tween(250)) + slideInVertically { it / 2 }) togetherWith (fadeOut(tween(150)) + slideOutVertically { -it / 2 }) },
            label = "boton-ingresar",
        ) { estaCargando ->
            if (estaCargando) Cargando() else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, tint = Blanco, modifier = Modifier.size(20.dp))
                    Text(
                        "Iniciar sesión",
                        color = Blanco,
                        style = CuerpoMedioStyle.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Cargando() {
    var paso by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_400)
            paso = (paso + 1) % PASOS.size
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        GotasDeLeche()
        AnimatedContent(
            targetState = paso,
            transitionSpec = { (fadeIn() + slideInVertically { it }) togetherWith (fadeOut() + slideOutVertically { -it }) },
            label = "paso-login",
        ) { i ->
            Text(
                PASOS[i] + "…",
                color = Blanco,
                style = CuerpoMedioStyle.copy(fontSize = 14.sp),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** Tres gotas que saltan una detrás de otra, como leche que cae. */
@Composable
private fun GotasDeLeche() {
    val infinita = rememberInfiniteTransition(label = "gotas")
    val saltos = (0 until 3).map { i ->
        infinita.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(560, delayMillis = i * 140), RepeatMode.Reverse),
            label = "gota-$i",
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(24.dp)) {
        saltos.forEach { salto ->
            Canvas(
                Modifier.size(width = 10.dp, height = 14.dp).graphicsLayer {
                    translationY = -salto.value * 8.dp.toPx()
                    alpha = 0.55f + salto.value * 0.45f
                },
            ) {
                // Gota: punta arriba y cuerpo redondo abajo.
                val w = size.width
                val h = size.height
                val gota = Path().apply {
                    moveTo(w / 2, 0f)
                    cubicTo(w * 0.95f, h * 0.45f, w, h * 0.62f, w, h * 0.68f)
                    cubicTo(w, h * 0.9f, w * 0.78f, h, w / 2, h)
                    cubicTo(w * 0.22f, h, 0f, h * 0.9f, 0f, h * 0.68f)
                    cubicTo(0f, h * 0.62f, w * 0.05f, h * 0.45f, w / 2, 0f)
                    close()
                }
                drawPath(gota, Color.White)
            }
        }
    }
}

/** Brillo diagonal que cruza el botón de lado a lado mientras carga. */
@Composable
private fun BrilloRecorriendo() {
    val avance by rememberInfiniteTransition(label = "brillo").animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(1500)), label = "brillo-x")
    Canvas(Modifier.fillMaxSize()) {
        val ancho = size.width * 0.35f
        val x = size.width * avance
        drawRect(
            brush = Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                start = Offset(x - ancho / 2, 0f),
                end = Offset(x + ancho / 2, size.height),
            ),
            topLeft = Offset(x - ancho / 2, 0f),
            size = Size(ancho, size.height),
        )
    }
}
