package com.example.ecolacteoshuata.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.Borde
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.EtiquetaMayusculaStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle

/** Color del diálogo: el del rol, rojo para acciones que borran, verde para lo que salió bien. */
enum class TonoDialogo { NORMAL, PELIGRO, EXITO }

/** Un dato destacado dentro del diálogo (ej. "Litros · 37.5 L"). */
data class DatoDialogo(val etiqueta: String, val valor: String, val icono: ImageVector)

private val RojoIntenso = Color(0xFFD14343)
private val RojoSuave = Color(0xFFF07A5A)
private val VerdeExito = Color(0xFF17663D)
private val VerdeExitoClaro = Color(0xFF3FB97A)

@Composable
private fun coloresDe(tono: TonoDialogo): Pair<Color, Brush> {
    val tema = LocalTemaRol.current
    return when (tono) {
        TonoDialogo.NORMAL -> tema.acento to tema.degradado()
        TonoDialogo.PELIGRO -> RojoIntenso to Brush.linearGradient(listOf(RojoIntenso, RojoSuave))
        TonoDialogo.EXITO -> VerdeExito to Brush.linearGradient(listOf(VerdeExito, VerdeExitoClaro))
    }
}

/**
 * Diálogo de la app (en vez del AlertDialog del sistema): cabecera con el degradado del rol y
 * ondas, ícono que late, título, mensaje, datos destacados y botones en forma de píldora.
 * Sin [onCancelar] muestra un solo botón (útil para avisos de "¡Listo!").
 */
@Composable
fun DialogoConfirmacion(
    titulo: String,
    mensaje: String,
    icono: ImageVector,
    textoConfirmar: String,
    onConfirmar: () -> Unit,
    onCancelar: (() -> Unit)? = null,
    textoCancelar: String = "Cancelar",
    tono: TonoDialogo = TonoDialogo.NORMAL,
    datos: List<DatoDialogo> = emptyList(),
    cargando: Boolean = false,
) {
    val (color, degradado) = coloresDe(tono)
    val cerrar = { if (!cargando) (onCancelar ?: onConfirmar)() }
    val aparicion = remember { MutableTransitionState(false).apply { targetState = true } }

    Dialog(onDismissRequest = cerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AnimatedVisibility(
            visibleState = aparicion,
            enter = fadeIn(tween(220)) +
                scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), initialScale = 0.85f) +
                slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 6 },
        ) {
            val forma = RoundedCornerShape(30.dp)
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .shadow(24.dp, forma, ambientColor = color, spotColor = color)
                    .clip(forma)
                    .background(Blanco),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CabeceraDialogo(icono = icono, color = color, degradado = degradado)

                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(titulo, style = TituloPantallaStyle.copy(fontSize = 20.sp), color = Negro, textAlign = TextAlign.Center)
                    Text(
                        mensaje,
                        style = CuerpoStyle.copy(lineHeight = 20.sp),
                        color = GrisTexto,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )

                    if (datos.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        datos.chunked(2).forEach { fila ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                fila.forEach { dato -> TarjetaDato(dato, color, Modifier.weight(1f)) }
                                if (fila.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (onCancelar != null) {
                            BotonPildora(
                                texto = textoCancelar,
                                fondo = null,
                                colorTexto = GrisTexto,
                                habilitado = !cargando,
                                onClick = onCancelar,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        BotonPildora(
                            texto = textoConfirmar,
                            fondo = degradado,
                            colorTexto = Blanco,
                            habilitado = !cargando,
                            cargando = cargando,
                            onClick = onConfirmar,
                            modifier = Modifier.weight(if (onCancelar != null) 1.3f else 1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CabeceraDialogo(icono: ImageVector, color: Color, degradado: Brush) {
    val transicion = rememberInfiniteTransition(label = "dialogo")
    val halo by transicion.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "halo")
    val latido by transicion.animateFloat(1f, 1.08f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "latido")

    Box(
        modifier = Modifier.fillMaxWidth().height(128.dp).background(degradado),
        contentAlignment = Alignment.Center,
    ) {
        OndasDecorativas(Modifier.matchParentSizeSafe())
        // Burbujas decorativas
        Box(Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 18.dp).size(14.dp).background(Blanco.copy(alpha = 0.18f), CircleShape))
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 30.dp, bottom = 16.dp).size(22.dp).background(Blanco.copy(alpha = 0.14f), CircleShape))
        Box(Modifier.align(Alignment.TopEnd).padding(end = 56.dp, top = 26.dp).size(8.dp).background(Blanco.copy(alpha = 0.25f), CircleShape))

        // Halo que se expande y el ícono que late
        Box(
            Modifier
                .size(76.dp)
                .scale(1f + 0.45f * halo)
                .background(Blanco.copy(alpha = 0.30f * (1f - halo)), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(76.dp)
                .scale(latido)
                .shadow(10.dp, CircleShape)
                .background(Blanco, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(38.dp))
        }
    }
}

/** OndasDecorativas ocupa todo el alto de la cabecera. */
private fun Modifier.matchParentSizeSafe(): Modifier = this.fillMaxWidth().height(128.dp)

@Composable
private fun TarjetaDato(dato: DatoDialogo, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).background(color.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(dato.icono, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
        }
        Column(Modifier.padding(start = 8.dp)) {
            Text(dato.etiqueta.uppercase(), style = EtiquetaMayusculaStyle.copy(fontSize = 9.sp), color = GrisTexto, maxLines = 1)
            Text(
                dato.valor,
                style = CuerpoMedioStyle.copy(fontSize = 14.sp),
                color = Negro,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BotonPildora(
    texto: String,
    fondo: Brush?,
    colorTexto: Color,
    habilitado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cargando: Boolean = false,
) {
    val forma = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .heightIn(min = 50.dp)
            .clip(forma)
            .then(if (fondo != null) Modifier.background(fondo) else Modifier.border(1.5.dp, Borde, forma))
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (cargando) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = colorTexto, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Un momento…", style = CuerpoMedioStyle, color = colorTexto)
            }
        } else {
            Text(texto, style = CuerpoMedioStyle.copy(fontSize = 15.sp), color = colorTexto, textAlign = TextAlign.Center)
        }
    }
}
