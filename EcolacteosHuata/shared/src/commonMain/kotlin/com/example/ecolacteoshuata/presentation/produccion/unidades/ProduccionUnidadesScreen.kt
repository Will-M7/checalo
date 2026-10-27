package com.example.ecolacteoshuata.presentation.produccion.unidades

import androidx.compose.material.icons.filled.Scale
import com.example.ecolacteoshuata.core.ui.TarjetaLista
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.RojoFondo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.BotonAccion
import com.example.ecolacteoshuata.core.ui.BotonAgregar
import com.example.ecolacteoshuata.core.ui.ConfirmarDialog
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.domain.model.UnidadMedida

@Composable
fun ProduccionUnidadesScreen(
    unidades: List<UnidadMedida>,
    formVisible: UnidadMedida?,
    error: String?,
    avisoLista: String?,
    onAbrirNuevo: () -> Unit,
    onAbrirEditar: (UnidadMedida) -> Unit,
    onCerrarForm: () -> Unit,
    onGuardar: (UnidadMedida) -> Unit,
    onEliminar: (String) -> Unit,
    onCerrarAviso: () -> Unit,
) {
    var porEliminar by remember { mutableStateOf<UnidadMedida?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (unidades.isEmpty()) {
            EmptyState("Sin unidades", "Registra la primera unidad de medida (kg, L, unid.).", modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
                item { TituloSeccion("Unidades de medida (${unidades.size})") }
                if (avisoLista != null) {
                    item {
                        Text(
                            avisoLista,
                            color = RojoTexto,
                            style = CuerpoMedioStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(RojoFondo, RoundedCornerShape(10.dp))
                                .clickable(onClick = onCerrarAviso)
                                .padding(10.dp),
                        )
                    }
                }
                itemsIndexed(unidades) { index, unidad ->
                    StaggeredListItem(index = index) {
                        TarjetaLista(
                            titulo = unidad.nombre,
                            icono = Icons.Filled.Scale,
                            derecha = { EstadoChip(unidad.simbolo, EstiloChip.EXITO) },
                            acciones = {
                                BotonAccion("Editar", Icons.Filled.Edit, VerdeOscuro, onClick = { onAbrirEditar(unidad) })
                                BotonAccion("Eliminar", Icons.Filled.Delete, RojoTexto, onClick = { porEliminar = unidad })
                            },
                        )
                    }
                }
            }
        }
        BotonAgregar(texto = "Nueva unidad", onClick = onAbrirNuevo, modifier = Modifier.align(Alignment.BottomEnd))
    }

    porEliminar?.let { unidad ->
        ConfirmarDialog(
            titulo = "¿Eliminar esta unidad?",
            mensaje = "Se eliminará \"${unidad.nombre} (${unidad.simbolo})\". Esta acción no se puede deshacer.",
            textoConfirmar = "Eliminar",
            onConfirmar = { onEliminar(unidad.id); porEliminar = null },
            onCancelar = { porEliminar = null },
        )
    }

    if (formVisible != null) {
        AppBottomSheet(onDismiss = onCerrarForm) {
            var nombre by remember(formVisible.id) { mutableStateOf(formVisible.nombre) }
            var simbolo by remember(formVisible.id) { mutableStateOf(formVisible.simbolo) }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                EncabezadoFormulario(if (formVisible.id.isBlank()) "Nueva unidad" else "Editar unidad")
                AppTextField(nombre, { nombre = it }, "Nombre", placeholder = "Kilogramo")
                AppTextField(simbolo, { simbolo = it }, "Símbolo", placeholder = "kg", modifier = Modifier.padding(top = 10.dp))
                if (error != null) {
                    MensajeError(error, Modifier.padding(top = 10.dp))
                }
                BotonesFormulario(onCancelar = onCerrarForm, onGuardar = { onGuardar(formVisible.copy(nombre = nombre, simbolo = simbolo)) })
            }
        }
    }
}
