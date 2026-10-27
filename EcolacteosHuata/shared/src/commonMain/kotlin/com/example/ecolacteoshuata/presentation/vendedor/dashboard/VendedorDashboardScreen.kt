package com.example.ecolacteoshuata.presentation.vendedor.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.AcentoCeleste
import com.example.ecolacteoshuata.core.theme.AcentoCoral
import com.example.ecolacteoshuata.core.theme.AcentoVerde
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.Rojo
import com.example.ecolacteoshuata.core.theme.RojoFondo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.ui.DividerLine
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.HeroMetricCard
import com.example.ecolacteoshuata.core.ui.KpiCard
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.util.formatDdMmmYyyy
import com.example.ecolacteoshuata.core.util.formatMoney
import com.example.ecolacteoshuata.presentation.common.ModuloItem
import com.example.ecolacteoshuata.presentation.common.ModulosGrid
import com.example.ecolacteoshuata.presentation.common.PanelScroll

private fun cantidad(valor: Double): String =
    if (valor % 1.0 == 0.0) valor.toLong().toString() else ((kotlin.math.round(valor * 100) / 100.0)).toString()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VendedorDashboardScreen(state: VendedorDashboardUiState, modulos: List<ModuloItem>) {
    PanelScroll {
        // 1. Ventas de hoy.
        HeroMetricCard(
            etiqueta = "Ventas de hoy",
            valor = formatMoney(state.totalHoy),
            detalle = if (state.ventasHoy == 0L) "Aún no hay ventas: ¡a vender!" else "${state.ventasHoy} boleta(s)",
            icono = Icons.Filled.Payments,
        )

        // 2. Los indicadores del panel de Laravel.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Producción de hoy", cantidad(state.produccionHoy), AcentoCeleste, Modifier.weight(1f), Icons.Filled.Factory)
            KpiCard("Valor del inventario", formatMoney(state.valorInventario), AcentoVerde, Modifier.weight(1f), Icons.Filled.Inventory2)
        }
        KpiCard(
            "Productos con stock bajo (≤ 5)",
            state.stockBajo.size.toString(),
            if (state.stockBajo.isNotEmpty()) AcentoCoral else AcentoVerde,
            Modifier.fillMaxWidth(),
            Icons.Filled.Warning,
        )

        // 3. Aviso de stock bajo.
        if (state.stockBajo.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(RojoFondo)
                    .border(1.dp, Rojo.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = RojoTexto, modifier = Modifier.size(18.dp))
                    Text("Stock bajo — reponer pronto", style = CuerpoMedioStyle, color = RojoTexto, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                }
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.stockBajo.forEach { EstadoChip("${it.nombre} · ${cantidad(it.disponible)}", EstiloChip.ERROR) }
                }
            }
        }

        // 4. Accesos.
        TituloSeccion("Accesos rápidos")
        ModulosGrid(modulos)

        // 5. Últimas ventas.
        TituloSeccion("Últimas ventas")
        SectionCard {
            if (state.ultimasVentas.isEmpty()) {
                Text("Todavía no hay ventas registradas.", style = CuerpoStyle, color = GrisTexto)
            }
            state.ultimasVentas.forEachIndexed { index, fila ->
                if (index > 0) DividerLine(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = VerdeOscuro, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(fila.venta.numeroBoleta, style = CuerpoMedioStyle, color = Negro)
                        Text("${fila.cliente} · ${fila.venta.fecha.formatDdMmmYyyy()}", style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp)
                    }
                    Text(formatMoney(fila.venta.total), style = CuerpoMedioStyle, color = VerdeOscuro, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
