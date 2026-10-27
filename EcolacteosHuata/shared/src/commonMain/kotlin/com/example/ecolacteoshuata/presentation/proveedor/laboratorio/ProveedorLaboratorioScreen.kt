package com.example.ecolacteoshuata.presentation.proveedor.laboratorio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.AcentoCoral
import com.example.ecolacteoshuata.core.theme.AcentoVerde
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.Rojo
import com.example.ecolacteoshuata.core.theme.VerdeClaro
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.ui.BurbujaIcono
import com.example.ecolacteoshuata.core.ui.DividerLine
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.KpiCard
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.domain.model.Periodo
import com.example.ecolacteoshuata.domain.model.ResultadoLaboratorio
import com.example.ecolacteoshuata.domain.model.ResultadoTipo
import com.example.ecolacteoshuata.presentation.proveedor.FiltroPeriodo
import com.example.ecolacteoshuata.presentation.proveedor.ddMmYyyy
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlinx.datetime.LocalDate

private fun etiquetaResultado(tipo: ResultadoTipo) = when (tipo) {
    ResultadoTipo.CONFORME -> "Conforme"
    ResultadoTipo.SOLEADO -> "Soleado"
    ResultadoTipo.ACIDIFICADO -> "Acidificado"
    ResultadoTipo.AGUADO -> "Aguado"
    ResultadoTipo.OTRO -> "Otro"
    ResultadoTipo.NO_CONFORME -> "No conforme"
}

/** Como `number_format` de Laravel: el número con [decimales] fijos, o "—" si no se midió. */
private fun Double?.conDecimales(decimales: Int): String {
    if (this == null) return "—"
    val factor = 10.0.pow(decimales)
    val redondeado = (abs(this) * factor).roundToLong()
    val entero = redondeado / factor.toLong()
    val fraccion = (redondeado % factor.toLong()).toString().padStart(decimales, '0')
    return "${if (this < 0) "-" else ""}$entero.$fraccion"
}

/** Hora y fecha que imprimió el analizador ("09:42 · 06/06/26"), como `horaFechaAnalisis()`. */
private fun ResultadoLaboratorio.horaFechaAnalisis(): String? =
    listOfNotNull(horaAnalisis?.take(5)?.ifBlank { null }, fechaAnalisis?.ifBlank { null }).takeIf { it.isNotEmpty() }?.joinToString(" · ")

@Composable
fun ProveedorLaboratorioScreen(
    state: ProveedorLaboratorioUiState,
    onPeriodo: (Periodo) -> Unit,
    onFecha: (LocalDate) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
        item(key = "ayuda") {
            Text("Calidad de la leche que has entregado.", style = CuerpoStyle, color = GrisTexto)
        }
        item(key = "filtro") { FiltroPeriodo(state.periodo, state.fecha, onPeriodo, onFecha) }
        if (state.resultados.isEmpty()) {
            item(key = "vacio") {
                EmptyState("Sin resultados", "No hay resultados de laboratorio en este período.", icono = Icons.Filled.Science)
            }
            return@LazyColumn
        }
        item(key = "resumen") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiCard("Conformes", state.conformes.toString(), AcentoVerde, Modifier.weight(1f), Icons.Filled.CheckCircle)
                KpiCard("No conformes", state.alteraciones.toString(), AcentoCoral, Modifier.weight(1f), Icons.Filled.Warning)
            }
        }
        itemsIndexed(state.resultados, key = { _, f -> f.resultado.id }) { index, fila ->
            StaggeredListItem(index = index) { TarjetaResultado(fila) }
        }
    }
}

@Composable
private fun TarjetaResultado(fila: FilaResultado) {
    val r = fila.resultado
    var abierto by rememberSaveable(r.id) { mutableStateOf(false) }
    SectionCard(modifier = if (r.conforme) Modifier else Modifier.border(1.5.dp, Rojo.copy(alpha = 0.45f), RoundedCornerShape(20.dp))) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BurbujaIcono(
                if (r.conforme) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                if (r.conforme) AcentoVerde else AcentoCoral,
                tamano = 36.dp,
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text("${r.fecha.ddMmYyyy()} · ${fila.turno ?: "Sin turno"}", style = CuerpoMedioStyle, color = Negro)
                Text("Resultado: ${etiquetaResultado(r.resultado)}", style = CuerpoStyle, color = GrisTexto)
            }
            EstadoChip(if (r.conforme) "Conforme" else "No conforme", if (r.conforme) EstiloChip.EXITO else EstiloChip.ERROR)
        }

        DividerLine(Modifier.padding(vertical = 10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Medida("Densidad", r.densidad.conDecimales(3), Modifier.weight(1f))
            Medida("Temperatura", if (r.temperatura != null) "${r.temperatura.conDecimales(2)} °C" else "—", Modifier.weight(1f))
        }
        Medida("Hora y fecha del análisis", r.horaFechaAnalisis() ?: "—", Modifier.padding(top = 8.dp))
        if (r.observaciones.isNotBlank()) {
            Text(r.observaciones, style = CuerpoStyle, color = GrisTexto, modifier = Modifier.padding(top = 8.dp))
        }

        // Las columnas de la tabla de Laravel, plegadas para que quepan en el teléfono.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).clickable { abierto = !abierto }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (abierto) "Ocultar parámetros" else "Ver todos los parámetros", style = CuerpoMedioStyle, color = VerdeOscuro, modifier = Modifier.weight(1f))
            Icon(if (abierto) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = VerdeOscuro, modifier = Modifier.size(20.dp))
        }
        AnimatedVisibility(visible = abierto) {
            val parametros = listOf(
                "Litros" to r.litros.conDecimales(2),
                "Temp." to r.temperatura.conDecimales(2),
                "Grasa" to r.grasa.conDecimales(2),
                "SNG" to r.sng.conDecimales(2),
                "Densidad" to r.densidad.conDecimales(3),
                "Proteína" to r.proteina.conDecimales(2),
                "Lactosa" to r.lactosa.conDecimales(2),
                "Sales" to r.sales.conDecimales(2),
                "Total sólidos" to r.totalSolidos.conDecimales(2),
                "Agua añadida" to r.aguaAnadida.conDecimales(2),
                "Punto cong." to r.puntoCongelacion.conDecimales(3),
                "pH" to r.ph.conDecimales(2),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                parametros.chunked(3).forEach { fila3 ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        fila3.forEach { (etiqueta, valor) -> Parametro(etiqueta, valor, Modifier.weight(1f)) }
                        repeat(3 - fila3.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Medida(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(etiqueta, style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp)
        Text(valor, style = CuerpoMedioStyle, color = Negro)
    }
}

@Composable
private fun Parametro(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(VerdeClaro.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(etiqueta, style = CuerpoStyle, color = GrisTexto, fontSize = 11.sp, maxLines = 1)
        Text(valor, style = CuerpoMedioStyle, color = if (valor == "—") GrisTexto else Negro, fontSize = 13.sp)
    }
}
