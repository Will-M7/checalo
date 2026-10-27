package com.example.ecolacteoshuata.presentation.produccion.compras

import com.example.ecolacteoshuata.core.ui.MensajeError
import com.example.ecolacteoshuata.core.ui.BotonesFormulario
import com.example.ecolacteoshuata.core.ui.EncabezadoFormulario
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.ui.BotonAgregar
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.RojoFondo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.AppDropdown
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.util.formatMoney
import com.example.ecolacteoshuata.domain.model.Insumo
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.AcentoCeleste
import com.example.ecolacteoshuata.core.theme.AcentoVerde
import com.example.ecolacteoshuata.core.theme.EtiquetaMayusculaStyle
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.KpiCard
import com.example.ecolacteoshuata.core.ui.fondoOpcion
import com.example.ecolacteoshuata.core.ui.textoOpcion
import com.example.ecolacteoshuata.core.util.formatDdMmmYyyy

/** Precio unitario con 4 decimales, como Laravel ("S/ 8.5000"). */
private fun precio4(valor: Double): String {
    val negativo = valor < 0
    val diezmilesimos = kotlin.math.round(kotlin.math.abs(valor) * 10_000).toLong()
    val texto = "${diezmilesimos / 10_000}.${(diezmilesimos % 10_000).toString().padStart(4, '0')}"
    return (if (negativo) "-S/ " else "S/ ") + texto
}

private fun decimal(valor: Double, decimales: Int): String {
    val factor = if (decimales == 1) 10 else 100
    val entero = kotlin.math.round(kotlin.math.abs(valor) * factor).toLong()
    return (if (valor < 0) "-" else "") + "${entero / factor}.${(entero % factor).toString().padStart(decimales, '0')}"
}

/** Opción del filtro de insumo: [id] null = "Todos los insumos". */
private data class OpcionInsumo(val id: String?, val nombre: String)

