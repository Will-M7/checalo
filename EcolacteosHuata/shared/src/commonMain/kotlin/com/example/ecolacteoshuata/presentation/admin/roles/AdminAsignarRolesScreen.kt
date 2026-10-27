package com.example.ecolacteoshuata.presentation.admin.roles

import androidx.compose.runtime.*
import com.example.ecolacteoshuata.core.ui.FiltrosListado
import com.example.ecolacteoshuata.core.ui.CampoFiltro
import com.example.ecolacteoshuata.presentation.common.filtros.*

import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import androidx.compose.material.icons.filled.Person
import com.example.ecolacteoshuata.core.ui.BotonesFormulario
import com.example.ecolacteoshuata.core.ui.EncabezadoFormulario
import com.example.ecolacteoshuata.core.ui.BotonAccion
import com.example.ecolacteoshuata.core.ui.TarjetaLista
import com.example.ecolacteoshuata.core.ui.MensajeError
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.CelesteClaro
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.RojoFondo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.BotonChico
import com.example.ecolacteoshuata.core.ui.CasillaTexto
import com.example.ecolacteoshuata.core.ui.ConfirmToastHost
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.domain.model.Usuario

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminAsignarRolesScreen(
    usuarios: List<Usuario>,
    form: AsignarRolesForm?,
    mensaje: String?,
    onAbrir: (Usuario) -> Unit,
    onCerrar: () -> Unit,
    onAlternarRol: (String) -> Unit,
    onGuardar: () -> Unit,
    onConsumirMensaje: () -> Unit,
    catalogoRoles: List<com.example.ecolacteoshuata.domain.model.RolAsignado> = emptyList(),
) {
    var busquedaFiltro by remember { mutableStateOf("") }
    var filtrosLista by remember { mutableStateOf(mapOf<String, String>()) }
    val opcionesRoles = remember(catalogoRoles, usuarios) { (catalogoRoles.map { it.id to it.nombre } + usuarios.flatMap { it.rolesParaFiltro() }).distinctBy { it.first }.sortedBy { it.second.lowercase() } }
    val filtrados = remember(usuarios, busquedaFiltro, filtrosLista) { filtrarUsuarios(usuarios, busquedaFiltro, filtrosLista["rol"].orEmpty(), filtrosLista["estado"].orEmpty(), filtrosLista["cantidad"].orEmpty()) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (usuarios.isEmpty()) {
            EmptyState("Sin usuarios", "Crea usuarios primero para poder asignarles un rol.", modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
                item(key = "filtros-lista") {
                    FiltrosListado(busquedaFiltro, { busquedaFiltro = it }, listOf(
                        CampoFiltro("rol", "Rol actual", filtrosLista["rol"].orEmpty(), opcionesRoles),
                        CampoFiltro("estado", "Estado", filtrosLista["estado"].orEmpty(), listOf("1" to "Activos", "0" to "Inactivos")),
                        CampoFiltro("cantidad", "Asignaciones", filtrosLista["cantidad"].orEmpty(), listOf("varios" to "Varios roles", "ninguno" to "Sin roles")),
                    ), { k, v -> filtrosLista = filtrosLista + (k to v) }, { busquedaFiltro = ""; filtrosLista = emptyMap() }, "Nombre, usuario o documento...")
                }
                if (filtrados.isEmpty()) item { EmptyState("Sin coincidencias", "Prueba otra búsqueda o limpia los filtros.") }
                item {
                    Text("Un usuario puede tener uno o varios roles a la vez.", style = CuerpoStyle, color = GrisTexto)
                }
                itemsIndexed(filtrados, key = { _, u -> u.id }) { index, usuario ->
                    StaggeredListItem(index = index) {
                        TarjetaLista(
                            titulo = usuario.nombreCompleto,
                            subtitulo = "@${usuario.usuario} · ${usuario.documento}",
                            icono = Icons.Filled.Person,
                            derecha = { EstadoChip(if (usuario.activo) "Activo" else "Inactivo", if (usuario.activo) EstiloChip.EXITO else EstiloChip.ERROR) },
                            acciones = { BotonAccion("Asignar roles", Icons.Filled.AssignmentInd, VerdeOscuro, onClick = { onAbrir(usuario) }) },
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                // El rol principal va en verde, los adicionales en celeste (verde/zinc en Laravel).
                                usuario.autorizacion?.roles.orEmpty().forEach { rol ->
                                    EstadoChip(rol.etiqueta, EstiloChip.EXITO)
                                }
                                if (usuario.tomaAsistencia) {
                                    EstadoChip("Toma asistencia", EstiloChip.ADVERTENCIA)
                                }
                            }
                        }
                    }
                }
            }
        }
        ConfirmToastHost(mensaje = mensaje, onDismiss = onConsumirMensaje, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (form != null) {
        AppBottomSheet(onDismiss = onCerrar) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                EncabezadoFormulario("Asignar roles", form.usuario.nombreCompleto)
                Text(
                    "Selecciona uno o varios roles. Vuelve a marcar un rol ya seleccionado para quitárselo.",
                    style = CuerpoStyle,
                    color = GrisTexto,
                    modifier = Modifier.padding(top = 10.dp),
                )
                form.catalogo.forEach { rol ->
                    CasillaTexto(texto = rol.etiqueta + if (rol.activo) "" else " (Inactivo)", marcado = rol.id in form.roles, onClick = { onAlternarRol(rol.id) })
                }
                if (form.error != null) {
                    MensajeError(form.error, Modifier.padding(top = 10.dp))
                }
                BotonesFormulario(onCancelar = onCerrar, onGuardar = onGuardar, cargando = form.guardando)
            }
        }
    }
}
