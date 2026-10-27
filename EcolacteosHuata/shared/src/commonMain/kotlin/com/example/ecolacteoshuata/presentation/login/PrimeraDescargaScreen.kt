package com.example.ecolacteoshuata.presentation.login

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.EtiquetaMayusculaStyle
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdeMenta
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.theme.VerdeProfundo
import com.example.ecolacteoshuata.core.ui.LogoHuata
import com.example.ecolacteoshuata.core.ui.OndasDecorativas
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import ecolacteoshuata.shared.generated.resources.Res
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun PrimeraDescargaScreen(
    state: PrimeraDescargaUiState,
    onReintentar: () -> Unit,
    onContinuar: () -> Unit,
    onOmitir: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(VerdeProfundo, VerdeOscuro, VerdePrimario))),
    ) {
        OndasDecorativas(Modifier.fillMaxSize(), Blanco.copy(alpha = 0.06f))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            LogoHuata(tamano = 96.dp, animado = false)
            Text(
                when (state.etapa) {
                    EtapaDescarga.LISTA -> "¡Todo listo!"
                    EtapaDescarga.ERROR -> "No se pudo descargar"
                    else -> "Preparando tu app"
                },
                style = TituloPantallaStyle.copy(fontSize = 26.sp),
                color = Blanco,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                when (state.etapa) {
                    EtapaDescarga.LISTA -> "Los datos del sistema ya están en tu teléfono. Podrás trabajar aunque no haya señal."
                    EtapaDescarga.ERROR -> state.error.orEmpty()
                    else -> "Es la primera vez que esta app se conecta al sistema. Estamos descargando todo desde ${state.servidor}."
                },
                style = CuerpoStyle,
                color = Blanco.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(28.dp))
            VasoDeLeche(
                fraccion = when (state.etapa) {
                    EtapaDescarga.LISTA -> 1f
                    EtapaDescarga.ERROR -> 0f
                    else -> state.progreso?.fraccion ?: 0.03f
                },
                icono = when (state.etapa) {
                    EtapaDescarga.LISTA -> Icons.Filled.CloudDone
                    EtapaDescarga.ERROR -> Icons.Filled.CloudOff
                    else -> Icons.Filled.CloudDownload
                },
            )
            if (state.etapa == EtapaDescarga.DESCARGANDO) {
                Text(
                    state.progreso?.etapa ?: "Conectando…",
                    style = CuerpoMedioStyle,
                    color = Blanco,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            if (state.etapa == EtapaDescarga.LISTA && state.resumen.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Blanco.copy(alpha = 0.1f), RoundedCornerShape(22.dp))
                        .border(1.dp, Blanco.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("DESCARGADO", style = EtiquetaMayusculaStyle, color = VerdeMenta)
                    state.resumen.filter { it.cantidad > 0 }.chunked(2).forEach { fila ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            fila.forEach { c ->
                                Row(
                                    modifier = Modifier.weight(1f).background(Blanco.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(c.etiqueta, style = CuerpoMedioStyle.copy(fontSize = 12.sp), color = Blanco.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                                    Text(c.cantidad.toString(), style = TituloPantallaStyle.copy(fontSize = 15.sp), color = Blanco)
                                }
                            }
                            if (fila.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            when (state.etapa) {
                EtapaDescarga.LISTA -> PrimaryButton("Comenzar", onClick = onContinuar)
                EtapaDescarga.ERROR -> {
                    PrimaryButton("Reintentar", onClick = onReintentar)
                    TextButton(onClick = onOmitir, modifier = Modifier.padding(top = 6.dp)) {
                        Text("Cambiar servidor o entrar sin descargar", color = Blanco)
                    }
                }
                else -> Unit
            }
        }
    }
}

/** Un vaso que se llena de leche con olas animadas a medida que avanza la descarga. */
@Composable
private fun VasoDeLeche(fraccion: Float, icono: androidx.compose.ui.graphics.vector.ImageVector) {
    val nivel by animateFloatAsState(fraccion.coerceIn(0f, 1f), tween(700), label = "nivel")
    val fase by rememberInfiniteTransition(label = "ola").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "ola-fase",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(width = 150.dp, height = 180.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val vaso = Path().apply {
                moveTo(size.width * 0.08f, 0f)
                lineTo(size.width * 0.92f, 0f)
                lineTo(size.width * 0.8f, size.height)
                lineTo(size.width * 0.2f, size.height)
                close()
            }
            drawPath(vaso, Blanco.copy(alpha = 0.12f))
            val superficie = size.height * (1f - nivel)
            val leche = Path().apply {
                moveTo(0f, size.height)
                var x = 0f
                lineTo(0f, superficie)
                while (x <= size.width) {
                    lineTo(x, superficie + 6f * sin(x / size.width * 4 * PI.toFloat() + fase))
                    x += 6f
                }
                lineTo(size.width, size.height)
                close()
            }
            clipPath(vaso) {
                if (nivel > 0f) drawPath(leche, Color(0xFFFFFBF2))
            }
            drawPath(vaso, Blanco.copy(alpha = 0.6f), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icono, null, tint = if (nivel > 0.55f) VerdePrimario else Blanco, modifier = Modifier.size(30.dp))
            Text(
                "${(nivel * 100).toInt()}%",
                style = TituloPantallaStyle.copy(fontSize = 24.sp),
                color = if (nivel > 0.45f) VerdeProfundo else Blanco,
            )
        }
    }
}
