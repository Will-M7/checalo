package com.example.ecolacteoshuata.presentation.planta.recepcion

import androidx.compose.runtime.*
import com.example.ecolacteoshuata.core.ui.FiltrosListado
import com.example.ecolacteoshuata.core.ui.CampoFiltro
import com.example.ecolacteoshuata.presentation.common.filtros.*

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.Icons
import com.example.ecolacteoshuata.core.ui.TarjetaLista
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.ui.DividerLine
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.util.formatLitros
import com.example.ecolacteoshuata.domain.model.EstadoLeche

private fun estiloEstado(estado: EstadoLeche) = when (estado) {
    EstadoLeche.NORMAL -> EstiloChip.EXITO
    EstadoLeche.SOLEADO, EstadoLeche.AGUADO -> EstiloChip.ADVERTENCIA
    EstadoLeche.ACIDIFICADO -> EstiloChip.ERROR
    EstadoLeche.OTRO -> EstiloChip.INFO
}

@Composable
fun PlantaRecepcionScreen(state: PlantaRecepcionUiState) {
    var busquedaFiltro by remember { mutableStateOf("") }
    var filtrosLista by remember { mutableStateOf(mapOf<String, String>()) }
    val filtrados = state.grupos.filter { it.recojos.isNotEmpty() && it.acopiador.nombreCompleto.contains(busquedaFiltro, true) && (filtrosLista["entrega"].isNullOrEmpty() || it.recojos.any { r -> r.entregado } == (filtrosLista["entrega"] == "1")) }

    if (state.grupos.all { it.recojos.isEmpty() }) {
        EmptyState("Sin recepción hoy", "Aún no se registran recojos para hoy.", modifier = Modifier.padding(24.dp))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
        item { FiltrosListado(busquedaFiltro, { busquedaFiltro = it }, listOf(CampoFiltro("entrega", "Entrega a planta", filtrosLista["entrega"].orEmpty(), listOf("1" to "Con entregas", "0" to "Sin entregas"))), { k, v -> filtrosLista = filtrosLista + (k to v) }, { busquedaFiltro = ""; filtrosLista = emptyMap() }, "Buscar acopiador...") }
        if (filtrados.isEmpty()) item { EmptyState("Sin coincidencias", "Prueba otra búsqueda o limpia los filtros.") }
        items(filtrados, key = { it.acopiador.id }) { grupo ->
            TarjetaLista(
                titulo = grupo.acopiador.nombreCompleto,
                subtitulo = "${grupo.recojos.size} recojo(s)",
                icono = Icons.Filled.LocalShipping,
                derecha = { Text(formatLitros(grupo.totalLitros), style = TituloPantallaStyle, color = VerdeOscuro) },
            ) {
                grupo.recojos.forEachIndexed { index, recojo ->
                    if (index > 0) DividerLine(modifier = Modifier.padding(vertical = 6.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(recojo.proveedorNombre, style = CuerpoMedioStyle, color = Negro, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatLitros(recojo.litros), style = CuerpoStyle, color = GrisTexto)
                        }
                        EstadoChip(
                            texto = if (recojo.estadoLeche == EstadoLeche.OTRO) recojo.estadoOtro.ifBlank { "Otro" } else recojo.estadoLeche.name,
                            estilo = estiloEstado(recojo.estadoLeche),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
