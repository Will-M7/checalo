package com.example.ecolacteoshuata.presentation.acopiador.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.AcentoAmbar
import com.example.ecolacteoshuata.core.theme.AcentoCeleste
import com.example.ecolacteoshuata.core.theme.AcentoCoral
import com.example.ecolacteoshuata.core.theme.AcentoVerde
import com.example.ecolacteoshuata.core.ui.HeroMetricCard
import com.example.ecolacteoshuata.core.ui.KpiCard
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.util.formatMoney
import com.example.ecolacteoshuata.core.util.litrosConDecimales
import com.example.ecolacteoshuata.presentation.common.ModuloItem
import com.example.ecolacteoshuata.presentation.common.ModulosGrid
import com.example.ecolacteoshuata.presentation.common.PanelScroll

@Composable
fun AcopiadorDashboardScreen(state: AcopiadorDashboardUiState, modulos: List<ModuloItem>) {
    val d = state.dashboard
    val recogidos = d?.let { it.proveedoresAsignados - it.proveedoresFaltantes } ?: 0
    PanelScroll {
        // 1. Lo más importante del día: litros y avance de la ruta.
        HeroMetricCard(
            etiqueta = "Leche recogida hoy",
            valor = d?.let { litrosConDecimales(it.litrosRecogidosHoy) } ?: "—",
            detalle = d?.let {
                if (it.proveedoresFaltantes == 0) "¡Ruta completa! Lleva la leche a planta"
                else "Te faltan ${it.proveedoresFaltantes} de ${it.proveedoresAsignados} paradas"
            },
            icono = Icons.Filled.LocalDrink,
            progreso = d?.takeIf { it.proveedoresAsignados > 0 }?.let { recogidos.toFloat() / it.proveedoresAsignados },
            textoProgreso = "RUTA",
        )

        // 2. Accesos a las tareas del día, en el orden en que se hacen.
        TituloSeccion("Accesos rápidos")
        ModulosGrid(modulos)

        // 3. Resumen en cuadrícula 2 x 2.
        TituloSeccion("Resumen de hoy")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Proveedores", d?.proveedoresAsignados?.toString() ?: "—", AcentoVerde, Modifier.weight(1f), Icons.Filled.Agriculture)
            KpiCard("Recogidos", d?.let { recogidos.toString() } ?: "—", AcentoCeleste, Modifier.weight(1f), Icons.Filled.CheckCircle)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Faltan", d?.proveedoresFaltantes?.toString() ?: "—", AcentoAmbar, Modifier.weight(1f), Icons.Filled.PendingActions)
            KpiCard("Deuda", d?.let { formatMoney(it.deudaTotal) } ?: "—", AcentoCoral, Modifier.weight(1f), Icons.Filled.MoneyOff)
        }
    }
}
