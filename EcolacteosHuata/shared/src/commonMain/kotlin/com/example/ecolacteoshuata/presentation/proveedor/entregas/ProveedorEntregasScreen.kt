package com.example.ecolacteoshuata.presentation.proveedor.entregas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.AcentoAmbar
import com.example.ecolacteoshuata.core.theme.AcentoCeleste
import com.example.ecolacteoshuata.core.theme.AcentoVerde
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.BotonAccion
import com.example.ecolacteoshuata.core.ui.DividerLine
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EncabezadoFormulario
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.FilaDato
import com.example.ecolacteoshuata.core.ui.KpiCard
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import com.example.ecolacteoshuata.core.ui.SecondaryButton
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.ui.TarjetaLista
import com.example.ecolacteoshuata.core.util.formatMoney
import com.example.ecolacteoshuata.core.util.litrosConDecimales
import com.example.ecolacteoshuata.domain.model.EstadoLeche
import com.example.ecolacteoshuata.domain.model.Periodo
import com.example.ecolacteoshuata.presentation.proveedor.FiltroPeriodo
import com.example.ecolacteoshuata.presentation.proveedor.ddMmYyyy
import kotlinx.datetime.LocalDate

@Composable
fun ProveedorEntregasScreen(
    state: ProveedorEntregasUiState,
    onPeriodo: (Periodo) -> Unit,
    onFecha: (LocalDate) -> Unit,
    onVerDetalle: (FilaEntrega) -> Unit,
    onCerrarDetalle: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
        item(key = "ayuda") {
            Text("Historial de leche entregada y estado de pago.", style = CuerpoStyle, color = GrisTexto)
        }
        item(key = "filtro") { FiltroPeriodo(state.periodo, state.fecha, onPeriodo, onFecha) }
        item(key = "resumen") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard("Litraje llegado", litrosConDecimales(state.totalLitros), AcentoCeleste, Modifier.weight(1f), Icons.Filled.LocalDrink)
                    KpiCard("Monto", formatMoney(state.totalMonto), AcentoVerde, Modifier.weight(1f), Icons.Filled.Payments)
                }
                KpiCard("Por cobrar", formatMoney(state.porCobrar), AcentoAmbar, Modifier.fillMaxWidth(), Icons.Filled.AccountBalanceWallet)
            }
        }
        if (state.entregas.isEmpty()) {
            item(key = "vacio") {
                EmptyState("Sin entregas", "No tienes entregas registradas en este período.", icono = Icons.Filled.LocalShipping)
            }
        } else {
            itemsIndexed(state.entregas, key = { _, f -> f.recojo.id }) { index, fila ->
                StaggeredListItem(index = index) {
                    TarjetaLista(
                        titulo = fila.recojo.fecha.ddMmYyyy(),
                        subtitulo = fila.turno ?: "Sin turno",
                        icono = Icons.Filled.LocalShipping,
                        onClick = { onVerDetalle(fila) },
                        derecha = { EstadoPago(fila.recojo.pagado) },
                        acciones = { BotonAccion("Entrar", Icons.Filled.Visibility, VerdeOscuro, onClick = { onVerDetalle(fila) }) },
                    ) {
                        FilaDato("Litros entregados", litrosConDecimales(fila.litrosEntregados), Modifier.padding(top = 6.dp))
                        FilaDato("Litraje llegado (a pagar)", litrosConDecimales(fila.recojo.litros))
                        FilaDato("Monto a pagar", formatMoney(fila.recojo.total), colorValor = VerdeOscuro)
                    }
                }
            }
        }
    }

    state.seleccionada?.let { DetalleEntrega(it, onCerrarDetalle) }
}

@Composable
private fun EstadoPago(pagado: Boolean) {
    EstadoChip(if (pagado) "Cobrado" else "Pendiente", if (pagado) EstiloChip.EXITO else EstiloChip.ADVERTENCIA)
}

private fun EstadoLeche.etiqueta(otro: String): String = when (this) {
    EstadoLeche.OTRO -> otro.ifBlank { "Otro" }
    else -> name.lowercase().replaceFirstChar { it.uppercase() }
}

/** El modal "Detalle de la entrega" de Laravel. */
@Composable
private fun DetalleEntrega(fila: FilaEntrega, onCerrar: () -> Unit) {
    val r = fila.recojo
    AppBottomSheet(onDismiss = onCerrar) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            EncabezadoFormulario("Detalle de la entrega")
            FilaDato("Fecha", r.fecha.ddMmYyyy(), Modifier.padding(top = 8.dp))
            FilaDato("Turno", fila.turno ?: "—")
            FilaDato("Litros entregados", litrosConDecimales(fila.litrosEntregados))
            FilaDato("Litraje llegado (a pagar)", litrosConDecimales(r.litros))
            FilaDato("Precio por litro", formatMoney(r.precioLitro))
            FilaDato("Estado de la leche", r.estadoLeche.etiqueta(r.estadoOtro))
            DividerLine(Modifier.padding(vertical = 10.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Monto a pagar", style = TituloPantallaStyle, color = Negro, modifier = Modifier.weight(1f))
                Text(formatMoney(r.total), style = TituloPantallaStyle, color = VerdeOscuro, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Estado de pago", style = CuerpoMedioStyle, color = GrisTexto, fontSize = 14.sp, modifier = Modifier.weight(1f))
                EstadoPago(r.pagado)
            }
            SecondaryButton("Cerrar", onClick = onCerrar, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        }
    }
}
