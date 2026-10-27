package com.example.ecolacteoshuata.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.PaletaModulos
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.BurbujaIcono
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem

/**
 * Un módulo del rol. [color] por defecto se toma de [PaletaModulos] según su posición, así
 * cada módulo conserva el mismo color en el panel y en la hoja "Más".
 */
data class ModuloItem(
    val etiqueta: String,
    val icono: ImageVector,
    val descripcion: String = "",
    val color: Color? = null,
    val onClick: () -> Unit,
)

private fun ModuloItem.colorEn(indice: Int): Color = color ?: PaletaModulos[indice % PaletaModulos.size]

@Composable
fun MasSheet(titulo: String, items: List<ModuloItem>, onDismiss: () -> Unit) {
    AppBottomSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp)) {
            Text(titulo, style = TituloPantallaStyle, color = Negro)
            Text("Toca un módulo para abrirlo", style = CuerpoMedioStyle, color = GrisTexto, modifier = Modifier.padding(bottom = 14.dp))
            items.chunked(3).forEachIndexed { fila, grupo ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    grupo.forEachIndexed { col, item ->
                        val indice = fila * 3 + col
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(item.colorEn(indice).copy(alpha = 0.08f))
                                .clickable {
                                    item.onClick()
                                    onDismiss()
                                }
                                .padding(vertical = 14.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BurbujaIcono(item.icono, item.colorEn(indice))
                            Text(
                                item.etiqueta,
                                style = CuerpoMedioStyle.copy(fontSize = 12.sp),
                                color = Negro,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp).height(34.dp),
                            )
                        }
                    }
                    repeat(3 - grupo.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Cuadrícula de módulos del panel: tarjetas de dos columnas con color propio. */
@Composable
fun ModulosGrid(modulos: List<ModuloItem>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        modulos.chunked(2).forEachIndexed { fila, grupo ->
            // Misma altura para las dos tarjetas de la fila aunque una descripción sea más larga.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                grupo.forEachIndexed { col, modulo ->
                    val indice = fila * 2 + col
                    StaggeredListItem(index = indice, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        TarjetaModulo(modulo, modulo.colorEn(indice))
                    }
                }
                if (grupo.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TarjetaModulo(modulo: ModuloItem, color: Color) {
    SectionCard(modifier = Modifier.fillMaxHeight().clickable { modulo.onClick() }) {
        Row(verticalAlignment = Alignment.Top) {
            BurbujaIcono(modulo.icono, color)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.1f)).padding(5.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = color, modifier = Modifier.padding(1.dp))
            }
        }
        Text(
            modulo.etiqueta,
            style = CuerpoMedioStyle.copy(fontSize = 14.sp),
            color = Negro,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (modulo.descripcion.isNotBlank()) {
            Text(
                modulo.descripcion,
                style = CuerpoStyle.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                color = GrisTexto,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Contenedor desplazable estándar de los paneles. */
@Composable
fun PanelScroll(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        content()
        Spacer(Modifier.height(8.dp))
    }
}
