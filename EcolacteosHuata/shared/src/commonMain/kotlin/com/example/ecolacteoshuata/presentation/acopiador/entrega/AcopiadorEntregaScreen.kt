package com.example.ecolacteoshuata.presentation.acopiador.entrega

import com.example.ecolacteoshuata.core.ui.MensajeError
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WaterDrop
import com.example.ecolacteoshuata.core.ui.DatoDialogo
import com.example.ecolacteoshuata.core.ui.DialogoConfirmacion
import com.example.ecolacteoshuata.core.ui.TonoDialogo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.AcentoAmbar
import com.example.ecolacteoshuata.core.theme.AcentoCeleste
import com.example.ecolacteoshuata.core.theme.AcentoCoral
import com.example.ecolacteoshuata.core.theme.AcentoVerde
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.RojoFondo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.HeroMetricCard
import com.example.ecolacteoshuata.core.ui.KpiCard
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.util.formatMoney
import com.example.ecolacteoshuata.domain.model.EstadoLeche
import com.example.ecolacteoshuata.domain.model.Turno
import com.example.ecolacteoshuata.presentation.acopiador.SelectorTurnosRuta
import com.example.ecolacteoshuata.presentation.acopiador.etiqueta
import com.example.ecolacteoshuata.presentation.acopiador.etiquetaConHorario
import com.example.ecolacteoshuata.core.util.litrosConDecimales

