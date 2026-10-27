package com.example.ecolacteoshuata.presentation.admin.rutas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.EtiquetaMayusculaStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.Rojo
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.BotonAccion
import com.example.ecolacteoshuata.core.ui.BotonChico
import com.example.ecolacteoshuata.core.ui.BotonesFormulario
import com.example.ecolacteoshuata.core.ui.ConfirmToastHost
import com.example.ecolacteoshuata.core.ui.DatoDialogo
import com.example.ecolacteoshuata.core.ui.DialogoConfirmacion
import com.example.ecolacteoshuata.core.ui.TonoDialogo
import com.example.ecolacteoshuata.core.ui.DividerLine
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EncabezadoFormulario
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.LocalTemaRol
import com.example.ecolacteoshuata.core.ui.MensajeError
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.ui.TarjetaLista
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.ui.fondoOpcion
import com.example.ecolacteoshuata.core.ui.textoOpcion
import com.example.ecolacteoshuata.domain.model.Turno
import com.example.ecolacteoshuata.presentation.common.mapa.MapaRutaViewModel
import com.example.ecolacteoshuata.presentation.common.mapa.RecorridoRuta
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Página "Rutas" de planta y administrador, igual que planta.rutas en Laravel: la lista de rutas
 * (crear, editar, eliminar, ver y asignar proveedores) y debajo el "Recorrido del camioncito".
 */
