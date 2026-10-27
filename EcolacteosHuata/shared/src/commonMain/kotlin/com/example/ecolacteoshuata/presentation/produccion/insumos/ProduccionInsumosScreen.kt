package com.example.ecolacteoshuata.presentation.produccion.insumos

import androidx.compose.material.icons.filled.Inventory2
import com.example.ecolacteoshuata.core.ui.TarjetaLista
import com.example.ecolacteoshuata.core.ui.BotonesFormulario
import com.example.ecolacteoshuata.core.ui.EncabezadoFormulario
import com.example.ecolacteoshuata.core.ui.MensajeError
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.RojoFondo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.AppDropdown
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.BotonAgregar
import com.example.ecolacteoshuata.core.ui.BotonAccion
import com.example.ecolacteoshuata.core.ui.ConfirmarDialog
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Edit
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.util.formatMoney
import com.example.ecolacteoshuata.domain.model.Insumo
import com.example.ecolacteoshuata.domain.model.UnidadMedida

/** "12.5" en vez de "12.5000000001", y vacío cuando es 0 para que el campo quede limpio. */
private fun textoNumero(valor: Double): String = if (valor == 0.0) "" else valor.toString().removeSuffix(".0")

@Composable
fun ProduccionInsumosScreen(
    insumos: List<Insumo>,
    unidades: List<UnidadMedida>,
    formVisible: Insumo?,
    error: String?,
    onAbrirNuevo: () -> Unit,
    onAbrirEditar: (Insumo) -> Unit,
    onCerrarForm: () -> Unit,
    onGuardar: (Insumo) -> Unit,
    onEliminar: (String) -> Unit,
    compraInsumo: Insumo?,
    onAbrirCompra: (Insumo) -> Unit,
    onCerrarCompra: () -> Unit,
    onRegistrarCompra: (cantidad: Double?, costoUnitario: Double?) -> Unit,
) {
    var porEliminar by remember { mutableStateOf<Insumo?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (insumos.isEmpty()) {
            EmptyState("Sin insumos", "Registra el primer insumo de producción.", modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
                item { TituloSeccion("Insumos (${insumos.size})") }
                itemsIndexed(insumos) { index, insumo ->
                    StaggeredListItem(index = index) {
                        TarjetaLista(
                            titulo = insumo.nombre,
                            subtitulo = "Costo: ${formatMoney(insumo.costoUnitario)} / ${insumo.unidadMedidaSimbolo.ifBlank { "unid." }}",
                            icono = Icons.Filled.Inventory2,
                            derecha = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Stock", style = CuerpoStyle, color = GrisTexto)
                                    Text("${textoNumero(insumo.stockActual).ifBlank { "0" }} ${insumo.unidadMedidaSimbolo}", style = CuerpoMedioStyle, color = VerdeOscuro)
                                }
                            },
                            // Acciones, como en la tabla de Laravel.
                            acciones = {
                                BotonAccion("Comprar", Icons.Filled.AddShoppingCart, VerdePrimario, onClick = { onAbrirCompra(insumo) })
                                BotonAccion("Editar", Icons.Filled.Edit, VerdeOscuro, onClick = { onAbrirEditar(insumo) })
                                BotonAccion("Eliminar", Icons.Filled.Delete, RojoTexto, onClick = { porEliminar = insumo })
                            },
                        )
                    }
                }
            }
        }
        BotonAgregar(texto = "Nuevo insumo", onClick = onAbrirNuevo, modifier = Modifier.align(Alignment.BottomEnd))
    }

    porEliminar?.let { insumo ->
        ConfirmarDialog(
            titulo = "¿Eliminar este insumo?",
            mensaje = "Se eliminará \"${insumo.nombre}\". Esta acción no se puede deshacer.",
            textoConfirmar = "Eliminar",
            onConfirmar = { onEliminar(insumo.id); porEliminar = null },
            onCancelar = { porEliminar = null },
        )
    }

    if (compraInsumo != null) {
        AppBottomSheet(onDismiss = onCerrarCompra) {
            var cantidadTexto by remember(compraInsumo.id) { mutableStateOf("") }
            var costoTexto by remember(compraInsumo.id) { mutableStateOf(textoNumero(compraInsumo.costoUnitario)) }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                EncabezadoFormulario("Registrar compra de insumo", compraInsumo.nombre)
                AppTextField(
                    cantidadTexto, { cantidadTexto = it }, "Cantidad (${compraInsumo.unidadMedidaSimbolo.ifBlank { "unid." }})",
                    teclado = KeyboardType.Decimal,
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
                BotonesFormulario(onCancelar = onCerrarCompra, onGuardar = { onRegistrarCompra(cantidad, costo) }, textoGuardar = "Registrar")
            }
        }
    }

    if (formVisible != null) {
        AppBottomSheet(onDismiss = onCerrarForm) {
            var nombre by remember(formVisible.id) { mutableStateOf(formVisible.nombre) }
            var unidadId by remember(formVisible.id) { mutableStateOf(formVisible.unidadMedidaId) }
            var costoTexto by remember(formVisible.id) { mutableStateOf(textoNumero(formVisible.costoUnitario)) }
            var stockTexto by remember(formVisible.id) { mutableStateOf(textoNumero(formVisible.stockActual).ifBlank { "0" }) }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                EncabezadoFormulario(if (formVisible.id.isBlank()) "Nuevo insumo" else "Editar insumo")
                // Mismo orden que Laravel: Nombre, Unidad de medida, Costo unitario, Stock actual.
                AppTextField(nombre, { nombre = it }, "Nombre", placeholder = "Leche fresca")
                AppDropdown(
                    seleccionado = unidades.find { it.id == unidadId },
                    opciones = unidades,
                    etiqueta = "Unidad de medida",
                    textoOpcion = { "${it.nombre} (${it.simbolo})" },
                    onSeleccionar = { unidadId = it.id },
                    placeholder = "Selecciona...",
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                AppTextField(
                    costoTexto, { costoTexto = it }, "Costo unitario (S/)",
                    teclado = KeyboardType.Decimal, modifier = Modifier.padding(top = 10.dp),
                )
                AppTextField(
                    stockTexto, { stockTexto = it }, "Stock actual",
                    teclado = KeyboardType.Decimal, modifier = Modifier.padding(top = 10.dp),
                )
                if (error != null) {
                    MensajeError(error, Modifier.padding(top = 10.dp))
                }
                BotonesFormulario(onCancelar = onCerrarForm, onGuardar = {
                            onGuardar(
                                formVisible.copy(
                                    nombre = nombre,
                                    unidadMedidaId = unidadId,
                                    costoUnitario = costoTexto.replace(',', '.').toDoubleOrNull() ?: 0.0,
                                    stockActual = stockTexto.replace(',', '.').toDoubleOrNull() ?: 0.0,
                                )
                            )
                        })
            }
        }
    }
}
