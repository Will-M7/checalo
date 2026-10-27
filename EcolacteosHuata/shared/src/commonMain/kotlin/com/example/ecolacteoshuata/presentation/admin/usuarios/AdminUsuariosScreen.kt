package com.example.ecolacteoshuata.presentation.admin.usuarios

import androidx.compose.runtime.*
import com.example.ecolacteoshuata.core.ui.FiltrosListado
import com.example.ecolacteoshuata.core.ui.CampoFiltro
import com.example.ecolacteoshuata.presentation.common.filtros.*

import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.theme.Rojo
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Edit
import com.example.ecolacteoshuata.core.ui.BotonAccion
import com.example.ecolacteoshuata.core.ui.TarjetaLista
import com.example.ecolacteoshuata.core.ui.BotonAgregar
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.domain.model.Usuario

@Composable
fun AdminUsuariosScreen(
    usuarios: List<Usuario>,
    onAgregar: () -> Unit,
    onEditar: (Usuario) -> Unit,
    onEliminar: (Usuario) -> Unit,
    catalogoRoles: List<com.example.ecolacteoshuata.domain.model.RolAsignado> = emptyList(),
) {
    var busquedaFiltro by remember { mutableStateOf("") }
    var filtrosLista by remember { mutableStateOf(mapOf<String, String>()) }
    val opcionesRoles = remember(catalogoRoles, usuarios) { (catalogoRoles.map { it.id to it.nombre } + usuarios.flatMap { it.rolesParaFiltro() }).distinctBy { it.first }.sortedBy { it.second.lowercase() } }
    val filtrados = remember(usuarios, busquedaFiltro, filtrosLista) { filtrarUsuarios(usuarios, busquedaFiltro, filtrosLista["rol"].orEmpty(), filtrosLista["estado"].orEmpty(), filtrosLista["cantidad"].orEmpty()) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (usuarios.isEmpty()) {
            EmptyState("Sin usuarios", "Agrega el primer usuario del sistema.", modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
                item(key = "disponibilidad-web") { com.example.ecolacteoshuata.presentation.vendedor.DisponibilidadWebControl(admin = true) }
                item(key = "filtros-lista") {
                    FiltrosListado(busquedaFiltro, { busquedaFiltro = it }, listOf(
                        CampoFiltro("rol", "Rol actual", filtrosLista["rol"].orEmpty(), opcionesRoles),
                        CampoFiltro("estado", "Estado", filtrosLista["estado"].orEmpty(), listOf("1" to "Activos", "0" to "Inactivos")),
                        CampoFiltro("cantidad", "Asignaciones", filtrosLista["cantidad"].orEmpty(), listOf("varios" to "Varios roles", "ninguno" to "Sin roles")),
                    ), { k, v -> filtrosLista = filtrosLista + (k to v) }, { busquedaFiltro = ""; filtrosLista = emptyMap() }, "Nombre, usuario o documento...")
                }
                if (filtrados.isEmpty()) item { EmptyState("Sin coincidencias", "Prueba otra búsqueda o limpia los filtros.") }
                itemsIndexed(filtrados, key = { _, u -> u.id }) { index, usuario ->
                    StaggeredListItem(index = index) {
                        TarjetaLista(
                            titulo = usuario.nombreCompleto,
                            subtitulo = "@${usuario.usuario} · ${usuario.documento}",
                            icono = Icons.Filled.Person,
                            derecha = { EstadoChip(if (usuario.activo) "Activo" else "Inactivo", if (usuario.activo) EstiloChip.EXITO else EstiloChip.ERROR) },
                            acciones = {
                                BotonAccion("Editar", Icons.Filled.Edit, VerdeOscuro, onClick = { onEditar(usuario) })
                                BotonAccion("Eliminar", Icons.Filled.Delete, Rojo, onClick = { onEliminar(usuario) })
                            },
                        ) {
                            Text("Roles: " + (usuario.autorizacion?.roles?.joinToString { it.nombre } ?: usuario.rol?.etiqueta.orEmpty()), style = CuerpoStyle, color = GrisTexto)
                        }
                    }
                }
            }
        }
        BotonAgregar(texto = "Nuevo usuario", onClick = onAgregar, modifier = Modifier.align(Alignment.BottomEnd))
    }
}
