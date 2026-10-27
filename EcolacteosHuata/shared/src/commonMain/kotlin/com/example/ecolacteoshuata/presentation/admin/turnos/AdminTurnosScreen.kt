package com.example.ecolacteoshuata.presentation.admin.turnos

import com.example.ecolacteoshuata.core.theme.Rojo
import androidx.compose.material.icons.filled.Schedule
import com.example.ecolacteoshuata.core.ui.BotonesFormulario
import com.example.ecolacteoshuata.core.ui.EncabezadoFormulario
import com.example.ecolacteoshuata.core.ui.BotonAccion
import com.example.ecolacteoshuata.core.ui.TarjetaLista
import com.example.ecolacteoshuata.core.ui.MensajeError
import com.example.ecolacteoshuata.core.ui.TituloSeccion
import com.example.ecolacteoshuata.core.ui.BotonChico
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.RojoFondo
import com.example.ecolacteoshuata.core.theme.RojoTexto
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.domain.model.Turno
import com.example.ecolacteoshuata.domain.model.TurnoProduccion

@Composable
fun AdminTurnosScreen(
    turnosRuta: List<Turno>,
    turnosProduccion: List<TurnoProduccion>,
    formVisible: FormTurno?,
    error: String?,
    onAbrirForm: (FormTurno) -> Unit,
    onCerrarForm: () -> Unit,
    onAgregarRuta: (nombre: String, horaInicio: String, horaFin: String) -> Unit,
    onAgregarProduccion: (nombre: String, horaInicio: String, horaFin: String) -> Unit,
    onEliminarRuta: (String) -> Unit,
    onEliminarProduccion: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
        item {
            TituloSeccion("Turnos de ruta") {
                BotonChico("Agregar turno", onClick = { onAbrirForm(FormTurno.RUTA) })
            }
        }
        if (turnosRuta.isEmpty()) {
            item { EmptyState("Sin turnos de ruta", "Agrega el primer turno de recojo.") }
        } else {
            itemsIndexed(turnosRuta) { index, turno ->
                StaggeredListItem(index = index) {
                    FilaTurno(
                        nombre = turno.nombre,
                        horario = "${turno.horaInicio} - ${turno.horaFin}",
                        onEliminar = { onEliminarRuta(turno.id) },
                    )
                }
            }
        }
        item {
            TituloSeccion("Turnos de producción", modifier = Modifier.padding(top = 12.dp)) {
                BotonChico("Agregar turno", onClick = { onAbrirForm(FormTurno.PRODUCCION) })
            }
        }
        if (turnosProduccion.isEmpty()) {
            item { EmptyState("Sin turnos de producción", "Agrega el primer turno de planta.") }
        } else {
            itemsIndexed(turnosProduccion) { index, turno ->
                StaggeredListItem(index = index) {
                    FilaTurno(
                        nombre = turno.nombre,
                        horario = "${turno.horaInicio} - ${turno.horaFin}",
                        onEliminar = { onEliminarProduccion(turno.id) },
                    )
                }
            }
        }
    }

    if (formVisible != null) {
        AppBottomSheet(onDismiss = onCerrarForm) {
            var nombre by remember { mutableStateOf("") }
            var horaInicio by remember { mutableStateOf("") }
            var horaFin by remember { mutableStateOf("") }
            val esRuta = formVisible == FormTurno.RUTA

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                EncabezadoFormulario(
                    if (esRuta) "Nuevo turno de ruta" else "Nuevo turno de producción",
                    "Usa el formato de 24 horas, por ejemplo 06:00.",
                )
                AppTextField(nombre, { nombre = it }, "Nombre", placeholder = "Mañana", modifier = Modifier.padding(top = 10.dp))
                AppTextField(horaInicio, { horaInicio = it }, "Hora de inicio", placeholder = "06:00", modifier = Modifier.padding(top = 10.dp))
                AppTextField(horaFin, { horaFin = it }, "Hora de fin", placeholder = "08:00", modifier = Modifier.padding(top = 10.dp))
                if (error != null) {
                    MensajeError(error, Modifier.padding(top = 10.dp))
                }
                BotonesFormulario(
                    onCancelar = onCerrarForm,
                    onGuardar = {
                        if (esRuta) onAgregarRuta(nombre, horaInicio, horaFin)
                        else onAgregarProduccion(nombre, horaInicio, horaFin)
                    },
                )
            }
        }
    }
}

@Composable
private fun FilaTurno(nombre: String, horario: String, onEliminar: () -> Unit) {
    TarjetaLista(
        titulo = nombre,
        subtitulo = horario,
        icono = Icons.Filled.Schedule,
        acciones = { BotonAccion("Eliminar", Icons.Filled.Delete, Rojo, onClick = onEliminar) },
    )
}
