package com.example.ecolacteoshuata.presentation.proveedor.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.AcentoAmbar
import com.example.ecolacteoshuata.core.theme.AcentoCeleste
import com.example.ecolacteoshuata.core.theme.AcentoVerde
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.Rojo
import com.example.ecolacteoshuata.core.theme.RojoFondo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.BotonChico
import com.example.ecolacteoshuata.core.ui.BurbujaIcono
import com.example.ecolacteoshuata.core.ui.DividerLine
import com.example.ecolacteoshuata.core.ui.HeroMetricCard
import com.example.ecolacteoshuata.core.ui.KpiCard
import com.example.ecolacteoshuata.core.ui.LocalTemaRol
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.util.formatMoney
import com.example.ecolacteoshuata.core.util.litrosConDecimales
import com.example.ecolacteoshuata.presentation.common.ModuloItem
import com.example.ecolacteoshuata.presentation.common.ModulosGrid
import com.example.ecolacteoshuata.presentation.common.PanelScroll
import com.example.ecolacteoshuata.presentation.common.UbicacionFincaSeccion

@Composable
fun ProveedorDashboardScreen(state: ProveedorDashboardUiState, modulos: List<ModuloItem>) {
    val d = state.dashboard
    val proveedor = state.proveedor
    var editandoUbicacion by remember { mutableStateOf(false) }

    PanelScroll {
        // 0. Como en Laravel: si está bloqueado, el acopiador no recoge su leche.
        if (proveedor?.bloqueado == true) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(RojoFondo)
                    .border(1.dp, Rojo.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = RojoTexto, modifier = Modifier.size(22.dp))
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text("Tu leche no está siendo recogida", style = CuerpoMedioStyle, color = RojoTexto, fontWeight = FontWeight.Bold)
                    Text(
                        "Se detectaron ${proveedor.alteraciones} alteraciones de calidad en tu leche. Comunícate con planta para resolverlo.",
                        style = CuerpoStyle,
                        color = RojoTexto,
                    )
                }
            }
        }

        // 1. Lo entregado hoy.
        HeroMetricCard(
            etiqueta = "Entregaste hoy",
            valor = d?.let { litrosConDecimales(it.entregadoHoyLitros) } ?: "—",
            detalle = d?.let { if (it.entregadoHoyLitros > 0) "¡Gracias por tu entrega!" else "Aún no registras entrega hoy" },
            icono = Icons.Filled.LocalDrink,
        )

        // 2. Acopiador asignado y ubicación de la finca (tarjetas del panel de Laravel).
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BurbujaIcono(Icons.Filled.LocalShipping, LocalTemaRol.current.acento)
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Acopiador asignado", style = CuerpoMedioStyle, color = GrisTexto)
                    Text(d?.acopiadorNombre?.ifBlank { null } ?: "Sin asignar", style = TituloPantallaStyle, color = VerdeOscuro)
                    d?.acopiadorRuta?.takeIf { it.isNotBlank() && proveedor?.acopiadorId != null }?.let {
                        Text(it, style = CuerpoStyle, color = GrisTexto)
                    }
                }
            }
            if (proveedor?.acopiadorId != null && d != null) {
                DividerLine(Modifier.padding(vertical = 10.dp))
                Text("Próximo recojo: ${d.proximoTurno}", style = CuerpoStyle, color = GrisTexto)
            }
        }
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BurbujaIcono(Icons.Filled.Place, AcentoVerde)
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Ubicación de tu finca", style = CuerpoMedioStyle, color = GrisTexto)
                    Text(proveedor?.fincaUbicacion?.ifBlank { null } ?: "Sin registrar", style = TituloPantallaStyle, color = VerdeOscuro)
                    proveedor?.fincaReferencia?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = CuerpoStyle, color = GrisTexto)
                    }
                }
            }
            if (proveedor != null) {
                BotonChico(
                    "Editar ubicación",
                    onClick = { editandoUbicacion = true },
                    icono = Icons.Filled.EditLocationAlt,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        // 3. Lo entregado en la semana y el mes, y lo que falta cobrar.
        TituloSeccion("Resumen")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Esta semana", d?.let { litrosConDecimales(it.entregadoSemanaLitros) } ?: "—", AcentoCeleste, Modifier.weight(1f), Icons.Filled.DateRange)
            KpiCard("Este mes", d?.let { litrosConDecimales(it.entregadoMesLitros) } ?: "—", AcentoVerde, Modifier.weight(1f), Icons.Filled.CalendarMonth)
        }
        KpiCard("Por cobrar", d?.let { formatMoney(it.porCobrar) } ?: "—", AcentoAmbar, Modifier.fillMaxWidth(), Icons.Filled.AccountBalanceWallet)

        // 4. Accesos.
        TituloSeccion("Accesos rápidos")
        ModulosGrid(modulos)
    }

    if (editandoUbicacion && proveedor != null) {
        AppBottomSheet(onDismiss = { editandoUbicacion = false }) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    "Al guardar, quedarás en la ruta del acopiador que elijas.",
                    style = CuerpoStyle,
                    color = Negro,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                UbicacionFincaSeccion(proveedor.usuarioId)
            }
        }
    }
}
