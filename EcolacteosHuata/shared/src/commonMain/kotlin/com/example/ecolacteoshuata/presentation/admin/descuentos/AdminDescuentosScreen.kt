package com.example.ecolacteoshuata.presentation.admin.descuentos

import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.theme.Rojo
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Edit
import com.example.ecolacteoshuata.core.ui.BotonesFormulario
import com.example.ecolacteoshuata.core.ui.EncabezadoFormulario
import com.example.ecolacteoshuata.core.ui.BotonAccion
import com.example.ecolacteoshuata.core.ui.TarjetaLista
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
import androidx.compose.material.icons.filled.Discount
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.BotonAgregar
import com.example.ecolacteoshuata.core.ui.ConfirmToastHost
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.core.ui.SecondaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.domain.model.Descuento

@Composable
fun AdminDescuentosScreen(
    descuentos: List<Descuento>,
    form: DescuentoForm?,
    mensaje: String?,
    onNuevo: () -> Unit,
    onEditar: (Descuento) -> Unit,
    onAlternarActivo: (Descuento) -> Unit,
    onEliminar: (Descuento) -> Unit,
    onCerrar: () -> Unit,
    onNombre: (String) -> Unit,
    onPorcentaje: (String) -> Unit,
    onActivo: (Boolean) -> Unit,
    onGuardar: () -> Unit,
    onConsumirMensaje: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
            item {
                Text("Descuentos que el vendedor puede activar en el punto de venta.", style = CuerpoStyle, color = GrisTexto)
            }
            if (descuentos.isEmpty()) {
                item {
                    EmptyState("Sin descuentos", "No hay descuentos registrados todavía.", icono = Icons.Filled.Discount)
                }
            } else {
                itemsIndexed(descuentos, key = { _, d -> d.id }) { index, descuento ->
                    StaggeredListItem(index = index) {
                        TarjetaLista(
                            titulo = descuento.nombre,
                            subtitulo = "${formatearPorcentaje(descuento.porcentaje)}% de descuento",
                            icono = Icons.Filled.Discount,
                            derecha = {
                                EstadoChip(
                                    texto = if (descuento.activo) "Disponible" else "Oculto",
                                    estilo = if (descuento.activo) EstiloChip.EXITO else EstiloChip.ADVERTENCIA,
                                )
                            },
                            acciones = {
                                BotonAccion(
                                    if (descuento.activo) "Ocultar" else "Mostrar",
                                    if (descuento.activo) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    GrisTexto,
                                    onClick = { onAlternarActivo(descuento) },
                                )
                                BotonAccion("Editar", Icons.Filled.Edit, VerdeOscuro, onClick = { onEditar(descuento) })
                                BotonAccion("Eliminar", Icons.Filled.Delete, Rojo, onClick = { onEliminar(descuento) })
                            },
                        )
                    }
                }
            }
        }
        BotonAgregar(texto = "Nuevo descuento", onClick = onNuevo, modifier = Modifier.align(Alignment.BottomEnd))
        ConfirmToastHost(mensaje = mensaje, onDismiss = onConsumirMensaje, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (form != null) {
        AppBottomSheet(onDismiss = onCerrar) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                EncabezadoFormulario(if (form.id == null) "Nuevo descuento" else "Editar descuento")
                AppTextField(form.nombre, onNombre, "Nombre", placeholder = "Descuento por temporada", modifier = Modifier.padding(top = 10.dp))
                AppTextField(form.porcentaje, onPorcentaje, "Porcentaje (%)", teclado = KeyboardType.Decimal, modifier = Modifier.padding(top = 10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Disponible para el vendedor", style = CuerpoMedioStyle, color = Negro)
                    Switch(
                        checked = form.activo,
                        onCheckedChange = onActivo,
                        colors = SwitchDefaults.colors(checkedThumbColor = Blanco, checkedTrackColor = VerdePrimario),
                    )
                }
                if (form.error != null) {
                    MensajeError(form.error, Modifier.padding(top = 10.dp))
                }
                BotonesFormulario(onCancelar = onCerrar, onGuardar = onGuardar)
            }
        }
    }
}
