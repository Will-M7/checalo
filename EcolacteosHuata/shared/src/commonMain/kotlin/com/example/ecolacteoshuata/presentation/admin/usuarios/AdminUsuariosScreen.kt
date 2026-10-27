package com.example.ecolacteoshuata.presentation.admin.usuarios

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
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (usuarios.isEmpty()) {
            EmptyState("Sin usuarios", "Agrega el primer usuario del sistema.", modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
                itemsIndexed(usuarios) { index, usuario ->
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
