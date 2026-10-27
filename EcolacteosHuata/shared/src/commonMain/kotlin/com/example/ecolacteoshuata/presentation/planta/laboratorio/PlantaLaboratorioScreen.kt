package com.example.ecolacteoshuata.presentation.planta.laboratorio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.AcentoCeleste
import com.example.ecolacteoshuata.core.theme.AcentoCoral
import com.example.ecolacteoshuata.core.theme.AcentoVerde
import com.example.ecolacteoshuata.core.theme.CelesteTexto
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.Rojo
import com.example.ecolacteoshuata.core.theme.VerdeClaro
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.BotonAccion
import com.example.ecolacteoshuata.core.ui.BotonAgregar
import com.example.ecolacteoshuata.core.ui.BotonChico
import com.example.ecolacteoshuata.core.ui.ConfirmToastHost
import com.example.ecolacteoshuata.core.ui.ConfirmarDialog
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EncabezadoFormulario
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.KpiCard
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import com.example.ecolacteoshuata.core.ui.SecondaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.ui.TarjetaLista
import com.example.ecolacteoshuata.core.util.litrosConDecimales
import com.example.ecolacteoshuata.domain.model.Proveedor
import com.example.ecolacteoshuata.domain.model.ResultadoLaboratorio
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlin.math.abs
import kotlin.math.round

private fun LocalDate.ddMmYyyy(): String = "${day.toString().padStart(2, '0')}/${month.number.toString().padStart(2, '0')}/$year"

/** Como `number_format`: el número con [decimales] fijos, o "—" si no se midió. */
private fun Double?.fijo(decimales: Int = 2): String {
    if (this == null) return "—"
    var factor = 1L
    repeat(decimales) { factor *= 10 }
    val r = round(abs(this) * factor).toLong()
    return "${if (this < 0) "-" else ""}${r / factor}.${(r % factor).toString().padStart(decimales, '0')}"
}

