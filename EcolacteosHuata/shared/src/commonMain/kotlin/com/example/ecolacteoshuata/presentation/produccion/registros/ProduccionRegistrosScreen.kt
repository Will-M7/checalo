package com.example.ecolacteoshuata.presentation.produccion.registros

import com.example.ecolacteoshuata.core.ui.BotonesFormulario
import com.example.ecolacteoshuata.core.ui.MensajeError
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Publish
import com.example.ecolacteoshuata.core.theme.CelesteTexto
import com.example.ecolacteoshuata.core.ui.BotonAccion
import com.example.ecolacteoshuata.core.ui.ConfirmarDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.Borde
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.RojoFondo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdeClaro
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.AppDropdown
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.BotonAgregar
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.util.formatDdMmmYyyy
import com.example.ecolacteoshuata.domain.model.ProduccionRegistro
import com.example.ecolacteoshuata.domain.model.Receta
import com.example.ecolacteoshuata.domain.model.TurnoProduccion

/** Opción del selector de turno: [id] null = "Sin turno", como en Laravel. */
private data class OpcionTurno(val id: String?, val nombre: String)

@Composable
fun ProduccionRegistrosScreen(
    registros: List<ProduccionRegistro>,
    recetas: List<Receta>,
    turnos: List<TurnoProduccion>,
    formVisible: RegistroFormState?,
    error: String?,
    onAbrirNuevo: () -> Unit,
    onCerrarForm: () -> Unit,
    onAgregarLinea: () -> Unit,
    onQuitarLinea: (Int) -> Unit,
    onRecetaChange: (Int, String) -> Unit,
    onCantidadChange: (Int, String) -> Unit,
    onTurnoChange: (String?) -> Unit,
    onGuardar: () -> Unit,
    onEditar: (ProduccionRegistro) -> Unit,
    onEliminar: (String) -> Unit,
    onPublicar: (String) -> Unit,
    actualizar: ActualizarFormState?,
    onAbrirActualizar: (ProduccionRegistro) -> Unit,
    onCerrarActualizar: () -> Unit,
    onActualizarCantidadChange: (String) -> Unit,
    onActualizarTurnoChange: (String?) -> Unit,
    onGuardarActualizacion: () -> Unit,
) {
    val nombresTurno = turnos.associate { it.id to it.nombre }
    val opcionesTurno = listOf(OpcionTurno(null, "Sin turno")) + turnos.map { OpcionTurno(it.id, "${it.nombre} (${it.horaInicio} - ${it.horaFin})") }
    var porEliminar by remember { mutableStateOf<ProduccionRegistro?>(null) }
    Box(modifier = Modifier.fillMaxSize()) {
        if (registros.isEmpty()) {
            EmptyState("Sin producción registrada", "Registra la primera producción de hoy.", modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
                item { TituloSeccion("Producciones (${registros.size})") }
                itemsIndexed(registros) { index, registro ->
                    StaggeredListItem(index = index) {
                        SectionCard {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(registro.recetaNombre, style = CuerpoMedioStyle.copy(fontSize = 15.sp), color = Negro)
                                    Text(
                                        listOfNotNull(registro.fecha.formatDdMmmYyyy(), registro.turnoId?.let { nombresTurno[it] }).joinToString(" · "),
                                        style = CuerpoStyle,
                                        color = GrisTexto,
                                    )
                                    EstadoChip(
                                        if (registro.publicada) "Publicada" else "Planificada",
                                        if (registro.publicada) EstiloChip.EXITO else EstiloChip.ADVERTENCIA,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Cantidad", style = CuerpoStyle, color = GrisTexto)
                                    Text(registro.cantidadLotes.toString().removeSuffix(".0"), style = TituloPantallaStyle, color = VerdeOscuro)
                                }
                            }
                            // Acciones, como en la tabla de Laravel.
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                BotonAccion("Editar", Icons.Filled.Edit, VerdeOscuro, onClick = { onEditar(registro) })
                                BotonAccion("Eliminar", Icons.Filled.Delete, RojoTexto, onClick = { porEliminar = registro })
                                BotonAccion("Actualizar", Icons.Filled.Autorenew, CelesteTexto, onClick = { onAbrirActualizar(registro) })
                                if (!registro.publicada) {
                                    BotonAccion("Publicar", Icons.Filled.Publish, VerdePrimario, onClick = { onPublicar(registro.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
        BotonAgregar(texto = "Nueva producción", onClick = onAbrirNuevo, modifier = Modifier.align(Alignment.BottomEnd))
    }

    if (formVisible != null) {
        AppBottomSheet(onDismiss = onCerrarForm) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                val editando = formVisible.editandoId != null
                Text(if (editando) "Editar producción" else "Nueva producción", style = TituloPantallaStyle, color = Negro)

                // Recetas a producir + Agregar receta (al editar es una sola receta, como Laravel)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recetas a producir", style = CuerpoMedioStyle, color = Negro, modifier = Modifier.weight(1f))
                    if (!editando) {
                        TextButton(onClick = onAgregarLinea) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = VerdePrimario, modifier = Modifier.size(18.dp))
                            Text("Agregar receta", style = CuerpoMedioStyle, color = VerdePrimario, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }

                formVisible.lineas.forEachIndexed { index, linea ->
                    val sinStock = linea.agotados.isNotEmpty()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .background(VerdeClaro.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .border(1.dp, Borde, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppDropdown(
                                seleccionado = recetas.find { it.id == linea.recetaId },
                                opciones = recetas,
                                etiqueta = "Receta",
                                textoOpcion = { it.nombre },
                                onSeleccionar = { onRecetaChange(index, it.id) },
                                placeholder = "Selecciona...",
                                modifier = Modifier.weight(1f),
                            )
                            AppTextField(
                                linea.cantidad, { onCantidadChange(index, it) }, "Cantidad",
                                teclado = KeyboardType.Decimal,
                                soloLectura = sinStock,
                                modifier = Modifier.width(100.dp).padding(start = 8.dp),
                            )
                            if (formVisible.lineas.size > 1) {
                                IconButton(onClick = { onQuitarLinea(index) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Quitar receta", tint = RojoTexto)
                                }
                            }
                        }
                        if (sinStock) {
                            Text(
                                "Sin stock de ${linea.agotados.joinToString(", ")}. No puedes producir esta receta hasta reponer stock.",
                                style = CuerpoStyle,
                                color = RojoTexto,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }

                if (formVisible.faltantes.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .background(RojoFondo, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = RojoTexto, modifier = Modifier.size(18.dp))
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text("No hay stock suficiente para producir esta cantidad:", style = CuerpoMedioStyle, color = RojoTexto)
                            formVisible.faltantes.forEach { Text("• $it", style = CuerpoStyle, color = RojoTexto) }
                        }
                    }
                }

                // Turno (opcional): turnos de producción, con "Sin turno" por defecto.
                AppDropdown(
                    seleccionado = opcionesTurno.firstOrNull { it.id == formVisible.turnoId },
                    opciones = opcionesTurno,
                    etiqueta = "Turno (opcional)",
                    textoOpcion = { it.nombre },
                    onSeleccionar = { onTurnoChange(it.id) },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )

                if (error != null) {
                    MensajeError(error, Modifier.padding(top = 10.dp))
                }

                BotonesFormulario(onCancelar = onCerrarForm, onGuardar = onGuardar,
                        habilitado = formVisible.faltantes.isEmpty(), cargando = formVisible.guardando)
            }
        }
    }

    porEliminar?.let { registro ->
        ConfirmarDialog(
            titulo = "¿Eliminar esta producción?",
            mensaje = "Se restituirá el stock de insumos consumidos por \"${registro.recetaNombre}\". Esta acción no se puede deshacer.",
            textoConfirmar = "Eliminar",
            onConfirmar = { onEliminar(registro.id); porEliminar = null },
            onCancelar = { porEliminar = null },
        )
    }

    if (actualizar != null) {
        AppBottomSheet(onDismiss = onCerrarActualizar) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Actualizar producción", style = TituloPantallaStyle, color = Negro)
                AppTextField(
                    actualizar.registro.recetaNombre, {}, "Receta",
                    soloLectura = true, modifier = Modifier.padding(top = 12.dp),
                )
                AppTextField(
                    actualizar.registro.cantidadLotes.toString().removeSuffix(".0"), {}, "Cantidad actual",
                    soloLectura = true, modifier = Modifier.padding(top = 10.dp),
                )
                AppTextField(
                    actualizar.cantidad, onActualizarCantidadChange, "Cantidad a agregar",
                    teclado = KeyboardType.Decimal, modifier = Modifier.padding(top = 10.dp),
                )
                if (actualizar.faltantes.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .background(RojoFondo, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = RojoTexto, modifier = Modifier.size(18.dp))
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text("No hay stock suficiente para agregar esta cantidad:", style = CuerpoMedioStyle, color = RojoTexto)
                            actualizar.faltantes.forEach { Text("• $it", style = CuerpoStyle, color = RojoTexto) }
                        }
                    }
                }
                AppDropdown(
                    seleccionado = opcionesTurno.firstOrNull { it.id == actualizar.turnoId },
                    opciones = opcionesTurno,
                    etiqueta = "Turno",
                    textoOpcion = { it.nombre },
                    onSeleccionar = { onActualizarTurnoChange(it.id) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                if (error != null) {
                    MensajeError(error, Modifier.padding(top = 10.dp))
                }
                BotonesFormulario(onCancelar = onCerrarActualizar, onGuardar = onGuardarActualizacion,
                        habilitado = actualizar.faltantes.isEmpty())
            }
        }
    }
}
