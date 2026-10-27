package com.example.ecolacteoshuata.presentation.vendedor.inventario

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
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.AcentoCeleste
import com.example.ecolacteoshuata.core.theme.AcentoCoral
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
import com.example.ecolacteoshuata.presentation.produccion.dashboard.formatoCantidad
import com.example.ecolacteoshuata.presentation.produccion.productos.ProductoTerminado

/** Igual que Laravel: hasta 5 unidades es "Stock bajo". */
private const val STOCK_BAJO = 5.0

/**
 * Inventario del vendedor: cantidad disponible de lo producido (producciones publicadas),
 * lista para la venta. Mismos totales y estados que la pantalla de Laravel.
 */
@Composable
fun VendedorInventarioScreen(
    productos: List<ProductoTerminado>,
    busqueda: String,
    onBusquedaChange: (String) -> Unit,
) {
    val sinStock = productos.count { it.registro.disponible <= 0 }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
        // 1. Totales.
        item {
            KpiCard("Valor del inventario", formatMoney(productos.sumOf { it.precioTotal }), AcentoVerde, Modifier.fillMaxWidth(), Icons.Filled.Payments)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiCard("Unidades disponibles", formatoCantidad(productos.sumOf { it.registro.disponible }), AcentoCeleste, Modifier.weight(1f), Icons.Filled.Inventory2)
                KpiCard("Sin stock", sinStock.toString(), if (sinStock > 0) AcentoCoral else AcentoVerde, Modifier.weight(1f), Icons.Filled.RemoveShoppingCart)
            }
        }

        // 2. Búsqueda.
        item { AppTextField(busqueda, onBusquedaChange, "Buscar por receta...") }

        // 3. Productos listos para vender.
        item { TituloSeccion("Productos (${productos.size})") }
        if (productos.isEmpty()) {
            item {
                EmptyState(
                    "Sin productos",
                    if (busqueda.isBlank()) "Cuando producción publique lo producido, aparecerá aquí." else "No se encontraron productos.",
                    icono = Icons.Filled.Inventory,
                )
            }
        }
        itemsIndexed(productos, key = { _, it -> it.registro.id }) { index, producto ->
            val disponible = producto.registro.disponible
            StaggeredListItem(index = index) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(producto.registro.nombre, style = CuerpoMedioStyle.copy(fontSize = 15.sp), color = Negro)
                            Text("Producido el ${producto.registro.fecha.formatDdMmmYyyy()}", style = CuerpoStyle, color = GrisTexto)
                        }
                        when {
                            disponible <= 0 -> EstadoChip("Sin stock", EstiloChip.ERROR)
                            disponible <= STOCK_BAJO -> EstadoChip("Stock bajo", EstiloChip.ADVERTENCIA)
                            else -> EstadoChip("Disponible", EstiloChip.EXITO)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Dato("Precio unidad", formatMoney(producto.precioUnidad), Modifier.weight(1f))
                        Dato("Disponible", formatoCantidad(disponible), Modifier.weight(1f))
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Valor en stock", style = CuerpoStyle, color = GrisTexto)
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
