package com.example.ecolacteoshuata.presentation.produccion.inventario

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.AcentoCoral
import com.example.ecolacteoshuata.core.theme.AcentoVerde
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.KpiCard
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.domain.model.InventarioInsumoItem
import com.example.ecolacteoshuata.presentation.produccion.dashboard.formatoCantidad
import com.example.ecolacteoshuata.presentation.produccion.dashboard.stockBajo

@Composable
fun ProduccionInventarioScreen(items: List<InventarioInsumoItem>) {
    if (items.isEmpty()) {
        EmptyState("Sin insumos", "Registra insumos y compras para ver el inventario.", modifier = Modifier.padding(24.dp), icono = Icons.Filled.Inventory)
        return
    }
    // Los de stock bajo primero, para verlos de inmediato.
    val ordenados = items.sortedWith(compareByDescending<InventarioInsumoItem> { it.stockBajo() }.thenBy { it.insumo.nombre })
    val bajos = items.count { it.stockBajo() }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiCard("Insumos", items.size.toString(), AcentoVerde, Modifier.weight(1f), Icons.Filled.Inventory2)
                KpiCard("Stock bajo", bajos.toString(), if (bajos > 0) AcentoCoral else AcentoVerde, Modifier.weight(1f), Icons.Filled.Warning)
            }
        }
        item { TituloSeccion("Stock de insumos") }
        itemsIndexed(ordenados, key = { _, it -> it.insumo.id }) { index, item ->
            val simbolo = item.insumo.unidadMedidaSimbolo
            val bajo = item.stockBajo()
            StaggeredListItem(index = index) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.insumo.nombre, style = CuerpoMedioStyle.copy(fontSize = 15.sp), color = Negro)
                            when {
                                item.stock <= 0 -> EstadoChip("Sin stock", EstiloChip.ERROR, modifier = Modifier.padding(top = 4.dp))
                                bajo -> EstadoChip("Stock bajo", EstiloChip.ADVERTENCIA, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Stock", style = CuerpoStyle, color = GrisTexto)
                            Text(
                                "${formatoCantidad(item.stock)} $simbolo".trim(),
                                style = TituloPantallaStyle,
                                color = if (bajo) RojoTexto else VerdeOscuro,
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Dato("Inicial", "${formatoCantidad(item.insumo.stockActual)} $simbolo", Modifier.weight(1f))
                        Dato("Comprado", "+${formatoCantidad(item.comprado)} $simbolo", Modifier.weight(1f))
                        Dato("Consumido", "−${formatoCantidad(item.consumido)} $simbolo", Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(etiqueta, style = CuerpoStyle, color = GrisTexto)
        Text(valor.trim(), style = CuerpoMedioStyle, color = Negro)
    }
}
