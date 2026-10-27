package com.example.ecolacteoshuata.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.Borde
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.Dimens
import com.example.ecolacteoshuata.core.theme.EtiquetaMayusculaStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.KpiStyle
import com.example.ecolacteoshuata.core.theme.Negro

private val FormaTarjeta = RoundedCornerShape(20.dp)

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(3.dp, FormaTarjeta, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(FormaTarjeta)
            .background(Blanco)
            .border(Dimens.cardBorder, Borde.copy(alpha = 0.6f), FormaTarjeta)
            .padding(Dimens.cardPadding + 2.dp),
        content = content,
    )
}

/**
 * Indicador numérico. Con [icono] muestra una burbuja de color; sin él, una barrita que se
 * llena al aparecer. El círculo difuso de fondo repite el color para darle identidad.
 */
@Composable
fun KpiCard(
    etiqueta: String,
    valor: String,
    colorBarra: Color,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val progreso by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(700), label = "kpi-bar")

    Box(
        modifier = modifier
            .shadow(3.dp, FormaTarjeta, ambientColor = colorBarra.copy(alpha = 0.4f), spotColor = colorBarra.copy(alpha = 0.4f))
            .clip(FormaTarjeta)
            .background(Blanco),
    ) {
        Canvas(Modifier.matchParentSize()) {
            // Un solo círculo discreto en la esquina: identidad de color sin tapar el contenido.
            drawCircle(colorBarra.copy(alpha = 0.08f), radius = size.height * 0.42f, center = Offset(size.width, 0f))
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icono != null) {
                    BurbujaIcono(icono, colorBarra, tamano = 30.dp)
                } else {
                    Box(
                        modifier = Modifier
                            .size(width = 18.dp * progreso.coerceAtLeast(0.01f), height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colorBarra),
                    )
                }
                Text(
                    etiqueta.uppercase(),
                    style = EtiquetaMayusculaStyle,
                    color = GrisTexto,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(valor, style = KpiStyle, color = Negro, modifier = Modifier.padding(top = 8.dp), maxLines = 1)
        }
    }
}

@Composable
fun InfoRow(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, style = CuerpoMedioStyle, color = GrisTexto, modifier = Modifier.weight(1f))
        Text(valor, style = CuerpoMedioStyle, color = Negro)
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Borde)
    )
}
