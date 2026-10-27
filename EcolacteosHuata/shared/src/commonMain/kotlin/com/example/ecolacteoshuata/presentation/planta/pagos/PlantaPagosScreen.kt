package com.example.ecolacteoshuata.presentation.planta.pagos

import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Agriculture
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.BotonAccion
import com.example.ecolacteoshuata.core.ui.FilaDato
import com.example.ecolacteoshuata.core.ui.TarjetaLista
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import com.example.ecolacteoshuata.core.theme.AcentoCoral
import com.example.ecolacteoshuata.core.theme.AcentoVerde
import com.example.ecolacteoshuata.core.ui.KpiCard
import com.example.ecolacteoshuata.core.ui.textoOpcion
import com.example.ecolacteoshuata.core.ui.fondoOpcion
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.EtiquetaMayusculaStyle
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdeClaro
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.util.formatMoney
import com.example.ecolacteoshuata.domain.model.MetodoPago
import com.example.ecolacteoshuata.domain.model.Periodo

private fun etiquetaPeriodo(periodo: Periodo) = when (periodo) {
    Periodo.TURNO -> "Turno"
    Periodo.DIA -> "Día"
    Periodo.SEMANA -> "Semana"
    Periodo.MES -> "Mes"
    Periodo.ANIO -> "Año"
}

private fun etiquetaMetodoPago(metodo: MetodoPago) = when (metodo) {
    MetodoPago.EFECTIVO -> "Efectivo"
    MetodoPago.YAPE -> "Yape"
    MetodoPago.PLIN -> "Plin"
    MetodoPago.TARJETA -> "Tarjeta"
    MetodoPago.OTRO -> "Otro"
}

@Composable
fun PlantaPagosScreen(
    state: PlantaPagosUiState,
    onPeriodoChange: (Periodo) -> Unit,
    onPagar: (FilaPago, MetodoPago) -> Unit,
) {
    var filaPagando by remember { mutableStateOf<FilaPago?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
        item {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Periodo.entries.forEach { periodo ->
                    val activo = periodo == state.periodo
                    Row(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .background(fondoOpcion(activo), RoundedCornerShape(999.dp))
                            .clickable { onPeriodoChange(periodo) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(etiquetaPeriodo(periodo), style = EtiquetaMayusculaStyle, color = textoOpcion(activo))
                    }
                }
            }
        }
        item {
            val totalPagado = state.filas.sumOf { it.resumen.totalPagado }
            val totalPendiente = state.filas.sumOf { it.resumen.totalPendiente }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiCard("Pagado", formatMoney(totalPagado), AcentoVerde, Modifier.weight(1f), Icons.Filled.CheckCircle)
                KpiCard("Por pagar", formatMoney(totalPendiente), AcentoCoral, Modifier.weight(1f), Icons.Filled.Schedule)
            }
        }
        if (state.filas.isEmpty()) {
            item { EmptyState("Sin datos", "No hay recojos para el periodo seleccionado.") }
        } else {
            items(state.filas) { fila ->
                val pendiente = fila.resumen.totalPendiente > 0
                TarjetaLista(
                    titulo = fila.proveedor.nombreCompleto,
                    icono = Icons.Filled.Agriculture,
                    derecha = { if (pendiente) EstadoChip("Pendiente", EstiloChip.ADVERTENCIA) else EstadoChip("Al día", EstiloChip.EXITO) },
                    acciones = if (pendiente) {
                        { BotonAccion("Pagar pendiente", Icons.Filled.Payments, VerdeOscuro, onClick = { filaPagando = fila }) }
                    } else {
                        null
                    },
                ) {
                    FilaDato("Pagado", formatMoney(fila.resumen.totalPagado), colorValor = VerdeOscuro)
                    FilaDato("Pendiente", formatMoney(fila.resumen.totalPendiente), colorValor = RojoTexto)
                }
            }
        }
    }

    val fila = filaPagando
    if (fila != null) {
        AppBottomSheet(onDismiss = { filaPagando = null }) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                TituloSeccion("Pagar a ${fila.proveedor.nombreCompleto}")
                Text("Monto: ${formatMoney(fila.resumen.totalPendiente)}", style = CuerpoMedioStyle, color = Negro, modifier = Modifier.padding(top = 4.dp))
                Text("Elige cómo se pagó:", style = CuerpoStyle, color = GrisTexto, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                MetodoPago.entries.forEach { metodo ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(VerdeClaro, RoundedCornerShape(12.dp))
                            .clickable {
                                onPagar(fila, metodo)
                                filaPagando = null
                            }
                            .padding(12.dp),
                    ) {
                        Text(etiquetaMetodoPago(metodo), style = CuerpoMedioStyle, color = VerdeOscuro)
                    }
                }
            }
        }
    }
}