@Composable
fun AcopiadorEntregaScreen(
    state: AcopiadorEntregaUiState,
    onSeleccionarTurno: (Turno) -> Unit,
    onPedirConfirmacion: (Boolean) -> Unit,
    onEntregar: () -> Unit,
    onConfirmacionConsumida: () -> Unit,
) {
    val resumen = state.resumen
    val litros = resumen?.litrosTotal ?: 0.0
    val recogidos = state.detalle.map { it.proveedorId }.distinct().size
    val noRecogidos = resumen?.proveedoresNoRecogidos ?: 0

    Box(Modifier.fillMaxSize()) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
        item {
            SelectorTurnosRuta(turnos = state.turnos, seleccionado = state.turnoSeleccionado, onSeleccionar = onSeleccionarTurno)
        }

        item {
            HeroMetricCard(
                etiqueta = "Litros por entregar",
                valor = litrosConDecimales(litros),
                detalle = state.turnoSeleccionado?.let { "Hoy · entrega del ${it.etiquetaConHorario()}" } ?: "Selecciona un turno de ruta",
                icono = Icons.Filled.LocalShipping,
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiCard("Recogidos", recogidos.toString(), AcentoVerde, Modifier.weight(1f), icono = Icons.Filled.Groups)
                KpiCard(
                    "No recogidos",
                    noRecogidos.toString(),
                    if (noRecogidos > 0) AcentoCoral else AcentoVerde,
                    Modifier.weight(1f),
                    icono = Icons.Filled.PersonOff,
                )
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiCard("Precio / litro", formatMoney(state.precioLitro), AcentoCeleste, Modifier.weight(1f), icono = Icons.Filled.Sell)
                KpiCard("Total en deuda", formatMoney(resumen?.totalDeuda ?: 0.0), AcentoAmbar, Modifier.weight(1f), icono = Icons.Filled.MoneyOff)
            }
        }

        item {
            SectionCard {
                Text("¿Todo listo?", style = TituloPantallaStyle, color = Negro)
                Text(
                    if (litros > 0) {
                        "Se entregarán en planta todos los recojos pendientes de hoy: ${litrosConDecimales(litros)}. Planta la verá en Laravel al sincronizar."
                    } else {
                        if (state.entregasHoy.isNotEmpty()) "Ya entregaste todo lo recogido hoy. Los nuevos recojos aparecerán aquí."
                        else "Todavía no hay recojos pendientes hoy. Regístralos en \"Recojo de ruta\"."
                    },
                    style = CuerpoStyle,
                    color = GrisTexto,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (state.error != null) {
                    MensajeError(state.error, Modifier.padding(top = 10.dp))
                }
                PrimaryButton(
                    texto = "Entregar a planta",
                    onClick = { onPedirConfirmacion(true) },
                    habilitado = litros > 0,
                    cargando = state.entregando,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }

        if (state.entregasHoy.isNotEmpty()) {
            item { TituloSeccion("Entregas de hoy (${state.entregasHoy.size})") }
            itemsIndexed(state.entregasHoy) { _, entrega ->
                SectionCard {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Guía ${entrega.guia}", style = CuerpoMedioStyle.copy(fontSize = 15.sp), color = Negro)
                            Text(
                                state.turnos.firstOrNull { it.id == entrega.turnoId }?.etiquetaConHorario() ?: "Turno de ruta",
                                style = CuerpoStyle,
                                color = GrisTexto,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(litrosConDecimales(entrega.litrosTotal), style = TituloPantallaStyle, color = VerdeOscuro, textAlign = TextAlign.End)
                            Text("Deuda ${formatMoney(entrega.totalDeuda)}", style = CuerpoStyle, color = GrisTexto)
                        }
                    }
                }
            }
        }

        // Como la tabla "Proveedores de la ruta" de Laravel, en tarjetas.
        item { TituloSeccion("Proveedores de la ruta (${state.proveedoresRuta.size})") }

        if (state.proveedoresRuta.isEmpty()) {
            item { EmptyState("Sin proveedores", "No tienes proveedores asignados todavía.", icono = Icons.Filled.Groups) }
        } else {
            itemsIndexed(state.proveedoresRuta, key = { _, f -> f.proveedor.id }) { index, fila ->
                StaggeredListItem(index = index) {
                    SectionCard {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${fila.nombres} ${fila.apellidos}".trim(), style = CuerpoMedioStyle.copy(fontSize = 15.sp), color = Negro)
                                Text(
                                    fila.proveedor.fincaUbicacion.ifBlank { "Sin registrar" },
                                    style = TituloPantallaStyle.copy(fontSize = 16.sp),
                                    color = VerdeOscuro,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(litrosConDecimales(fila.litrosHoy), style = TituloPantallaStyle, color = VerdeOscuro, textAlign = TextAlign.End)
                                Text("recogidos hoy", style = CuerpoStyle, color = GrisTexto)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("DNI/RUC: ${fila.documento.ifBlank { "—" }}", style = CuerpoStyle, color = GrisTexto)
                                Text("Celular: ${fila.celular.ifBlank { "—" }}", style = CuerpoStyle, color = GrisTexto)
                            }
                            when {
                                fila.proveedor.bloqueado -> EstadoChip("Bloqueado por calidad", EstiloChip.ERROR)
                                fila.recogidoHoy -> EstadoChip("Ya recogido hoy", EstiloChip.EXITO)
                                else -> EstadoChip("Pendiente hoy", EstiloChip.INFO)
                            }
                        }
                    }
                }
            }
        }
    }

    }

    if (state.confirmando) {
        DialogoConfirmacion(
            titulo = "¿Llevamos la leche a planta?",
            mensaje = "Se registrará la entrega de hoy y estos recojos quedarán como entregados. " +
                "Planta la verá en el sistema apenas haya conexión.",
            icono = Icons.Filled.LocalShipping,
            textoConfirmar = "Sí, entregar",
            textoCancelar = "Todavía no",
            onConfirmar = onEntregar,
            onCancelar = { onPedirConfirmacion(false) },
            cargando = state.entregando,
            datos = listOf(
                DatoDialogo("Litros", litrosConDecimales(litros), Icons.Filled.WaterDrop),
                DatoDialogo("Proveedores", "${state.detalle.map { it.proveedorId }.distinct().size} recogidos", Icons.Filled.Groups),
                DatoDialogo("Deuda", formatMoney(resumen?.totalDeuda ?: 0.0), Icons.Filled.MoneyOff),
                DatoDialogo("Turno", state.turnoSeleccionado?.nombre ?: "—", Icons.Filled.Schedule),
            ),
        )
    }

    state.entregaCompletada?.let { entrega ->
        DialogoConfirmacion(
            titulo = "¡Entrega registrada!",
            mensaje = "La leche ya quedó registrada en planta. Gracias por tu recorrido de hoy.",
            icono = Icons.Filled.CheckCircle,
            tono = TonoDialogo.EXITO,
            textoConfirmar = "Listo",
            onConfirmar = onConfirmacionConsumida,
            datos = listOf(
                DatoDialogo("Guía", entrega.guia, Icons.AutoMirrored.Filled.ReceiptLong),
                DatoDialogo("Litros", litrosConDecimales(entrega.litrosTotal), Icons.Filled.WaterDrop),
                DatoDialogo("Deuda", formatMoney(entrega.totalDeuda), Icons.Filled.MoneyOff),
                DatoDialogo("Faltaron", "${entrega.proveedoresNoRecogidos} prov.", Icons.Filled.PersonOff),
            ),
        )
    }
}
