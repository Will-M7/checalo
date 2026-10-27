package com.example.ecolacteoshuata.presentation.planta.comunicados

import com.example.ecolacteoshuata.core.ui.MensajeError
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Campaign
import com.example.ecolacteoshuata.core.theme.Rojo
import com.example.ecolacteoshuata.core.ui.BotonAccion
import com.example.ecolacteoshuata.core.ui.TarjetaLista
import com.example.ecolacteoshuata.core.ui.fondoOpcion
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.ui.BotonAgregar
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.RojoFondo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.util.formatDdMmmYyyy
import com.example.ecolacteoshuata.domain.model.Comunicado
import com.example.ecolacteoshuata.domain.model.TipoComunicado
import com.example.ecolacteoshuata.domain.model.Rol
import com.example.ecolacteoshuata.core.ui.AppDropdown
import com.example.ecolacteoshuata.core.ui.CasillaTexto
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun PlantaComunicadosScreen(
    comunicados: List<Comunicado>,
    mostrarForm: Boolean,
    error: String?,
    onAbrirForm: () -> Unit,
    onCerrarForm: () -> Unit,
    onPublicar: (TipoComunicado, String, String, Set<Rol>?) -> Unit,
    onEliminar: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (comunicados.isEmpty()) {
            EmptyState("Sin comunicados", "Publica el primer aviso para los proveedores.", modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
                itemsIndexed(comunicados) { index, comunicado ->
                    StaggeredListItem(index = index) {
                        val esAsesoria = comunicado.tipo == TipoComunicado.ASESORAMIENTO
                        TarjetaLista(
                            titulo = comunicado.titulo,
                            subtitulo = comunicado.fecha.formatDdMmmYyyy(),
                            icono = if (esAsesoria) Icons.Filled.Lightbulb else Icons.Filled.Campaign,
                            derecha = { EstadoChip(if (esAsesoria) "Asesoría" else "Aviso", if (esAsesoria) EstiloChip.INFO else EstiloChip.EXITO) },
                            acciones = { BotonAccion("Eliminar", Icons.Filled.Delete, Rojo, onClick = { onEliminar(comunicado.id) }) },
                        ) {
                            Text(comunicado.descripcion, style = CuerpoStyle, color = Negro)
                            Text(
                                "Para: " + (comunicado.roles?.let { roles -> Rol.ordenLaravel.filter { it in roles }.joinToString(", ") { it.etiqueta } } ?: "Todos los roles"),
                                style = CuerpoStyle,
                                color = GrisTexto,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        BotonAgregar(texto = "Publicar comunicado", onClick = onAbrirForm, modifier = Modifier.align(Alignment.BottomEnd))
    }

    if (mostrarForm) {
        AppBottomSheet(onDismiss = onCerrarForm) {
            var tipo by remember { mutableStateOf(TipoComunicado.COMUNICADO) }
            var titulo by remember { mutableStateOf("") }
            var descripcion by remember { mutableStateOf("") }
            var paraTodos by remember { mutableStateOf(true) }
            var rolesDestino by remember { mutableStateOf(emptySet<Rol>()) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                TituloSeccion("Comunicados y asesoramiento")
                AppDropdown(
                    seleccionado = tipo,
                    opciones = TipoComunicado.entries,
                    etiqueta = "Tipo",
                    textoOpcion = { if (it == TipoComunicado.COMUNICADO) "Comunicado" else "Asesoramiento" },
                    onSeleccionar = { tipo = it },
                    modifier = Modifier.padding(top = 10.dp),
                )
                AppTextField(titulo, { titulo = it }, "Título", placeholder = "Cambio de horario de recojo", modifier = Modifier.padding(top = 10.dp))
                AppTextField(descripcion, { descripcion = it }, "Descripción", lineasMax = 4, modifier = Modifier.padding(top = 10.dp))
                CasillaTexto(texto = "Todos los roles", marcado = paraTodos, onClick = { paraTodos = !paraTodos })
                if (!paraTodos) {
                    Text("Selecciona uno o varios roles destinatarios:", style = CuerpoStyle, color = GrisTexto, modifier = Modifier.padding(start = 12.dp))
                    Rol.ordenLaravel.forEach { rol ->
                        CasillaTexto(
                            texto = rol.etiqueta,
                            marcado = rol in rolesDestino,
                            onClick = { rolesDestino = if (rol in rolesDestino) rolesDestino - rol else rolesDestino + rol },
                        )
                    }
                }
                if (error != null) {
                    MensajeError(error, Modifier.padding(top = 10.dp))
                }
                PrimaryButton(
                    texto = "Publicar",
                    onClick = { onPublicar(tipo, titulo, descripcion, if (paraTodos) null else rolesDestino) },
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
            }
        }
    }
}
