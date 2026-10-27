package com.example.ecolacteoshuata.presentation.vendedor.historial

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.AcentoCeleste
import com.example.ecolacteoshuata.core.theme.AcentoVerde
import com.example.ecolacteoshuata.core.theme.Borde
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.Rojo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.BotonAccion
import com.example.ecolacteoshuata.core.ui.ConfirmToastHost
import com.example.ecolacteoshuata.core.ui.ConfirmarDialog
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.KpiCard
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.ui.fondoOpcion
import com.example.ecolacteoshuata.core.ui.textoOpcion
import com.example.ecolacteoshuata.core.util.formatDdMmmYyyyHm
import com.example.ecolacteoshuata.core.util.formatMoney
import com.example.ecolacteoshuata.domain.model.MetodoPago
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.datetime.plus

private const val MILIS_DIA = 86_400_000L
private val Epoca = LocalDate(1970, 1, 1)

private fun LocalDate.ddMmYyyy(): String = "${day.toString().padStart(2, '0')}/${month.number.toString().padStart(2, '0')}/$year"

fun etiquetaMetodo(metodo: MetodoPago) = when (metodo) {
    MetodoPago.EFECTIVO -> "Efectivo"
    MetodoPago.YAPE -> "Yape"
    MetodoPago.PLIN -> "Plin"
    MetodoPago.TARJETA -> "Tarjeta"
    MetodoPago.OTRO -> "Otro"
}

/** "Historial de ventas" de Laravel: consulta las boletas emitidas y descarga su PDF. */
@Composable
fun VendedorHistorialScreen(
    state: VendedorHistorialUiState,
    onDesde: (LocalDate) -> Unit,
    onHasta: (LocalDate) -> Unit,
    onMetodo: (MetodoPago?) -> Unit,
    onBusqueda: (String) -> Unit,
    onDescargarBoleta: (FilaVenta) -> Unit,
    onPedirLimpiar: () -> Unit,
    onCancelarLimpiar: () -> Unit,
    onLimpiar: () -> Unit,
    onConsumirMensaje: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
            item(key = "ayuda") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Consulta las boletas emitidas y descarga su PDF.", style = CuerpoStyle, color = GrisTexto, modifier = Modifier.weight(1f))
                    val habilitado = state.boletasPeriodo > 0
                    Row(
                        modifier = Modifier
                            .alpha(if (habilitado) 1f else 0.4f)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(enabled = habilitado, onClick = onPedirLimpiar)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = RojoTexto, modifier = Modifier.size(18.dp))
                        Text("Limpiar historial", style = CuerpoMedioStyle, color = RojoTexto, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            item(key = "filtros") {
                SectionCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CampoFecha("Desde", state.desde, onDesde, Modifier.weight(1f))
                        CampoFecha("Hasta", state.hasta, onHasta, Modifier.weight(1f))
                    }
                    Text("Método de pago", style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        (listOf<MetodoPago?>(null) + MetodoPago.entries).forEach { metodo ->
                            val activa = metodo == state.metodoPago
                            Text(
                                metodo?.let { etiquetaMetodo(it) } ?: "Todos",
                                style = CuerpoMedioStyle,
                                color = textoOpcion(activa),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(fondoOpcion(activa))
                                    .clickable { onMetodo(metodo) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                    AppTextField(state.busqueda, onBusqueda, "Buscar", placeholder = "Boleta o cliente...", modifier = Modifier.padding(top = 10.dp))
                }
            }
            item(key = "totales") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard("Total vendido en el período", formatMoney(state.totalPeriodo), AcentoVerde, Modifier.weight(1f), Icons.Filled.Payments)
                    KpiCard("Boletas emitidas", state.boletasPeriodo.toString(), AcentoCeleste, Modifier.weight(1f), Icons.AutoMirrored.Filled.ReceiptLong)
                }
            }
            if (state.ventas.isEmpty()) {
                item(key = "vacio") {
                    EmptyState("Sin ventas", "Sin ventas en este período.", icono = Icons.AutoMirrored.Filled.ReceiptLong)
                }
            }
            itemsIndexed(state.ventas, key = { _, f -> f.venta.id }) { index, fila ->
                val venta = fila.venta
                StaggeredListItem(index = index) {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(venta.numeroBoleta, style = CuerpoMedioStyle.copy(fontSize = 15.sp), color = Negro, fontWeight = FontWeight.SemiBold)
                                Text(venta.fecha.formatDdMmmYyyyHm(), style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp)
                            }
                            Text(formatMoney(venta.total), style = TituloPantallaStyle, color = VerdeOscuro)
                        }
                        Text(fila.cliente, style = CuerpoMedioStyle, color = Negro, modifier = Modifier.padding(top = 8.dp))
                        Text("Vendedor: ${fila.vendedor ?: "—"}", style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            EstadoChip(etiquetaMetodo(venta.metodoPago), EstiloChip.INFO)
                            Box(Modifier.weight(1f))
                            BotonAccion("Boleta PDF", Icons.Filled.PictureAsPdf, Rojo, onClick = { onDescargarBoleta(fila) })
                        }
                    }
                }
            }
        }
        ConfirmToastHost(mensaje = state.mensaje, onDismiss = onConsumirMensaje, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (state.confirmarLimpiar) {
        ConfirmarDialog(
            titulo = "¿Eliminar historial del período?",
            mensaje = "Esto eliminará permanentemente ${state.boletasPeriodo} boleta(s) del ${state.desde.ddMmYyyy()} al ${state.hasta.ddMmYyyy()} " +
                "(según los filtros actuales). Esta acción no se puede deshacer.",
            textoConfirmar = "Eliminar historial",
            onConfirmar = onLimpiar,
            onCancelar = onCancelarLimpiar,
        )
    }
}

/** Fecha de un filtro: al tocarla abre el calendario. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampoFecha(etiqueta: String, fecha: LocalDate, onFecha: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var abierto by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Borde.copy(alpha = 0.35f))
            .clickable { abierto = true }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(etiqueta, style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = VerdePrimario, modifier = Modifier.size(16.dp))
            Text(fecha.ddMmYyyy(), style = CuerpoMedioStyle, color = Negro, modifier = Modifier.padding(start = 6.dp))
        }
    }
    if (abierto) {
        val estado = rememberDatePickerState(initialSelectedDateMillis = fecha.toEpochDays() * MILIS_DIA)
        DatePickerDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { onFecha(Epoca.plus(DatePeriod(days = (it / MILIS_DIA).toInt()))) }
                    abierto = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } },
        ) { DatePicker(state = estado) }
    }
}
