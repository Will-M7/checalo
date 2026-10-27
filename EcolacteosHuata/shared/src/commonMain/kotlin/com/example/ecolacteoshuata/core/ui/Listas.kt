package com.example.ecolacteoshuata.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro

// Medidas comunes de todas las listas para que las pantallas se vean ordenadas igual:
// margen de 16 dp, 12 dp entre tarjetas y espacio al final para el botón "Nuevo".

/** Relleno estándar de una lista (el inferior deja libre el botón flotante). */
val ListaPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 104.dp)

/** Separación estándar entre tarjetas de una lista. */
val ListaEspaciado = Arrangement.spacedBy(12.dp)

/** Botón flotante "Nuevo …" con el degradado del rol; se ubica abajo a la derecha. */
@Composable
fun BotonAgregar(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tema = LocalTemaRol.current
    val forma = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(18.dp)
            .shadow(10.dp, forma, ambientColor = tema.acento, spotColor = tema.acento)
            .clip(forma)
            .background(tema.degradado())
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Blanco, modifier = Modifier.size(22.dp))
        Text(texto, style = CuerpoMedioStyle, color = Blanco, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Botón compacto con el degradado del rol, para ir al costado de un [TituloSeccion]. */
@Composable
fun BotonChico(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icono: ImageVector = Icons.Filled.Add,
) {
    val tema = LocalTemaRol.current
    val forma = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(forma)
            .background(tema.degradado())
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, contentDescription = null, tint = Blanco, modifier = Modifier.size(18.dp))
        Text(texto, style = CuerpoMedioStyle, color = Blanco, modifier = Modifier.padding(start = 6.dp))
    }
}

/**
 * Tarjeta estándar de un elemento de lista, igual en todos los roles:
 * [icono] opcional a la izquierda · [titulo] y [subtitulo] al centro (se cortan con "…" para no
 * montarse sobre lo de la derecha) · [derecha] (chip, monto…) · flecha si la tarjeta se puede
 * tocar. Debajo va el [contenido] extra y al final la fila de [acciones] alineada a la derecha.
 */
@Composable
fun TarjetaLista(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    icono: ImageVector? = null,
    colorIcono: Color? = null,
    onClick: (() -> Unit)? = null,
    derecha: (@Composable () -> Unit)? = null,
    acciones: (@Composable RowScope.() -> Unit)? = null,
    contenido: (@Composable ColumnScope.() -> Unit)? = null,
) {
    SectionCard(modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (icono != null) {
                BurbujaIcono(icono, colorIcono ?: LocalTemaRol.current.acento, tamano = 38.dp)
            }
            Column(modifier = Modifier.weight(1f).padding(start = if (icono != null) 12.dp else 0.dp)) {
                Text(titulo, style = CuerpoMedioStyle.copy(fontSize = 15.sp), color = Negro, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitulo.isNullOrBlank()) {
                    Text(subtitulo, style = CuerpoStyle.copy(fontSize = 13.sp), color = GrisTexto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (derecha != null) {
                Box(modifier = Modifier.padding(start = 10.dp)) { derecha() }
            }
            if (onClick != null && acciones == null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = GrisTexto,
                    modifier = Modifier.padding(start = 4.dp).size(22.dp),
                )
            }
        }
        if (contenido != null) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { contenido() }
        }
        if (acciones != null) {
            DividerLine(modifier = Modifier.padding(top = 12.dp, bottom = 10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = acciones,
            )
        }
    }
}

/** Fila "etiqueta ........ valor" dentro de una tarjeta; el valor nunca empuja a la etiqueta. */
@Composable
fun FilaDato(etiqueta: String, valor: String, modifier: Modifier = Modifier, colorValor: Color = Negro) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, style = CuerpoStyle, color = GrisTexto, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(valor, style = CuerpoMedioStyle, color = colorValor, modifier = Modifier.padding(start = 12.dp))
    }
}
