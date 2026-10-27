package com.example.ecolacteoshuata.presentation.admin.clientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.example.ecolacteoshuata.core.theme.Ambar
import com.example.ecolacteoshuata.core.theme.AmbarFondo
import com.example.ecolacteoshuata.core.theme.AmbarTexto
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Rojo
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.BotonAccion
import com.example.ecolacteoshuata.core.ui.BotonAgregar
import com.example.ecolacteoshuata.core.ui.BotonesFormulario
import com.example.ecolacteoshuata.core.ui.ConfirmToastHost
import com.example.ecolacteoshuata.core.ui.ConfirmarDialog
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EncabezadoFormulario
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.FilaDato
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import com.example.ecolacteoshuata.core.ui.MensajeError
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.ui.TarjetaLista
import com.example.ecolacteoshuata.domain.model.Cliente

private fun porcentaje(valor: Double): String {
    val centesimos = kotlin.math.round(valor * 100).toLong()
    return "${centesimos / 100}.${(centesimos % 100).toString().padStart(2, '0')}%"
}

/** "Clientes" de Laravel: clientes que compran productos en el punto de venta. */
@Composable
fun AdminClientesScreen(
    state: ClientesUiState,
    formVisible: Cliente?,
    porEliminar: Cliente?,
    error: String?,
    mensaje: String?,
    onBusqueda: (String) -> Unit,
    onAbrirNuevo: () -> Unit,
    onAbrirEditar: (Cliente) -> Unit,
    onCerrarForm: () -> Unit,
    onGuardar: (Cliente) -> Unit,
    onPedirEliminar: (Cliente) -> Unit,
    onCancelarEliminar: () -> Unit,
    onEliminar: () -> Unit,
    onConsumirMensaje: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
            item(key = "ayuda") {
                Text("Clientes que compran productos en el punto de venta.", style = CuerpoStyle, color = GrisTexto)
            }
            state.masFrecuente?.let { frecuente ->
                item(key = "frecuente") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AmbarFondo)
                            .border(1.dp, Ambar.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = AmbarTexto, modifier = Modifier.size(18.dp))
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text("Cliente más frecuente", style = CuerpoStyle, color = AmbarTexto)
                            Text(
                                "${frecuente.nombreCompleto} (${frecuente.comprasCount} compras)",
                                style = CuerpoMedioStyle,
                                color = AmbarTexto,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            item(key = "busqueda") { AppTextField(state.busqueda, onBusqueda, "Buscar cliente...", placeholder = "Nombre, apellido o DNI/RUC") }
            if (state.filas.isEmpty()) {
                item(key = "vacio") {
                    EmptyState(
                        "Sin clientes",
                        if (state.busqueda.isBlank()) "No hay clientes registrados todavía." else "Ningún cliente coincide con la búsqueda.",
                        icono = Icons.Filled.Groups,
                    )
                }
            }
            itemsIndexed(state.filas, key = { _, f -> f.cliente.id }) { index, fila ->
                val cliente = fila.cliente
                StaggeredListItem(index = index) {
                    TarjetaLista(
                        titulo = cliente.nombreCompleto,
                        subtitulo = "DNI/RUC: ${cliente.documento}",
                        icono = Icons.Filled.Person,
                        onClick = { onAbrirEditar(cliente) },
                        derecha = if (cliente.id == state.masFrecuente?.id) {
                            { EstadoChip("Más frecuente", EstiloChip.ADVERTENCIA) }
                        } else {
                            null
                        },
                        acciones = {
                            BotonAccion("Editar", Icons.Filled.Edit, VerdeOscuro, onClick = { onAbrirEditar(cliente) })
                            BotonAccion("Eliminar", Icons.Filled.Delete, Rojo, onClick = { onPedirEliminar(cliente) })
                        },
                    ) {
                        FilaDato("Compras", cliente.comprasCount.toString(), Modifier.padding(top = 6.dp))
                        FilaDato("% Descuento", porcentaje(fila.descuentoPct))
                    }
                }
            }
        }
        BotonAgregar(texto = "Nuevo cliente", onClick = onAbrirNuevo, modifier = Modifier.align(Alignment.BottomEnd))
        ConfirmToastHost(mensaje = mensaje, onDismiss = onConsumirMensaje, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (formVisible != null) {
        AppBottomSheet(onDismiss = onCerrarForm) {
            var nombres by remember(formVisible.id) { mutableStateOf(formVisible.nombres) }
            var apellidos by remember(formVisible.id) { mutableStateOf(formVisible.apellidos) }
            var documento by remember(formVisible.id) { mutableStateOf(formVisible.documento) }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                EncabezadoFormulario(if (formVisible.id.isBlank()) "Nuevo cliente" else "Editar cliente")
                AppTextField(nombres, { nombres = it }, "Nombres")
                AppTextField(apellidos, { apellidos = it }, "Apellidos", modifier = Modifier.padding(top = 10.dp))
                AppTextField(documento, { documento = it }, "DNI/RUC", teclado = KeyboardType.Number, modifier = Modifier.padding(top = 10.dp))
                if (error != null) {
                    MensajeError(error, Modifier.padding(top = 10.dp))
                }
                BotonesFormulario(
                    onCancelar = onCerrarForm,
                    onGuardar = { onGuardar(formVisible.copy(nombres = nombres.trim(), apellidos = apellidos.trim(), documento = documento.trim())) },
                )
            }
        }
    }

    porEliminar?.let { cliente ->
        ConfirmarDialog(
            titulo = "¿Eliminar este cliente?",
            mensaje = "Se eliminará a ${cliente.nombreCompleto}. Esta acción no se puede deshacer.",
            textoConfirmar = "Eliminar",
            onConfirmar = onEliminar,
            onCancelar = onCancelarEliminar,
        )
    }
}
