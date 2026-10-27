package com.example.ecolacteoshuata.presentation.admin.precios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.Borde
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.ConfirmToastHost
import com.example.ecolacteoshuata.core.ui.DividerLine
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.HeroMetricCard
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import com.example.ecolacteoshuata.core.ui.MensajeError
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.util.formatMoney
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.PrecioLeche
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.datetime.plus

private const val MILIS_DIA = 86_400_000L
private val Epoca = LocalDate(1970, 1, 1)

private fun LocalDate.ddMmYyyy(): String = "${day.toString().padStart(2, '0')}/${month.number.toString().padStart(2, '0')}/$year"

/** "Precios" de Laravel: precio de la leche vigente, registrar un nuevo precio y su historial. */
@Composable
fun AdminPreciosScreen(
    precioVigente: PrecioLeche?,
    historial: List<PrecioLeche>,
    form: PrecioForm,
    mensaje: String?,
    onPrecio: (String) -> Unit,
    onVigenteDesde: (LocalDate) -> Unit,
    onRegistrar: () -> Unit,
    onConsumirMensaje: () -> Unit,
) {
    val hoy = todayLocalDate()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
            item(key = "ayuda") { Text("Precio de la leche.", style = CuerpoStyle, color = GrisTexto) }

            // 1. Precio vigente.
            item(key = "vigente") {
                HeroMetricCard(
                    etiqueta = "Precio vigente",
                    valor = "${formatMoney(precioVigente?.precioLitro ?: 0.0)} / litro",
                    detalle = "Desde ${precioVigente?.vigenteDesde?.ddMmYyyy() ?: "—"}",
                    icono = Icons.Filled.LocalDrink,
                )
            }

            // 2. Registrar nuevo precio.
            item(key = "form") {
                SectionCard {
                    TituloSeccion("Registrar nuevo precio")
                    AppTextField(
                        form.precio, onPrecio, "Nuevo precio por litro (S/)",
                        placeholder = "1.80", teclado = KeyboardType.Decimal, modifier = Modifier.padding(top = 10.dp),
                    )
                    CampoVigenteDesde(form.vigenteDesde, onVigenteDesde, Modifier.padding(top = 10.dp))
                    if (form.vigenteDesde > hoy) {
                        Text(
                            "Este precio empezará a regir el ${form.vigenteDesde.ddMmYyyy()}; hasta entonces sigue el vigente.",
                            style = CuerpoStyle,
                            color = GrisTexto,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    form.error?.let { MensajeError(it, Modifier.padding(top = 10.dp)) }
                    PrimaryButton(
                        texto = "Registrar nuevo precio",
                        onClick = onRegistrar,
                        cargando = form.guardando,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
            }

            // 3. Historial (los 10 más recientes).
            if (historial.isNotEmpty()) {
                item(key = "historial-titulo") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.History, contentDescription = null, tint = VerdeOscuro, modifier = Modifier.size(18.dp))
                        Text("Historial", style = CuerpoMedioStyle, color = Negro, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                item(key = "historial") {
                    SectionCard {
                        historial.forEachIndexed { index, precio ->
                            if (index > 0) DividerLine(Modifier.padding(vertical = 8.dp))
                            FilaHistorial(precio, esVigente = precio.id == precioVigente?.id, esFuturo = precio.vigenteDesde > hoy)
                        }
                    }
                }
            }
        }
        ConfirmToastHost(mensaje = mensaje, onDismiss = onConsumirMensaje, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun FilaHistorial(precio: PrecioLeche, esVigente: Boolean, esFuturo: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(formatMoney(precio.precioLitro), style = CuerpoMedioStyle, color = Negro, fontWeight = FontWeight.SemiBold)
            Text("desde ${precio.vigenteDesde.ddMmYyyy()}", style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp)
        }
        when {
            esVigente -> EstadoChip("Vigente", EstiloChip.EXITO)
            esFuturo -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = GrisTexto, modifier = Modifier.size(14.dp).padding(end = 2.dp))
                EstadoChip("Próximo", EstiloChip.INFO)
            }
        }
    }
}

/** "Vigente desde": al tocarlo abre el calendario. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampoVigenteDesde(fecha: LocalDate, onFecha: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var abierto by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Borde.copy(alpha = 0.35f))
            .clickable { abierto = true }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("Vigente desde", style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = VerdePrimario, modifier = Modifier.size(18.dp))
            Text(fecha.ddMmYyyy(), style = CuerpoMedioStyle, color = Negro, modifier = Modifier.padding(start = 8.dp))
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
