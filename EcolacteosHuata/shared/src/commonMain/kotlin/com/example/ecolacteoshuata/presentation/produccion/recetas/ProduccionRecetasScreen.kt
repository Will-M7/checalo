package com.example.ecolacteoshuata.presentation.produccion.recetas

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.ecolacteoshuata.core.theme.VerdeClaro
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.AppDropdown
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.BotonAgregar
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.domain.model.Insumo
import com.example.ecolacteoshuata.domain.model.Receta
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.ui.ConfirmarDialog
import com.example.ecolacteoshuata.core.util.formatMoney
import com.example.ecolacteoshuata.presentation.produccion.dashboard.formatoCantidad

/** Botón redondo solo con icono, para las acciones del encabezado de una tarjeta. */
@Composable
private fun IconoAccion(icono: ImageVector, descripcion: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = descripcion, tint = color, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProduccionRecetasScreen(
    recetas: List<Receta>,
    insumos: List<Insumo>,
    formVisible: RecetaFormState?,
    error: String?,
    onAbrirNuevo: () -> Unit,
    onAbrirEditar: (Receta) -> Unit,
    onCerrarForm: () -> Unit,
    onNombreChange: (String) -> Unit,
    onAgregarInsumo: (String, Double) -> Unit,
    onQuitarInsumo: (Int) -> Unit,
    onGuardar: () -> Unit,
    onEliminar: (String) -> Unit,
) {
    val simbolos = insumos.associate { it.id to it.unidadMedidaSimbolo }
    val costos = insumos.associate { it.id to it.costoUnitario }
    var porEliminar by remember { mutableStateOf<Receta?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (recetas.isEmpty()) {
            EmptyState("Sin recetas", "Crea la primera receta con sus insumos.", modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
                item { TituloSeccion("Recetas (${recetas.size})") }
                itemsIndexed(recetas) { index, receta ->
                    // Costo por unidad = Σ cantidad × costo unitario de cada insumo (como Laravel).
                    val costo = receta.detalle.sumOf { it.cantidad * (costos[it.insumoId] ?: 0.0) }
                    StaggeredListItem(index = index) {
                        SectionCard {
                            // Encabezado: nombre y costo, con las acciones como iconos a la derecha.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(receta.nombre, style = CuerpoMedioStyle.copy(fontSize = 15.sp), color = Negro)
                                    Text("Costo por unidad: ${formatMoney(costo)}", style = CuerpoStyle, color = GrisTexto)
                                }
                                IconoAccion(Icons.Filled.Edit, "Editar receta", VerdeOscuro) { onAbrirEditar(receta) }
                                IconoAccion(Icons.Filled.Delete, "Eliminar receta", RojoTexto, Modifier.padding(start = 6.dp)) { porEliminar = receta }
                            }
                            // Insumos como etiquetas.
                            Text("Insumos (${receta.detalle.size})", style = CuerpoStyle, color = GrisTexto, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                            if (receta.detalle.isEmpty()) {
                                Text("Sin insumos", style = CuerpoStyle, color = GrisTexto)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                receta.detalle.forEach {
                                    Text(
                                        "${it.insumoNombre} · ${formatoCantidad(it.cantidad)} ${simbolos[it.insumoId].orEmpty()}".trim(),
                                        style = CuerpoMedioStyle,
                                        color = VerdeOscuro,
                                        modifier = Modifier
                                            .background(VerdeClaro, RoundedCornerShape(999.dp))
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        BotonAgregar(texto = "Nueva receta", onClick = onAbrirNuevo, modifier = Modifier.align(Alignment.BottomEnd))
    }

    porEliminar?.let { receta ->
        ConfirmarDialog(
            titulo = "¿Eliminar esta receta?",
            mensaje = "Se eliminará \"${receta.nombre}\" con sus insumos. Esta acción no se puede deshacer.",
            textoConfirmar = "Eliminar",
            onConfirmar = { onEliminar(receta.id); porEliminar = null },
            onCancelar = { porEliminar = null },
        )
    }

    if (formVisible != null) {
        AppBottomSheet(onDismiss = onCerrarForm) {
            var insumoId by remember { mutableStateOf<String?>(null) }
            var cantidadTexto by remember { mutableStateOf("") }

            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                EncabezadoFormulario(if (formVisible.id.isBlank()) "Nueva receta" else "Editar receta")
                AppTextField(formVisible.nombre, onNombreChange, "Nombre de la receta")

                Text("Insumos de la receta", style = CuerpoMedioStyle, color = Negro, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                if (formVisible.detalle.isEmpty()) {
                    Text("Agrega al menos un insumo.", style = CuerpoStyle, color = GrisTexto)
                }
                formVisible.detalle.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(VerdeClaro, RoundedCornerShape(10.dp))
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.insumoNombre, style = CuerpoMedioStyle, color = Negro, modifier = Modifier.weight(1f))
                        Text("${item.cantidad} ${simbolos[item.insumoId].orEmpty()}", style = CuerpoStyle, color = GrisTexto)
                        IconButton(onClick = { onQuitarInsumo(index) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Quitar", tint = RojoTexto)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppDropdown(
                        seleccionado = insumos.find { it.id == insumoId },
                        opciones = insumos,
                        etiqueta = "Insumo",
                        textoOpcion = { it.nombre },
                        onSeleccionar = { insumoId = it.id },
                        placeholder = "Selecciona...",
                        modifier = Modifier.weight(1f),
                    )
                    AppTextField(
                        cantidadTexto, { cantidadTexto = it }, "Cant.",
                        teclado = KeyboardType.Decimal, modifier = Modifier.width(90.dp).padding(start = 8.dp),
                    )
                    IconButton(onClick = {
                        val id = insumoId
                        val cant = cantidadTexto.replace(',', '.').toDoubleOrNull()
                        if (id != null && cant != null && cant > 0) {
                            onAgregarInsumo(id, cant)
                            cantidadTexto = ""
                        }
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Agregar insumo", tint = VerdePrimario)
                    }
                }

                if (error != null) {
                    MensajeError(error, Modifier.padding(top = 10.dp))
                }
                BotonesFormulario(onCancelar = onCerrarForm, onGuardar = onGuardar)
            }
        }
    }
}
