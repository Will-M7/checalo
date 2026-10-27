package com.example.ecolacteoshuata.presentation.admin.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ecolacteoshuata.core.theme.AcentoAmbar
import com.example.ecolacteoshuata.core.theme.AcentoCeleste
import com.example.ecolacteoshuata.core.theme.AcentoCoral
import com.example.ecolacteoshuata.core.theme.AcentoIndigo
import com.example.ecolacteoshuata.core.theme.AcentoVerde
import com.example.ecolacteoshuata.core.ui.HeroMetricCard
import com.example.ecolacteoshuata.core.ui.KpiCard
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.presentation.common.ModuloItem
import com.example.ecolacteoshuata.presentation.common.ModulosGrid
import com.example.ecolacteoshuata.presentation.common.PanelScroll
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import com.example.ecolacteoshuata.core.util.formatLitros
import com.example.ecolacteoshuata.core.util.formatMoney

@Composable
fun AdminDashboardScreen(state: AdminDashboardUiState, modulos: List<ModuloItem>) {
    val d = state.dashboard
    PanelScroll {
        HeroMetricCard(
            etiqueta = "Leche acopiada hoy",
            valor = d?.let { formatLitros(it.litrosHoy) } ?: "—",
            detalle = d?.let { "${it.ventasHoyCantidad} ventas · ${formatMoney(it.ventasHoyTotal)}" },
            icono = Icons.Filled.LocalDrink,
            progreso = d?.takeIf { it.totalUsuarios > 0 }?.let { it.usuariosActivos.toFloat() / it.totalUsuarios },
            textoProgreso = "ACTIVOS",
        )

        TituloSeccion("Accesos rápidos")
        ModulosGrid(modulos)

        TituloSeccion("Resumen de hoy")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Usuarios", d?.totalUsuarios?.toString() ?: "—", AcentoIndigo, Modifier.weight(1f), Icons.Filled.Groups)
            KpiCard("Activos", d?.usuariosActivos?.toString() ?: "—", AcentoVerde, Modifier.weight(1f), Icons.Filled.PersonPin)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Ventas", d?.ventasHoyCantidad?.toString() ?: "—", AcentoAmbar, Modifier.weight(1f), Icons.Filled.Receipt)
            KpiCard("Vendido", d?.let { formatMoney(it.ventasHoyTotal) } ?: "—", AcentoCeleste, Modifier.weight(1f), Icons.Filled.PointOfSale)
        }
    }
}