/** "Laboratorio" de planta en Laravel: los últimos resultados con sus acciones. */
@Composable
fun PlantaLaboratorioScreen(
    state: PlantaLaboratorioUiState,
    onNuevo: () -> Unit,
    onCerrarEleccion: () -> Unit,
    onElegirProveedor: (Proveedor) -> Unit,
    onVerDetalle: (FilaResultadoLab) -> Unit,
    onCerrarDetalle: () -> Unit,
    onEditar: (FilaResultadoLab) -> Unit,
    onPdf: (FilaResultadoLab) -> Unit,
    onPedirEnviar: (FilaResultadoLab) -> Unit,
    onCancelarEnviar: () -> Unit,
    onEnviar: () -> Unit,
    onDesbloquear: (Proveedor) -> Unit,
    onConsumirMensaje: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
            item(key = "ayuda") {
                Text("Registra resultados de calidad de leche por proveedor.", style = CuerpoStyle, color = GrisTexto)
            }
            item(key = "resumen") {
                val conformes = state.resultados.count { it.resultado.conforme }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard("Conformes", conformes.toString(), AcentoVerde, Modifier.weight(1f), Icons.Filled.CheckCircle)
                    KpiCard("No conformes", (state.resultados.size - conformes).toString(), AcentoCoral, Modifier.weight(1f), Icons.Filled.Warning)
                    KpiCard("A producción", state.resultados.count { it.resultado.enviadoProduccion }.toString(), AcentoCeleste, Modifier.weight(1f), Icons.Filled.Factory)
                }
            }
            if (state.resultados.isEmpty()) {
                item(key = "vacio") {
                    EmptyState("Sin resultados", "Sin resultados registrados. Presiona \"Nuevo resultado\".", icono = Icons.Filled.Science)
                }
            }
            itemsIndexed(state.resultados, key = { _, f -> f.resultado.id }) { index, fila ->
                StaggeredListItem(index = index) {
                    TarjetaResultado(fila, onVerDetalle, onEditar, onPdf, onPedirEnviar, onDesbloquear)
                }
            }
        }
        BotonAgregar(texto = "Nuevo resultado", onClick = onNuevo, modifier = Modifier.align(Alignment.BottomEnd))
        ConfirmToastHost(mensaje = state.mensaje, onDismiss = onConsumirMensaje, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (state.eligiendoProveedor) {
        ElegirProveedorSheet(state.proveedoresEntregados, onElegirProveedor, onCerrarEleccion)
    }
    state.detalle?.let { DetalleSheet(it, onCerrarDetalle) }
    state.confirmarEnviar?.let { fila ->
        ConfirmarDialog(
            titulo = "¿Enviar esta leche a producción?",
            mensaje = "Se sumarán ${litrosConDecimales(fila.resultado.litros ?: 0.0)} de ${fila.nombreProveedor} al stock de insumos disponible para producir.",
            textoConfirmar = "Enviar",
            onConfirmar = onEnviar,
            onCancelar = onCancelarEnviar,
            colorConfirmar = VerdeOscuro,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaResultado(
    fila: FilaResultadoLab,
    onVerDetalle: (FilaResultadoLab) -> Unit,
    onEditar: (FilaResultadoLab) -> Unit,
    onPdf: (FilaResultadoLab) -> Unit,
    onPedirEnviar: (FilaResultadoLab) -> Unit,
    onDesbloquear: (Proveedor) -> Unit,
) {
    val r = fila.resultado
    val proveedor = fila.proveedor
    TarjetaLista(
        titulo = fila.nombreProveedor,
        subtitulo = listOfNotNull(r.fecha.ddMmYyyy(), fila.turno, r.litros?.let { litrosConDecimales(it) }).joinToString(" · "),
        icono = Icons.Filled.Agriculture,
        colorIcono = if (r.conforme) AcentoVerde else AcentoCoral,
        onClick = { onVerDetalle(fila) },
        derecha = { EstadoChip(if (r.conforme) "Conforme" else "No conforme", if (r.conforme) EstiloChip.EXITO else EstiloChip.ERROR) },
        acciones = {
            BotonAccion("Detalle", Icons.Filled.TableChart, GrisTexto, onClick = { onVerDetalle(fila) })
            BotonAccion("Editar", Icons.Filled.Edit, VerdeOscuro, onClick = { onEditar(fila) })
            BotonAccion("PDF", Icons.Filled.PictureAsPdf, Rojo, onClick = { onPdf(fila) })
        },
    ) {
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                proveedor?.bloqueado == true -> EstadoChip("Bloqueado (${proveedor.alteraciones}/${Proveedor.MAX_ALTERACIONES})", EstiloChip.ERROR)
                (proveedor?.alteraciones ?: 0) > 0 -> EstadoChip("En observación (${proveedor?.alteraciones}/${Proveedor.MAX_ALTERACIONES})", EstiloChip.ADVERTENCIA)
            }
            if (r.enviadoProduccion) EstadoChip("Enviado a producción", EstiloChip.INFO)
        }
        if (proveedor?.bloqueado == true) {
            BotonAccion("Desbloquear", Icons.Filled.LockOpen, CelesteTexto, onClick = { onDesbloquear(proveedor) }, modifier = Modifier.padding(top = 8.dp))
        }
        if (fila.puedeEnviarProduccion) {
            BotonChico("Enviar a producción", onClick = { onPedirEnviar(fila) }, icono = Icons.Filled.Upload, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun ElegirProveedorSheet(proveedores: List<Proveedor>, onElegir: (Proveedor) -> Unit, onCerrar: () -> Unit) {
    AppBottomSheet(onDismiss = onCerrar) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            EncabezadoFormulario("Nuevo resultado", "Elige al proveedor. Solo aparecen los que ya tienen leche entregada a planta.")
            if (proveedores.isEmpty()) {
                Text(
                    "Ningún proveedor tiene una entrega a planta todavía.",
                    style = CuerpoStyle,
                    color = GrisTexto,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            proveedores.forEach { p ->
                TarjetaLista(
                    titulo = p.nombreCompleto,
                    subtitulo = p.fincaUbicacion.ifBlank { "Sin ubicación" },
                    icono = Icons.Filled.Agriculture,
                    onClick = { onElegir(p) },
                    derecha = when {
                        p.bloqueado -> { { EstadoChip("Bloqueado", EstiloChip.ERROR) } }
                        p.alteraciones > 0 -> { { EstadoChip("${p.alteraciones}/${Proveedor.MAX_ALTERACIONES}", EstiloChip.ADVERTENCIA) } }
                        else -> null
                    },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            SecondaryButton("Cancelar", onClick = onCerrar, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        }
    }
}

/** El modal de detalle de Laravel: todos los valores del ticket del analizador. */
@Composable
private fun DetalleSheet(fila: FilaResultadoLab, onCerrar: () -> Unit) {
    val r: ResultadoLaboratorio = fila.resultado
    val datos = listOf(
        "SN" to (r.sn ?: "—"),
        "Mode" to (r.modo ?: "—"),
        "Temp. (°C)" to r.temperatura.fijo(),
        "Grasa (%)" to r.grasa.fijo(),
        "SNG (%)" to r.sng.fijo(),
        "Densidad" to r.densidad.fijo(3),
        "Proteína (%)" to r.proteina.fijo(),
        "Lactosa (%)" to r.lactosa.fijo(),
        "Sales (%)" to r.sales.fijo(),
        "Total sólidos (%)" to r.totalSolidos.fijo(),
        "Agua añadida (%)" to r.aguaAnadida.fijo(),
        "Punto cong. (°C)" to r.puntoCongelacion.fijo(3),
        "pH" to r.ph.fijo(),
        "Hora" to (r.horaAnalisis?.take(5) ?: "—"),
        "Fecha" to (r.fechaAnalisis ?: "—"),
    )
    AppBottomSheet(onDismiss = onCerrar) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            EncabezadoFormulario(
                fila.nombreProveedor,
                listOfNotNull(r.fecha.ddMmYyyy(), fila.turno, "${r.litros.fijo()} L").joinToString(" · "),
            )
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EstadoChip(if (r.conforme) "Conforme" else "No conforme", if (r.conforme) EstiloChip.EXITO else EstiloChip.ERROR)
                if (r.enviadoProduccion) EstadoChip("Enviado a producción", EstiloChip.INFO)
            }
            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                datos.chunked(3).forEach { fila3 ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fila3.forEach { (etiqueta, valor) ->
                            Column(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(VerdeClaro.copy(alpha = 0.7f)).padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text(etiqueta, style = CuerpoStyle, color = GrisTexto, fontSize = 11.sp, maxLines = 1)
                                Text(valor, style = CuerpoMedioStyle, color = if (valor == "—") GrisTexto else Negro, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        repeat(3 - fila3.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (r.observaciones.isNotBlank()) {
                SectionCard(modifier = Modifier.padding(top = 12.dp)) {
                    Text("Observaciones", style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp)
                    Text(r.observaciones, style = CuerpoStyle, color = Negro)
                }
            }
            SecondaryButton("Cerrar", onClick = onCerrar, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        }
    }
}
