package com.example.ecolacteoshuata.presentation.produccion.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.AcentoAmbar
import com.example.ecolacteoshuata.core.theme.AcentoCeleste
import com.example.ecolacteoshuata.core.theme.AcentoCoral
import com.example.ecolacteoshuata.core.theme.AcentoVerde
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.ui.DividerLine
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.HeroMetricCard
import com.example.ecolacteoshuata.core.ui.KpiCard
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.util.formatMoney
import com.example.ecolacteoshuata.presentation.common.ModuloItem
import com.example.ecolacteoshuata.presentation.common.ModulosGrid
import com.example.ecolacteoshuata.presentation.common.PanelScroll

@Composable
fun ProduccionDashboardScreen(state: ProduccionDashboardUiState, modulos: List<ModuloItem>) {
    PanelScroll {
        // 1. Lo del día.
        HeroMetricCard(
            etiqueta = "Producción de hoy",
            valor = "${state.produccionesHoy} producción(es)",
            detalle = if (state.porPublicar > 0) "${state.porPublicar} planificada(s) por publicar" else "Todo publicado",
            icono = Icons.Filled.Factory,
        )

        // 2. Accesos, en el orden del trabajo: producir → recetas → insumos → compras → inventario → productos.
        TituloSeccion("Accesos rápidos")
        ModulosGrid(modulos)

        // 3. Resumen en cuadrícula 2 x 2.
        TituloSeccion("Resumen de hoy")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Por publicar", state.porPublicar.toString(), AcentoAmbar, Modifier.weight(1f), Icons.Filled.PendingActions)
            KpiCard("Listos para vender", state.listosParaVender.toString(), AcentoVerde, Modifier.weight(1f), Icons.Filled.Storefront)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Insumos", state.totalInsumos.toString(), AcentoCeleste, Modifier.weight(1f), Icons.Filled.Inventory2)
            KpiCard("Compras del mes", formatMoney(state.comprasDelMesTotal), AcentoCoral, Modifier.weight(1f), Icons.Filled.ShoppingCart)
        }

        // 4. Alertas de stock bajo.
        TituloSeccion("Stock bajo")
        SectionCard {
            if (state.stockBajo.isEmpty()) {
                Text("Todos los insumos tienen stock suficiente.", style = CuerpoStyle, color = GrisTexto)
            }
            state.stockBajo.take(5).forEachIndexed { index, item ->
                if (index > 0) DividerLine(Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.insumo.nombre, style = CuerpoMedioStyle, color = Negro, modifier = Modifier.weight(1f))
                    EstadoChip(
                        "${formatoCantidad(item.stock)} ${item.insumo.unidadMedidaSimbolo}".trim(),
                        if (item.stock <= 0) EstiloChip.ERROR else EstiloChip.ADVERTENCIA,
                    )
                }
            }
            if (state.stockBajo.size > 5) {
                Text("y ${state.stockBajo.size - 5} más…", style = CuerpoStyle, color = RojoTexto, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** "12.5" / "3" con máximo 2 decimales. */
fun formatoCantidad(v: Double): String {
    val centesimos = kotlin.math.round(v * 100).toLong()
    val entero = centesimos / 100
    val resto = kotlin.math.abs(centesimos % 100)
    return when {
        resto == 0L -> entero.toString()
        resto % 10 == 0L -> "$entero.${resto / 10}"
        else -> "$entero.${resto.toString().padStart(2, '0')}"
    }
}