@Composable
fun RutasHost() {
    val vm = koinViewModel<AdminRutasViewModel>()
    val state by vm.state.collectAsState()
    val mapaVm = koinViewModel<MapaRutaViewModel>(key = "mapa-true-null") { parametersOf(true, null) }
    val mapa by mapaVm.state.collectAsState()

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ListaRutas(
                state = state,
                onNueva = vm::nuevaRuta,
                onVerDetalle = vm::verDetalle,
                onEditar = { vm.editarRuta(it.ruta) },
                onEliminar = { vm.pedirEliminar(it.ruta.id) },
            )

            TituloSeccion("Recorrido del camioncito", modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
            Text(
                "Traza el camino de cada ruta, marca la ubicación de los proveedores y ordena las paradas.",
                style = CuerpoStyle,
                color = GrisTexto,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (mapa.rutas.isEmpty()) {
                Text("Crea una ruta para trazar su recorrido.", style = CuerpoStyle, color = GrisTexto, modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                RecorridoRuta(
                    state = mapa,
                    onSeleccionarRuta = mapaVm::seleccionarRuta,
                    onEnfocarParada = mapaVm::enfocarParada,
                    onTocarMapa = mapaVm::tocarMapa,
                    onAlternarUbicar = mapaVm::alternarUbicar,
                    onEmpezarDibujo = mapaVm::empezarDibujo,
                    onDeshacerPunto = mapaVm::deshacerPunto,
                    onGuardarDibujo = mapaVm::guardarDibujo,
                    onCancelarModo = mapaVm::cancelarModo,
                    onTrazarPorParadas = mapaVm::trazarPorParadas,
                    onLimpiarTrazo = mapaVm::limpiarTrazo,
                    onMoverParada = mapaVm::moverParada,
                    onBuscar = mapaVm::buscar,
                )
            }
        }

        val mensaje = state.mensaje ?: mapa.mensaje
        ConfirmToastHost(
            mensaje = mensaje,
            onDismiss = { if (state.mensaje != null) vm.consumirMensaje() else mapaVm.consumirMensaje() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    state.detalle?.let { fila ->
        DetalleRutaSheet(
            fila = fila,
            state = state,
            onAsignar = { vm.abrirAsignar(fila.ruta.id) },
            onDismiss = { vm.verDetalle(null) },
        )
    }
    state.asignar?.let { fila ->
        AsignarProveedoresSheet(
            fila = fila,
            state = state,
            onBuscar = vm::buscarAsignar,
            onAlternar = vm::alternarProveedor,
            onDismiss = { vm.abrirAsignar(null) },
        )
    }
    state.form?.let { form ->
        RutaFormSheet(
            form = form,
            state = state,
            onCambia = vm::onFormCambia,
            onGuardar = vm::guardarRuta,
            onDismiss = vm::cerrarForm,
        )
    }
    state.eliminar?.let {
        DialogoConfirmacion(
            titulo = "¿Eliminar esta ruta?",
            mensaje = "Se quitarán sus proveedores asignados. Esta acción no se puede deshacer.",
            icono = Icons.Filled.Delete,
            tono = TonoDialogo.PELIGRO,
            textoConfirmar = "Eliminar",
            onConfirmar = vm::eliminarRuta,
            onCancelar = { vm.pedirEliminar(null) },
            datos = listOf(
                DatoDialogo("Ruta", it.ruta.nombre, Icons.Filled.Route),
                DatoDialogo("Proveedores", it.ruta.proveedorIds.size.toString(), Icons.Filled.Agriculture),
            ),
        )
    }
}

@Composable
private fun ListaRutas(
    state: AdminRutasUiState,
    onNueva: () -> Unit,
    onVerDetalle: (String) -> Unit,
    onEditar: (RutaFila) -> Unit,
    onEliminar: (RutaFila) -> Unit,
) {
    val tema = LocalTemaRol.current
    TituloSeccion("Rutas", modifier = Modifier.padding(horizontal = 16.dp)) {
        BotonChico("Nueva ruta", onClick = onNueva, icono = Icons.Filled.Add)
    }
    Text(
        "Acopiadores con sus proveedores asignados y si ya recogieron hoy.",
        style = CuerpoStyle,
        color = GrisTexto,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    if (state.rutas.isEmpty()) {
        EmptyState("Sin rutas", "No hay rutas creadas todavía. Usa \"Nueva ruta\" para crear la primera.", icono = Icons.Filled.Route)
        return
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.rutas.forEachIndexed { index, fila ->
            StaggeredListItem(index = index) {
                TarjetaLista(
                    titulo = fila.ruta.nombre,
                    subtitulo = fila.acopiador,
                    icono = Icons.Filled.Route,
                    derecha = {
                        EstadoChip(if (fila.ruta.activa) "Activa" else "Inactiva", if (fila.ruta.activa) EstiloChip.EXITO else EstiloChip.ADVERTENCIA)
                    },
                    contenido = {
                        ChipsTurnos(fila.turnos)
                        Text(
                            "${fila.ruta.proveedorIds.size} proveedores",
                            style = CuerpoMedioStyle,
                            color = tema.acento,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    },
                    acciones = {
                        BotonAccion("Ver proveedores", Icons.Filled.TableRows, tema.acento, onClick = { onVerDetalle(fila.ruta.id) })
                        BotonAccion("Editar", Icons.Filled.Edit, Negro, onClick = { onEditar(fila) })
                        BotonAccion("Eliminar", Icons.Filled.Delete, Rojo, onClick = { onEliminar(fila) })
                    },
                )
            }
        }
    }
}

@Composable
private fun ChipsTurnos(turnos: List<Turno>) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (turnos.isEmpty()) {
            Text("Sin turno", style = CuerpoStyle, color = GrisTexto)
        }
        turnos.forEach { EstadoChip("${it.nombre}: ${it.horaInicio} - ${it.horaFin}", EstiloChip.ADVERTENCIA) }
    }
}

@Composable
private fun DetalleRutaSheet(fila: RutaFila, state: AdminRutasUiState, onAsignar: () -> Unit, onDismiss: () -> Unit) {
    AppBottomSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            EncabezadoFormulario(fila.ruta.nombre, fila.acopiador)
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                BotonChico("Asignar proveedores", onClick = onAsignar, icono = Icons.Filled.PersonAdd)
            }
            ChipsTurnos(fila.turnos)
            if (state.proveedoresDetalle.isEmpty()) {
                EmptyState("Sin proveedores", "Esta ruta no tiene proveedores asignados.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(top = 8.dp)) {
                    items(state.proveedoresDetalle, key = { it.id }) { p ->
                        val recogido = p.id in state.recogidosHoy
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.nombreCompleto, style = CuerpoMedioStyle, color = Negro, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(p.fincaUbicacion.ifBlank { "Sin registrar" }, style = CuerpoStyle, color = GrisTexto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            EstadoChip(if (recogido) "Recogido" else "No recogido", if (recogido) EstiloChip.EXITO else EstiloChip.ERROR)
                        }
                        DividerLine()
                    }
                }
            }
        }
    }
}

@Composable
private fun AsignarProveedoresSheet(
    fila: RutaFila,
    state: AdminRutasUiState,
    onBuscar: (String) -> Unit,
    onAlternar: (CandidatoRuta) -> Unit,
    onDismiss: () -> Unit,
) {
    val tema = LocalTemaRol.current
    AppBottomSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            EncabezadoFormulario("Asignar proveedores", "${fila.ruta.nombre} · ${fila.acopiador}")
            AppTextField(state.busquedaAsignar, onBuscar, "Buscar proveedor por nombre o apellido...")
            if (state.candidatos.isEmpty()) {
                Text(
                    "Este acopiador no tiene proveedores registrados.",
                    style = CuerpoStyle,
                    color = GrisTexto,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(top = 8.dp)) {
                items(state.candidatos, key = { it.proveedor.id }) { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(if (c.asignado) tema.acento.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { onAlternar(c) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.proveedor.nombreCompleto, style = CuerpoMedioStyle, color = Negro, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.proveedor.fincaUbicacion.ifBlank { "Sin ubicación" }, style = CuerpoStyle, color = GrisTexto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (c.deOtroAcopiador) {
                                Text("Sin ruta · ${c.acopiadorActual ?: "Sin acopiador"}", style = EtiquetaMayusculaStyle, color = com.example.ecolacteoshuata.core.theme.AmbarTexto)
                            }
                        }
                        if (c.asignado) {
                            BotonAccion("Quitar", Icons.Filled.Close, Rojo, onClick = { onAlternar(c) })
                        } else {
                            BotonAccion("Asignar", Icons.Filled.Add, tema.acento, onClick = { onAlternar(c) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RutaFormSheet(
    form: RutaForm,
    state: AdminRutasUiState,
    onCambia: (RutaForm) -> Unit,
    onGuardar: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            EncabezadoFormulario(if (form.id.isBlank()) "Nueva ruta" else "Editar ruta")
            AppTextField(form.nombre, { onCambia(form.copy(nombre = it)) }, "Nombre de la ruta", placeholder = "Ej: Ruta Huata Centro")

            Text("Acopiador", style = EtiquetaMayusculaStyle, color = GrisTexto, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            if (state.acopiadores.isEmpty()) {
                Text("Registra acopiadores desde el módulo de usuarios.", style = CuerpoStyle, color = GrisTexto)
            }
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.acopiadores.forEach { a ->
                    val activo = a.id == form.acopiadorId
                    Text(
                        a.nombreCompleto,
                        style = EtiquetaMayusculaStyle,
                        color = textoOpcion(activo),
                        modifier = Modifier
                            .background(fondoOpcion(activo), RoundedCornerShape(999.dp))
                            .clickable { onCambia(form.copy(acopiadorId = a.id)) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ruta activa", style = CuerpoMedioStyle, color = Negro, modifier = Modifier.weight(1f))
                Switch(checked = form.activa, onCheckedChange = { onCambia(form.copy(activa = it)) })
            }

            form.error?.let { MensajeError(it, modifier = Modifier.padding(top = 10.dp)) }
            BotonesFormulario(onCancelar = onDismiss, onGuardar = onGuardar)
        }
    }
}
