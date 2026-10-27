package com.example.ecolacteoshuata.presentation.acopiador.recojo

import androidx.compose.material.icons.filled.Agriculture
import com.example.ecolacteoshuata.core.ui.BotonesFormulario
import com.example.ecolacteoshuata.core.ui.EncabezadoFormulario
import com.example.ecolacteoshuata.core.ui.TarjetaLista
import com.example.ecolacteoshuata.core.ui.MensajeError
import com.example.ecolacteoshuata.core.ui.textoOpcion
import com.example.ecolacteoshuata.core.ui.fondoOpcion
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.util.litrosConDecimales
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.EtiquetaMayusculaStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.RojoFondo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.UbicacionFincaStyle
import com.example.ecolacteoshuata.core.theme.VerdeClaro
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.theme.KpiStyle
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.AppDropdown
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.util.formatMoney
import com.example.ecolacteoshuata.domain.model.EstadoLeche
import com.example.ecolacteoshuata.domain.model.Proveedor
import com.example.ecolacteoshuata.domain.model.Turno
import com.example.ecolacteoshuata.presentation.acopiador.SelectorTurnosRuta
import com.example.ecolacteoshuata.presentation.acopiador.etiqueta
import com.example.ecolacteoshuata.presentation.acopiador.etiquetaConHorario

@Composable
fun AcopiadorRecojoScreen(
    state: AcopiadorRecojoUiState,
    onSeleccionarTurno: (Turno) -> Unit,
    onAbrirFormulario: (Proveedor) -> Unit,
    onCerrarFormulario: () -> Unit,
    onTurnoFormularioChange: (Turno) -> Unit,
    onLitrosChange: (String) -> Unit,
    onEstadoChange: (EstadoLeche) -> Unit,
    onEstadoOtroChange: (String) -> Unit,
    onGuardar: () -> Unit,
) {
    val recogidos = state.proveedores.count { it.id in state.recogidosHoy }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
        // 1. Turno en el que se registra.
        item {
            SelectorTurnosRuta(turnos = state.turnos, seleccionado = state.turnoSeleccionado, onSeleccionar = onSeleccionarTurno)
        }

        // 2. Proveedores de la ruta con su avance.
        item {
            TituloSeccion("Proveedores de la ruta") {
                Text("$recogidos de ${state.proveedores.size} recogidos", style = CuerpoMedioStyle, color = VerdeOscuro)
            }
        }
        if (state.proveedores.isEmpty()) {
            item { EmptyState("Sin proveedores", "No tienes proveedores asignados.") }
        }
        itemsIndexed(state.proveedores) { index, proveedor ->
            val recogido = proveedor.id in state.recogidosHoy
            StaggeredListItem(index = index) {
                TarjetaLista(
                    titulo = proveedor.nombreCompleto,
                    icono = Icons.Filled.Agriculture,
                    colorIcono = if (recogido) VerdePrimario else null,
                    onClick = { onAbrirFormulario(proveedor) },
                    derecha = {
                        when {
                            proveedor.bloqueado -> EstadoChip("Bloqueado", EstiloChip.ERROR)
                            recogido -> EstadoChip("Recogido", EstiloChip.EXITO)
                            else -> EstadoChip("Pendiente", EstiloChip.ADVERTENCIA)
                        }
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = VerdePrimario, modifier = Modifier.size(18.dp))
                        Text(
                            proveedor.fincaUbicacion.ifBlank { "Sin ubicación registrada" },
                            style = UbicacionFincaStyle.copy(fontSize = 16.sp),
                            color = VerdeOscuro,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    if (proveedor.fincaReferencia.isNotBlank()) {
                        Text(proveedor.fincaReferencia, style = CuerpoStyle, color = GrisTexto, modifier = Modifier.padding(start = 22.dp, top = 2.dp))
                    }
                }
            }
        }
    }

    val proveedor = state.proveedorFormulario
    if (proveedor != null) {
        AppBottomSheet(onDismiss = onCerrarFormulario) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                EncabezadoFormulario(if (proveedor.bloqueado) "Proveedor bloqueado" else "Registrar recojo", proveedor.nombreCompleto)

                if (proveedor.bloqueado) {
                    Text(
                        "${proveedor.nombreCompleto} tiene ${proveedor.alteraciones} alteraciones de calidad registradas y está " +
                            "bloqueado para entregar leche. Comunícate con planta para desbloquearlo.",
                        style = CuerpoStyle,
                        color = Negro,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    PrimaryButton(texto = "Entendido", onClick = onCerrarFormulario, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                    return@Column
                }

                AppTextField(state.litros, onLitrosChange, "Litros", teclado = KeyboardType.Decimal, modifier = Modifier.padding(top = 20.dp))

                AppDropdown(
                    seleccionado = state.turnoFormulario,
                    opciones = state.turnos,
                    etiqueta = "Turno de ruta",
                    textoOpcion = { it.etiquetaConHorario() },
                    onSeleccionar = onTurnoFormularioChange,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )

                AppDropdown(
                    seleccionado = state.estadoLeche,
                    opciones = EstadoLeche.entries,
                    etiqueta = "Estado de la leche",
                    textoOpcion = { it.etiqueta() },
                    onSeleccionar = onEstadoChange,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
                if (state.estadoLeche == EstadoLeche.OTRO) {
                    AppTextField(state.estadoOtro, onEstadoOtroChange, "Describe el estado", modifier = Modifier.padding(top = 16.dp))
                }

                Column(
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .fillMaxWidth()
                        .background(VerdeClaro, RoundedCornerShape(12.dp))
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Litros que estás registrando", style = CuerpoStyle, color = GrisTexto)
                    val litros = state.litros.toDoubleOrNull() ?: 0.0
                    Text(litrosConDecimales(litros), style = KpiStyle, color = VerdeOscuro, modifier = Modifier.padding(top = 4.dp))
                }

                if (state.error != null) {
                    MensajeError(state.error, Modifier.padding(top = 12.dp))
                }

                BotonesFormulario(onCancelar = onCerrarFormulario, onGuardar = onGuardar, cargando = state.guardando)
            }
        }
    }
}
