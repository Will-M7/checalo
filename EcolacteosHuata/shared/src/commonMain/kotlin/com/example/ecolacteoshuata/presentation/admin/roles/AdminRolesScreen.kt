package com.example.ecolacteoshuata.presentation.admin.roles

import androidx.compose.runtime.*
import com.example.ecolacteoshuata.core.ui.FiltrosListado
import com.example.ecolacteoshuata.core.ui.CampoFiltro
import com.example.ecolacteoshuata.presentation.common.filtros.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.*
import com.example.ecolacteoshuata.core.ui.*
import com.example.ecolacteoshuata.domain.model.RolAdministrable

@Composable
fun AdminRolesScreen(
    state: AdminRolesState, onNuevo: () -> Unit, onEditar: (RolAdministrable) -> Unit,
    onRecargar: () -> Unit, onCerrar: () -> Unit, onNombre: (String) -> Unit, onCodigo: (String) -> Unit,
    onActivo: (Boolean) -> Unit, onPantalla: (String) -> Unit, onGuardar: () -> Unit,
    onConsumirMensaje: () -> Unit, onAsignarUsuarios: (() -> Unit)?,
) {
    var busquedaFiltro by remember { mutableStateOf("") }
    var filtrosLista by remember { mutableStateOf(mapOf<String, String>()) }

    val filtrados = remember(state.catalogo?.roles.orEmpty(), busquedaFiltro, filtrosLista) { state.catalogo?.roles.orEmpty().filter { (it.nombre.contains(busquedaFiltro, true) || it.codigo.contains(busquedaFiltro, true)) && (filtrosLista["estado"].isNullOrEmpty() || it.activo == (filtrosLista["estado"] == "1")) && (filtrosLista["pantalla"].isNullOrEmpty() || filtrosLista["pantalla"] in it.pantallas) } }

    var distribuciones by remember { mutableStateOf(false) }
    if (distribuciones) DistribucionesSheet(onDismiss = { distribuciones = false; onRecargar() })
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
                item(key = "filtros-listado") {
                    FiltrosListado(busquedaFiltro, { busquedaFiltro = it }, listOf(CampoFiltro("estado", "Estado", filtrosLista["estado"].orEmpty(), listOf("1" to "Activos", "0" to "Inactivos")), CampoFiltro("pantalla", "Pantalla asignada", filtrosLista["pantalla"].orEmpty(), state.catalogo?.pantallas.orEmpty().map { it.id to it.nombre })), { k, v -> filtrosLista = filtrosLista + (k to v) }, { busquedaFiltro = ""; filtrosLista = emptyMap() })
                }
                if (filtrados.isEmpty() && state.catalogo?.roles.orEmpty().isNotEmpty()) item { EmptyState("Sin coincidencias", "Prueba otra búsqueda o limpia los filtros.") }
            item {
                Text("Configuración central de Laravel. Requiere conexión.", style = CuerpoStyle, color = GrisTexto)
                BotonAccion("Distribuciones", Icons.Filled.Shield, VerdeOscuro, { distribuciones = true })
                BotonAccion("Actualizar", Icons.Filled.Shield, VerdeOscuro, onRecargar)
                if (onAsignarUsuarios != null) BotonAccion("Asignar roles a usuarios", Icons.Filled.AssignmentInd, VerdeOscuro, onAsignarUsuarios)
                if (state.cargando) Text("Consultando roles…", style = CuerpoStyle, color = GrisTexto)
                if (state.form == null && state.error != null) MensajeError(state.error)
            }
            itemsIndexed(filtrados, key = { _, r -> r.registro_id }) { index, r ->
                StaggeredListItem(index) {
                    TarjetaLista(
                        titulo = r.nombre, subtitulo = r.codigo + if (r.protegido) " · Protegido" else "",
                        icono = Icons.Filled.Shield,
                        derecha = { EstadoChip(if (r.activo) "Activo" else "Inactivo", if (r.activo) EstiloChip.EXITO else EstiloChip.ADVERTENCIA) },
                        acciones = { BotonAccion("Editar", Icons.Filled.Edit, VerdeOscuro, { onEditar(r) }) },
                    ) {
                        val nombres = state.catalogo?.pantallas.orEmpty().associate { it.id to "${it.area}: ${it.nombre}" }
                        Text(r.pantallas.joinToString("\n") { nombres[it] ?: it }.ifBlank { "Sin funciones asignadas" }, style = CuerpoStyle, color = GrisTexto)
                    }
                }
            }
            if (state.catalogo != null) item { BotonAgregar("Nuevo rol", onNuevo) }
        }
        ConfirmToastHost(state.mensaje, onConsumirMensaje, Modifier.align(Alignment.BottomCenter))
    }
    state.form?.let { f ->
        AppBottomSheet(onDismiss = onCerrar) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                EncabezadoFormulario(if (f.id == null) "Nuevo rol" else "Editar rol", "Las asignaciones se guardan en Laravel")
                AppTextField(f.nombre, onNombre, "Nombre", modifier = Modifier.padding(top = 10.dp))
                if (f.id == null) AppTextField(f.codigo, onCodigo, "Identificador", modifier = Modifier.padding(top = 10.dp))
                else Text("Identificador: ${f.codigo}", style = CuerpoStyle, color = GrisTexto, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Rol activo", style = CuerpoMedioStyle, color = Negro)
                    Switch(f.activo, onActivo, colors = SwitchDefaults.colors(checkedThumbColor = Blanco, checkedTrackColor = VerdePrimario))
                }
                state.catalogo?.pantallas.orEmpty().groupBy { it.area }.forEach { (area, pantallas) ->
                    Text(area, style = CuerpoMedioStyle, color = Negro, modifier = Modifier.padding(top = 12.dp))
                    pantallas.forEach { p ->
                        CasillaTexto(p.nombre + if (p.android == null) " · Solo web" else " · Web y Android", p.id in f.pantallas) {
                            if (p.delegable && !state.guardando) onPantalla(p.id)
                        }
                    }
                }
                state.error?.let { MensajeError(it, Modifier.padding(top = 10.dp)) }
                BotonesFormulario(onCancelar = onCerrar, onGuardar = onGuardar, cargando = state.guardando)
            }
        }
    }
}
