package com.example.ecolacteoshuata.presentation.planta.dashboard

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
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalShipping
import com.example.ecolacteoshuata.core.util.formatLitros

@Composable
fun PlantaDashboardScreen(state: PlantaDashboardUiState, modulos: List<ModuloItem>) {
    val d = state.dashboard
    PanelScroll {
        HeroMetricCard(
            etiqueta = "Leche recibida hoy",
            valor = d?.let { formatLitros(it.litrosRecibidosHoy) } ?: "—",
            detalle = d?.let { "De ${it.cantidadAcopiadores} acopiador(es) y ${it.cantidadProveedores} proveedores" },
            icono = Icons.Filled.LocalDrink,
        )

        TituloSeccion("Accesos rápidos")
        ModulosGrid(modulos)

        TituloSeccion("Resumen de hoy")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Acopiadores", d?.cantidadAcopiadores?.toString() ?: "—", AcentoIndigo, Modifier.weight(1f), Icons.Filled.LocalShipping)
            KpiCard("Proveedores", d?.cantidadProveedores?.toString() ?: "—", AcentoVerde, Modifier.weight(1f), Icons.Filled.Agriculture)
        }
    }
}
