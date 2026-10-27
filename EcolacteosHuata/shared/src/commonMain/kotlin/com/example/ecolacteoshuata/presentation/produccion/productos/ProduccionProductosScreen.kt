package com.example.ecolacteoshuata.presentation.produccion.productos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.AcentoCeleste
import com.example.ecolacteoshuata.core.theme.AcentoVerde
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.KpiCard
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.util.formatDdMmmYyyy
import com.example.ecolacteoshuata.core.util.formatMoney

private fun cantidad(v: Double): String = v.toString().removeSuffix(".0")

/** Productos: todo lo que ya se terminó de producir (producciones publicadas), listo para vender. */
@Composable
fun ProduccionProductosScreen(
    productos: List<ProductoTerminado>,
    busqueda: String,
    onBusquedaChange: (String) -> Unit,
    /** Vendedor: el estado de stock de Laravel (Sin stock / Cantidad baja / Disponible) en vez de "Completada". */
    estadoStock: Boolean = false,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
        item {
            AppTextField(busqueda, onBusquedaChange, "Buscar por receta...")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiCard("Unidades disponibles", cantidad(productos.sumOf { it.registro.disponible }), AcentoVerde, Modifier.weight(1f), Icons.Filled.Inventory2)
                KpiCard("Valor total", formatMoney(productos.sumOf { it.precioTotal }), AcentoCeleste, Modifier.weight(1f), Icons.Filled.Payments)
            }
        }
        item { TituloSeccion("Listos para vender (${productos.size})") }
        if (productos.isEmpty()) {
            item {
                EmptyState(
                    "Sin productos",
                    if (busqueda.isBlank()) "Publica una producción para que aparezca aquí." else "No se encontraron producciones.",
                    icono = Icons.Filled.Storefront,
                )
            }
        }
        itemsIndexed(productos, key = { _, it -> it.registro.id }) { index, producto ->
            val registro = producto.registro
            StaggeredListItem(index = index) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(registro.nombre, style = CuerpoMedioStyle.copy(fontSize = 15.sp), color = Negro)
                            Text(registro.fecha.formatDdMmmYyyy(), style = CuerpoStyle, color = GrisTexto)
                        }
                        when {
                            !estadoStock -> EstadoChip("Completada", EstiloChip.EXITO)
                            registro.disponible <= 0 -> EstadoChip("Sin stock", EstiloChip.ERROR)
                            registro.disponible <= 5 -> EstadoChip("Cantidad baja", EstiloChip.ADVERTENCIA)
                            else -> EstadoChip("Disponible", EstiloChip.EXITO)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Dato("Disponible", "${cantidad(registro.disponible)} de ${cantidad(registro.cantidadProducida)}", Modifier.weight(1f))
                        Dato("Precio unidad", formatMoney(producto.precioUnidad), Modifier.weight(1f))
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Precio total", style = CuerpoStyle, color = GrisTexto)
                            Text(formatMoney(producto.precioTotal), style = TituloPantallaStyle, color = VerdeOscuro)
                        }
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
        Text(valor, style = CuerpoMedioStyle, color = Negro)
    }
}
