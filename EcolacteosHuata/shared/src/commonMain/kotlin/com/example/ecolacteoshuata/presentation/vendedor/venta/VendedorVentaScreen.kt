package com.example.ecolacteoshuata.presentation.vendedor.venta

import com.example.ecolacteoshuata.core.ui.MensajeError
import com.example.ecolacteoshuata.core.ui.textoOpcion
import com.example.ecolacteoshuata.core.ui.fondoOpcion
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.EtiquetaMayusculaStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.RojoFondo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdeClaro
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.ui.AppDropdown
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.util.formatMoney
import com.example.ecolacteoshuata.domain.model.Cliente
import com.example.ecolacteoshuata.domain.model.Descuento
import com.example.ecolacteoshuata.domain.model.MetodoPago
import com.example.ecolacteoshuata.domain.model.Producto

private fun cantidadTexto(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

private fun etiquetaMetodoPago(metodo: MetodoPago) = when (metodo) {
    MetodoPago.EFECTIVO -> "Efectivo"
    MetodoPago.YAPE -> "Yape"
    MetodoPago.PLIN -> "Plin"
    MetodoPago.TARJETA -> "Tarjeta"
    MetodoPago.OTRO -> "Otro"
}

@Composable
fun VendedorVentaScreen(
    productos: List<Producto>,
    clientes: List<Cliente>,
    descuentos: List<Descuento>,
    state: VendedorVentaUiState,
    onAgregarProducto: (Producto) -> Unit,
    onQuitarUnidad: (Producto) -> Unit,
    onSeleccionarCliente: (Cliente?) -> Unit,
    onAlternarDescuento: (Descuento) -> Unit,
    onNombreLibreChange: (String) -> Unit,
    onDocLibreChange: (String) -> Unit,
    onMetodoPagoChange: (MetodoPago) -> Unit,
    onEfectivoChange: (String) -> Unit,
    onCobrar: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
        item {
            TituloSeccion("Productos")
            if (productos.isEmpty()) {
                Text("No hay producción publicada con stock para vender.", style = CuerpoStyle, color = GrisTexto)
            }
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                productos.forEach { producto ->
                    Column(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .background(VerdeClaro, RoundedCornerShape(14.dp))
                            .clickable { onAgregarProducto(producto) }
                            .padding(12.dp),
                    ) {
                        Text(producto.nombre, style = CuerpoMedioStyle, color = Negro)
                        Text(formatMoney(producto.precio), style = CuerpoStyle, color = VerdeOscuro)
                        Text("Disponible: ${cantidadTexto(producto.disponible)}", style = EtiquetaMayusculaStyle, color = GrisTexto)
                    }
                }
            }
        }

        item {
            TituloSeccion("Carrito")
        }
        if (state.carrito.isEmpty()) {
            item { EmptyState("Carrito vacío", "Toca un producto para agregarlo.") }
        } else {
            items(state.carrito) { item ->
                SectionCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.producto.nombre, style = CuerpoMedioStyle, color = Negro)
                            Text("${item.cantidad} × ${formatMoney(item.producto.precio)}", style = CuerpoStyle, color = GrisTexto)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Remove, contentDescription = "Quitar", tint = VerdeOscuro, modifier = Modifier.clickable { onQuitarUnidad(item.producto) })
                            Text(item.cantidad.toString(), style = CuerpoMedioStyle, color = Negro, modifier = Modifier.padding(horizontal = 8.dp))
                            Icon(Icons.Filled.Add, contentDescription = "Agregar", tint = VerdeOscuro, modifier = Modifier.clickable { onAgregarProducto(item.producto) })
                        }
                        Text(formatMoney(item.subtotal), style = CuerpoMedioStyle, color = Negro, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }

        item {
            SectionCard {
                Text("Cliente (opcional)", style = CuerpoMedioStyle, color = Negro, modifier = Modifier.padding(bottom = 6.dp))
                AppDropdown(
                    seleccionado = state.clienteSeleccionado,
                    opciones = clientes,
                    etiqueta = "Cliente registrado",
                    textoOpcion = { it.nombreCompleto },
                    onSeleccionar = onSeleccionarCliente,
                )
                if (state.clienteSeleccionado == null) {
                    AppTextField(state.clienteNombreLibre, onNombreLibreChange, "Nombre (opcional)", modifier = Modifier.padding(top = 8.dp))
                    AppTextField(state.clienteDocLibre, onDocLibreChange, "DNI / RUC (opcional)", modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        if (descuentos.isNotEmpty()) {
            item {
                SectionCard {
                    Text("Descuentos", style = CuerpoMedioStyle, color = Negro, modifier = Modifier.padding(bottom = 6.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        descuentos.forEach { descuento ->
                            val activo = state.descuentos.any { it.id == descuento.id }
                            Row(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .background(fondoOpcion(activo), RoundedCornerShape(999.dp))
                                    .clickable { onAlternarDescuento(descuento) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text("${descuento.nombre} (${cantidadTexto(descuento.porcentaje)}%)", style = EtiquetaMayusculaStyle, color = textoOpcion(activo))
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Resumen", style = TituloPantallaStyle, color = Negro, modifier = Modifier.weight(1f))
                    val unidades = state.carrito.sumOf { it.cantidad }
                    Text(
                        "$unidades ${if (unidades == 1) "producto" else "productos"}",
                        style = EtiquetaMayusculaStyle,
                        color = VerdeOscuro,
                        modifier = Modifier.background(VerdeClaro, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                FilaResumen("Subtotal", formatMoney(state.totales.subtotal), Negro, Modifier.padding(top = 12.dp))
                if (state.totales.descuento > 0) {
                    FilaResumen("Descuento · ${state.totales.descuentoTipo}", "− ${formatMoney(state.totales.descuento)}", VerdePrimario, Modifier.padding(top = 6.dp))
                }
                // Total destacado: etiqueta a la izquierda y el monto grande a la derecha.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(VerdePrimario, RoundedCornerShape(18.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TOTAL A COBRAR", style = EtiquetaMayusculaStyle, color = Blanco.copy(alpha = 0.85f))
                        if (state.totales.descuento > 0) {
                            Text("Ahorra ${formatMoney(state.totales.descuento)}", style = CuerpoStyle, color = Blanco.copy(alpha = 0.85f))
                        }
                    }
                    Text(formatMoney(state.totales.total), style = TituloPantallaStyle.copy(fontSize = 26.sp), color = Blanco)
                }

                Text("Método de pago", style = CuerpoMedioStyle, color = Negro, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetodoPago.entries.forEach { metodo ->
                        val activo = metodo == state.metodoPago
                        Row(
                            modifier = Modifier
                                .background(fondoOpcion(activo), RoundedCornerShape(14.dp))
                                .clickable { onMetodoPagoChange(metodo) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(iconoMetodo(metodo), contentDescription = null, tint = textoOpcion(activo), modifier = Modifier.size(18.dp))
                            Text(etiquetaMetodoPago(metodo), style = CuerpoMedioStyle, color = textoOpcion(activo), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }

                if (state.metodoPago == MetodoPago.EFECTIVO) {
                    AppTextField(
                        state.efectivoRecibido,
                        onEfectivoChange,
                        "Efectivo recibido (S/)",
                        teclado = KeyboardType.Decimal,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    // Montos rápidos: el exacto y los billetes más comunes que alcanzan.
                    val total = state.totales.total
                    val rapidos = (listOf(total) + listOf(10.0, 20.0, 50.0, 100.0, 200.0).filter { it > total }.take(3)).distinct()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rapidos.forEachIndexed { indice, monto ->
                            Text(
                                if (indice == 0) "Exacto" else formatMoney(monto),
                                style = CuerpoMedioStyle,
                                color = VerdeOscuro,
                                modifier = Modifier
                                    .border(1.dp, VerdePrimario.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                                    .clickable { onEfectivoChange(cantidadTexto(monto)) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                    val recibido = state.efectivoRecibido.replace(',', '.').toDoubleOrNull()
                    if (recibido != null) {
                        val vuelto = recibido - total
                        val falta = vuelto < 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .background(if (falta) RojoFondo else VerdeClaro, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (falta) "Falta" else "Vuelto", style = CuerpoMedioStyle, color = if (falta) RojoTexto else VerdeOscuro, modifier = Modifier.weight(1f))
                            Text(formatMoney(kotlin.math.abs(vuelto)), style = TituloPantallaStyle, color = if (falta) RojoTexto else VerdeOscuro)
                        }
                    }
                }

                if (state.error != null) {
                    MensajeError(state.error, Modifier.padding(top = 10.dp))
                }

                PrimaryButton(
                    texto = "Cobrar ${formatMoney(state.totales.total)}",
                    onClick = onCobrar,
                    habilitado = state.carrito.isNotEmpty(),
                    cargando = state.cargando,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun FilaResumen(etiqueta: String, valor: String, colorValor: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, style = CuerpoStyle, color = GrisTexto, modifier = Modifier.weight(1f))
        Text(valor, style = CuerpoMedioStyle, color = colorValor)
    }
}

private fun iconoMetodo(metodo: MetodoPago) = when (metodo) {
    MetodoPago.EFECTIVO -> Icons.Filled.Payments
    MetodoPago.YAPE, MetodoPago.PLIN -> Icons.Filled.PhoneAndroid
    MetodoPago.TARJETA -> Icons.Filled.CreditCard
    MetodoPago.OTRO -> Icons.Filled.MoreHoriz
}
