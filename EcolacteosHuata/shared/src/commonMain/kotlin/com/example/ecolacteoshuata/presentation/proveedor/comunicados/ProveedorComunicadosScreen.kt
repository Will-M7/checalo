package com.example.ecolacteoshuata.presentation.proveedor.comunicados

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.ui.TarjetaLista
import com.example.ecolacteoshuata.core.ui.fondoOpcion
import com.example.ecolacteoshuata.core.ui.textoOpcion
import com.example.ecolacteoshuata.core.util.formatDdMmmYyyy
import com.example.ecolacteoshuata.domain.model.Comunicado
import com.example.ecolacteoshuata.domain.model.TipoComunicado

private val FILTROS = listOf<Pair<String, TipoComunicado?>>(
    "Todos" to null,
    "Comunicados" to TipoComunicado.COMUNICADO,
    "Asesoramiento" to TipoComunicado.ASESORAMIENTO,
)

@Composable
fun ProveedorComunicadosScreen(
    comunicados: List<Comunicado>,
    filtroTipo: TipoComunicado?,
    onFiltrar: (TipoComunicado?) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
        item(key = "ayuda") {
            Text("Avisos publicados por la planta.", style = CuerpoStyle, color = GrisTexto)
        }
        item(key = "filtro") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FILTROS.forEach { (etiqueta, tipo) ->
                    val activa = tipo == filtroTipo
                    Text(
                        etiqueta,
                        style = CuerpoMedioStyle,
                        color = textoOpcion(activa),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(fondoOpcion(activa))
                            .clickable { onFiltrar(tipo) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        if (comunicados.isEmpty()) {
            item(key = "vacio") {
                EmptyState("Sin comunicados", "No hay comunicados publicados todavía.", icono = Icons.Filled.Campaign)
            }
        }
        itemsIndexed(comunicados, key = { _, c -> c.id }) { index, comunicado ->
            StaggeredListItem(index = index) {
                // Mismo formato que la lista de comunicados de planta.
                val esAsesoria = comunicado.tipo == TipoComunicado.ASESORAMIENTO
                TarjetaLista(
                    titulo = comunicado.titulo,
                    subtitulo = comunicado.fecha.formatDdMmmYyyy(),
                    icono = if (esAsesoria) Icons.Filled.Lightbulb else Icons.Filled.Campaign,
                    derecha = { EstadoChip(if (esAsesoria) "Asesoramiento" else "Comunicado", if (esAsesoria) EstiloChip.INFO else EstiloChip.EXITO) },
                ) {
                    Text(comunicado.descripcion, style = CuerpoStyle, color = Negro)
                }
            }
        }
    }
}
