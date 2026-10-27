package com.example.ecolacteoshuata.presentation.proveedor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.fondoOpcion
import com.example.ecolacteoshuata.core.ui.textoOpcion
import com.example.ecolacteoshuata.core.util.endOfMonth
import com.example.ecolacteoshuata.core.util.endOfWeek
import com.example.ecolacteoshuata.core.util.endOfYear
import com.example.ecolacteoshuata.core.util.plusDias
import com.example.ecolacteoshuata.core.util.startOfMonth
import com.example.ecolacteoshuata.core.util.startOfWeek
import com.example.ecolacteoshuata.core.util.startOfYear
import com.example.ecolacteoshuata.domain.model.Periodo
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.datetime.plus

private const val MILIS_DIA = 86_400_000L
private val Epoca = LocalDate(1970, 1, 1)

val Periodo.etiqueta: String
    get() = when (this) {
        Periodo.TURNO -> "Turno"
        Periodo.DIA -> "Día"
        Periodo.SEMANA -> "Semana"
        Periodo.MES -> "Mes"
        Periodo.ANIO -> "Año"
    }

/** `Periodo::rangoParaFecha` de Laravel: turno y día son el mismo día. */
fun rangoPeriodo(periodo: Periodo, fecha: LocalDate): Pair<LocalDate, LocalDate> = when (periodo) {
    Periodo.TURNO, Periodo.DIA -> fecha to fecha
    Periodo.SEMANA -> fecha.startOfWeek() to fecha.endOfWeek()
    Periodo.MES -> fecha.startOfMonth() to fecha.endOfMonth()
    Periodo.ANIO -> fecha.startOfYear() to fecha.endOfYear()
}

/** El mismo período, uno antes o después (flechas junto a la fecha). */
fun moverPeriodo(periodo: Periodo, fecha: LocalDate, pasos: Int): LocalDate = when (periodo) {
    Periodo.TURNO, Periodo.DIA -> fecha.plusDias(pasos)
    Periodo.SEMANA -> fecha.plusDias(7 * pasos)
    Periodo.MES -> fecha.plus(DatePeriod(months = pasos))
    Periodo.ANIO -> fecha.plus(DatePeriod(years = pasos))
}

fun LocalDate.ddMmYyyy(): String = "${day.toString().padStart(2, '0')}/${month.number.toString().padStart(2, '0')}/$year"

/** "Período" y "Fecha de referencia" de Laravel, en una tarjeta para el teléfono. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltroPeriodo(
    periodo: Periodo,
    fecha: LocalDate,
    onPeriodo: (Periodo) -> Unit,
    onFecha: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var calendarioAbierto by remember { mutableStateOf(false) }
    val (desde, hasta) = rangoPeriodo(periodo, fecha)

    SectionCard(modifier = modifier) {
        Text("Período", style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Periodo.entries.forEach { p ->
                val activa = p == periodo
                Text(
                    p.etiqueta,
                    style = CuerpoMedioStyle,
                    color = textoOpcion(activa),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(fondoOpcion(activa))
                        .clickable { onPeriodo(p) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onFecha(moverPeriodo(periodo, fecha, -1)) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Anterior", tint = VerdeOscuro)
            }
            Column(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { calendarioAbierto = true }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = VerdePrimario, modifier = Modifier.size(16.dp))
                    Text(fecha.ddMmYyyy(), style = CuerpoMedioStyle, color = Negro, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                }
                Text(
                    if (desde == hasta) "Fecha de referencia" else "Del ${desde.ddMmYyyy()} al ${hasta.ddMmYyyy()}",
                    style = CuerpoStyle,
                    color = GrisTexto,
                    fontSize = 12.sp,
                )
            }
            IconButton(onClick = { onFecha(moverPeriodo(periodo, fecha, 1)) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Siguiente", tint = VerdeOscuro)
            }
        }
    }

    if (calendarioAbierto) {
        val estado = rememberDatePickerState(initialSelectedDateMillis = fecha.toEpochDays() * MILIS_DIA)
        DatePickerDialog(
            onDismissRequest = { calendarioAbierto = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { onFecha(Epoca.plus(DatePeriod(days = (it / MILIS_DIA).toInt()))) }
                    calendarioAbierto = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { calendarioAbierto = false }) { Text("Cancelar") } },
        ) { DatePicker(state = estado) }
    }
}