@Composable
fun ProduccionComprasScreen(
    historial: HistorialComprasState,
    insumos: List<Insumo>,
    formVisible: Boolean,
    error: String?,
    onSeleccionarPeriodo: (PeriodoCompras) -> Unit,
    onSeleccionarInsumo: (String?) -> Unit,
    onAbrirNuevo: () -> Unit,
    onCerrarForm: () -> Unit,
    onRegistrar: (insumoId: String?, cantidad: Double?, costoUnitario: Double?) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
            // 1. Atajos de fecha (Hoy, Ayer, Esta semana, Semana pasada, Este mes, Mes pasado).
            item {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    PeriodoCompras.entries.forEach { periodo ->
                        val activo = periodo == historial.periodo
                        Text(
                            periodo.etiqueta,
                            style = EtiquetaMayusculaStyle,
                            color = textoOpcion(activo),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(fondoOpcion(activo))
                                .clickable { onSeleccionarPeriodo(periodo) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // 2. Rango y filtro por insumo.
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DateRange, contentDescription = null, tint = GrisTexto, modifier = Modifier.size(18.dp))
                        Text(
                            "${historial.desde.formatDdMmmYyyy()} — ${historial.hasta.formatDdMmmYyyy()}",
                            style = CuerpoMedioStyle,
                            color = Negro,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    val opciones = listOf(OpcionInsumo(null, "Todos los insumos")) + insumos.map { OpcionInsumo(it.id, it.nombre) }
                    AppDropdown(
                        seleccionado = opciones.firstOrNull { it.id == historial.insumoId },
                        opciones = opciones,
                        etiqueta = "Insumo",
                        textoOpcion = { it.nombre },
                        onSeleccionar = { onSeleccionarInsumo(it.id) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }

            // 3. Totales del período.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard("Total gastado", formatMoney(historial.totalPeriodo), AcentoVerde, Modifier.weight(1f), Icons.Filled.Payments)
                    KpiCard("Compras registradas", historial.ingresos.size.toString(), AcentoCeleste, Modifier.weight(1f), Icons.Filled.ShoppingCart)
                }
            }

            // 4. Historial: cuándo, cuánto y a qué precio, con la variación contra la compra anterior.
            item { TituloSeccion("Ingresos de stock") }
            if (historial.ingresos.isEmpty()) {
                item { EmptyState("Sin compras", "No hay compras registradas en este período.", icono = Icons.Filled.ShoppingCart) }
            }
            itemsIndexed(historial.ingresos, key = { _, it -> it.detalle.id }) { index, ingreso ->
                StaggeredListItem(index = index) {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ingreso.detalle.insumoNombre, style = CuerpoMedioStyle.copy(fontSize = 15.sp), color = Negro)
                                Text(ingreso.fecha.formatDdMmmYyyy(), style = CuerpoStyle, color = GrisTexto)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Subtotal", style = CuerpoStyle, color = GrisTexto)
                                Text(formatMoney(ingreso.detalle.subtotal), style = TituloPantallaStyle, color = VerdeOscuro)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Cantidad ingresada", style = CuerpoStyle, color = GrisTexto)
                                Text("${decimal(ingreso.detalle.cantidad, 2)} ${ingreso.simbolo}".trim(), style = CuerpoMedioStyle, color = Negro)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Precio unitario", style = CuerpoStyle, color = GrisTexto)
                                Text(precio4(ingreso.detalle.costoUnitario), style = CuerpoMedioStyle, color = Negro)
                            }
                        }
                        val diferencia = ingreso.diferencia
                        val porcentaje = ingreso.porcentaje?.let { " (${decimal(it, 1)}%)" }.orEmpty()
                        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Vs. compra anterior: ", style = CuerpoStyle, color = GrisTexto)
                            when {
                                diferencia == null -> EstadoChip("Primera compra registrada", EstiloChip.INFO)
                                diferencia > 0 -> EstadoChip("↑ +${precio4(diferencia)}$porcentaje", EstiloChip.ERROR)
                                diferencia < 0 -> EstadoChip("↓ ${precio4(diferencia)}$porcentaje", EstiloChip.EXITO)
                                else -> EstadoChip("Sin cambio", EstiloChip.INFO)
                            }
                        }
                    }
                }
            }
        }
        BotonAgregar(texto = "Nueva compra", onClick = onAbrirNuevo, modifier = Modifier.align(Alignment.BottomEnd))
    }

    // Mismo formulario que "Registrar compra de insumo" de Insumos: aquí además se elige el insumo.
    if (formVisible) {
        AppBottomSheet(onDismiss = onCerrarForm) {
            var insumoId by remember { mutableStateOf<String?>(null) }
            var cantidadTexto by remember { mutableStateOf("") }
            var costoTexto by remember { mutableStateOf("") }
            val insumo = insumos.find { it.id == insumoId }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                EncabezadoFormulario("Registrar compra de insumo", insumo?.nombre ?: "Elige el insumo que compraste")
                AppDropdown(
                    seleccionado = insumo,
                    opciones = insumos,
                    etiqueta = "Insumo",
                    textoOpcion = { it.nombre },
                    onSeleccionar = {
                        insumoId = it.id
                        // Como en Insumos: se propone el costo unitario actual del insumo.
                        costoTexto = textoCosto(it.costoUnitario)
                    },
                )
                AppTextField(
                    cantidadTexto, { cantidadTexto = it }, "Cantidad (${insumo?.unidadMedidaSimbolo?.ifBlank { null } ?: "unid."})",
                    teclado = KeyboardType.Decimal, modifier = Modifier.padding(top = 10.dp),
                )
                AppTextField(
                    costoTexto, { costoTexto = it }, "Costo unitario pagado (S/)",
                    teclado = KeyboardType.Decimal, modifier = Modifier.padding(top = 10.dp),
                )
                val cantidad = cantidadTexto.replace(',', '.').toDoubleOrNull()
                val costo = costoTexto.replace(',', '.').toDoubleOrNull()
                if (cantidad != null && costo != null) {
                    Text(
                        "Total: ${formatMoney(cantidad * costo)}",
                        style = CuerpoMedioStyle,
                        color = VerdeOscuro,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (error != null) {
                    MensajeError(error, Modifier.padding(top = 10.dp))
                }
                BotonesFormulario(
                    onCancelar = onCerrarForm,
                    onGuardar = { onRegistrar(insumoId, cantidad, costo) },
                    textoGuardar = "Registrar",
                )
            }
        }
    }
}

/** 2.5 se muestra "2.5"; 3.0, "3". */
private fun textoCosto(valor: Double): String = if (valor % 1.0 == 0.0) valor.toLong().toString() else valor.toString()
