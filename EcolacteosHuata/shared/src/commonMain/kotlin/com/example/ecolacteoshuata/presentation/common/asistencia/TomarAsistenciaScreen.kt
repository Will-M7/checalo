package com.example.ecolacteoshuata.presentation.common.asistencia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.Ambar
import com.example.ecolacteoshuata.core.theme.AmbarFondo
import com.example.ecolacteoshuata.core.theme.AmbarTexto
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.Borde
import com.example.ecolacteoshuata.core.theme.CelesteClaro
import com.example.ecolacteoshuata.core.theme.CelesteTexto
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.EtiquetaMayusculaStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.Rojo
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdeClaro
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.BotonAccion
import com.example.ecolacteoshuata.core.ui.BotonChico
import com.example.ecolacteoshuata.core.ui.BotonesFormulario
import com.example.ecolacteoshuata.core.ui.ConfirmToastHost
import com.example.ecolacteoshuata.core.ui.ConfirmarDialog
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EncabezadoFormulario
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import com.example.ecolacteoshuata.core.ui.LocalTemaRol
import com.example.ecolacteoshuata.core.ui.MensajeError
import com.example.ecolacteoshuata.core.ui.PrimaryButton
import com.example.ecolacteoshuata.core.ui.SecondaryButton
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.ui.TarjetaLista
import com.example.ecolacteoshuata.core.ui.TipoAviso
import com.example.ecolacteoshuata.core.ui.degradado
import com.example.ecolacteoshuata.core.ui.fondoOpcion
import com.example.ecolacteoshuata.core.ui.textoOpcion
import com.example.ecolacteoshuata.domain.model.EstadoAsistencia
import com.example.ecolacteoshuata.domain.model.EstadoJustificacion
import com.example.ecolacteoshuata.domain.model.HorarioAsistencia
import com.example.ecolacteoshuata.domain.model.Usuario
import com.example.ecolacteoshuata.domain.repository.UsuarioRepository
import com.example.ecolacteoshuata.presentation.common.ModuloItem
import androidx.compose.runtime.remember
import kotlinx.datetime.LocalDate
import org.koin.compose.koinInject
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val MESES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)
private val DIAS_SEMANA = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
private val DIAS_CORTOS = listOf("Lu", "Ma", "Mi", "Ju", "Vi", "Sá", "Do")

internal fun LocalDate.mesAnio(): String = "${MESES[month.number - 1].replaceFirstChar { it.uppercase() }} $year"
internal fun LocalDate.diaSemana(): String = DIAS_SEMANA[dayOfWeek.isoDayNumber - 1]
internal fun LocalDate.largo(): String = "$day de ${MESES[month.number - 1]} de $year"
private fun LocalDate.corto(): String = "${day.toString().padStart(2, '0')}/${month.number.toString().padStart(2, '0')}/$year"

/** Color e ícono de cada estado, como los badges de Laravel (verde, ámbar, rojo). */
internal fun EstadoAsistencia.color(): Color = when (this) {
    EstadoAsistencia.PRESENTE -> VerdePrimario
    EstadoAsistencia.TARDANZA -> Ambar
    EstadoAsistencia.FALTA -> Rojo
}

internal fun EstadoAsistencia.icono(): ImageVector = when (this) {
    EstadoAsistencia.PRESENTE -> Icons.Filled.CheckCircle
    EstadoAsistencia.TARDANZA -> Icons.Filled.Schedule
    EstadoAsistencia.FALTA -> Icons.Filled.Cancel
}

internal fun EstadoAsistencia.estiloChip(): EstiloChip = when (this) {
    EstadoAsistencia.PRESENTE -> EstiloChip.EXITO
    EstadoAsistencia.TARDANZA -> EstiloChip.ADVERTENCIA
    EstadoAsistencia.FALTA -> EstiloChip.ERROR
}

internal fun EstadoJustificacion.estiloChip(): EstiloChip = when (this) {
    EstadoJustificacion.PENDIENTE -> EstiloChip.ADVERTENCIA
    EstadoJustificacion.ACEPTADA -> EstiloChip.EXITO
    EstadoJustificacion.RECHAZADA -> EstiloChip.ERROR
}

/**
 * Si el usuario puede tomar asistencia, siguiendo lo que trae el sync: si el administrador se lo
 * asigna o se lo quita en Laravel, el módulo aparece o desaparece sin volver a iniciar sesión.
 */
@Composable
fun rememberPuedeTomarAsistencia(usuario: Usuario): Boolean {
    val repository = koinInject<UsuarioRepository>()
    val flujo = remember(usuario.id) { repository.observeById(usuario.id) }
    val actual by flujo.collectAsState(initial = usuario)
    return (actual ?: usuario).puedeTomarAsistencia
}

/** Módulo "Tomar asistencia" del panel y del menú "Más" (va antes de Inventario / Comunicados, como en Laravel). */
fun moduloTomarAsistencia(onClick: () -> Unit) =
    ModuloItem("Tomar asistencia", Icons.Filled.EventAvailable, "Asistencia, justificaciones y acopiadores", onClick = onClick)

/**
 * "Tomar asistencia" (mismo módulo que en Laravel) para el administrador o quien él asigne.
 * Se usa desde el panel de cualquier rol.
 */
@Composable
fun TomarAsistenciaHost(usuario: Usuario) {
    val vm = koinViewModel<TomarAsistenciaViewModel>(key = "asistencia-${usuario.id}") { parametersOf(usuario.id) }
    val state by vm.state.collectAsState()
    TomarAsistenciaScreen(state = state, vm = vm)
}

@Composable
private fun TomarAsistenciaScreen(state: TomarAsistenciaUiState, vm: TomarAsistenciaViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
            item(key = "pestanas") {
                Column {
                    Pestanas(actual = state.pestana, pendientes = state.pendientes, onSeleccionar = vm::seleccionarPestana)
                    Text(state.pestana.ayuda, style = CuerpoStyle, color = GrisTexto, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
                }
            }
            when (state.pestana) {
                PestanaAsistencia.ASISTENCIA -> pestanaAsistencia(state, vm)
                PestanaAsistencia.JUSTIFICACIONES -> pestanaJustificaciones(state, vm)
                PestanaAsistencia.ACOPIADORES -> pestanaAcopiadores(state, vm)
            }
        }

        if (state.pestana == PestanaAsistencia.ASISTENCIA && state.editando) {
            BarraGuardar(onCancelar = vm::cancelar, onGuardar = vm::guardar, modifier = Modifier.align(Alignment.BottomCenter))
        }
        ConfirmToastHost(
            mensaje = state.mensaje,
            onDismiss = vm::consumirMensaje,
            tipo = if (state.mensajeEsAdvertencia) TipoAviso.INFO else TipoAviso.EXITO,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    state.formHorario?.let { HorarioSheet(it, vm) }
    state.formAcopiador?.let { MarcasAcopiadorSheet(it, state.horariosActivos, state.fecha, vm) }
    state.horarioAEliminar?.let { horario ->
        ConfirmarDialog(
            titulo = "Eliminar horario",
            mensaje = "Se eliminará «${horario.nombre}» junto con las marcas de asistencia de los acopiadores en ese horario.",
            textoConfirmar = "Eliminar",
            onConfirmar = vm::eliminarHorario,
            onCancelar = vm::cancelarEliminarHorario,
        )
    }
    if (state.confirmarQuitarDias) {
        ConfirmarDialog(
            titulo = "Quitar días",
            mensaje = "¿Quitar los días de asistencia que faltan de este mes?",
            textoConfirmar = "Quitar",
            onConfirmar = { vm.seleccionarDiasDelMes(ModoDias.NINGUNO) },
            onCancelar = vm::cancelarQuitarDias,
        )
    }
}

// ---------------------------------------------------------------- Pestaña "Asistencia"

private fun LazyListScope.pestanaAsistencia(state: TomarAsistenciaUiState, vm: TomarAsistenciaViewModel) {
    item(key = "pasos") { Pasos() }
    item(key = "calendario") {
        SectionCard {
            Calendario(
                mes = state.mes,
                semanas = state.semanas,
                onMesAnterior = { vm.cambiarMes(-1) },
                onMesSiguiente = { vm.cambiarMes(1) },
            ) { dia, modifier -> CeldaAsistencia(dia, seleccionado = dia.fecha == state.fecha, modifier = modifier, onClick = { vm.seleccionarFecha(dia.fecha) }) }
            Leyenda(
                listOf(
                    VerdePrimario to "Registrada",
                    Ambar to "Con tardanzas",
                    Rojo to "Con faltas",
                ),
            )
        }
    }
    item(key = "dia") { TarjetaDia(state, vm) }

    if (!state.fechaRegistrada && !state.editando) {
        item(key = "sin-registro") {
            EmptyState(
                "Aún no se tomó asistencia este día",
                if (state.esFutura) "No se puede tomar asistencia de un día futuro." else "Presiona \"Tomar asistencia\" y todos aparecerán como presentes.",
                icono = Icons.Filled.EventAvailable,
            )
        }
        return
    }

    item(key = "busqueda") {
        AppTextField(state.busqueda, vm::onBusqueda, "Buscar trabajador", placeholder = "Nombre, apellido o documento")
    }
    if (state.editando) {
        item(key = "marcar-todos") { MarcarTodos(onMarcar = vm::marcarTodos) }
    }
    if (state.trabajadores.isEmpty()) {
        item(key = "sin-trabajadores") {
            EmptyState("Sin trabajadores", "No hay trabajadores que coincidan con la búsqueda.")
        }
    } else {
        itemsIndexed(state.trabajadores, key = { _, f -> "t-${f.usuario.id}" }) { index, fila ->
            StaggeredListItem(index = index) {
                TarjetaTrabajador(fila, editando = state.editando, onCambiar = { vm.cambiarEstado(fila.usuario.id, it) })
            }
        }
    }
    if (state.editando) {
        // Espacio para que la barra de "Guardar" no tape al último trabajador.
        item(key = "espacio-barra") { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun Pasos() {
    val pasos = listOf("Elige el día en el calendario", "Presiona \"Tomar asistencia\"", "Cambia a quien llegó tarde o faltó", "Presiona \"Guardar\"")
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        pasos.forEachIndexed { indice, paso ->
            Row(
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Blanco).border(1.dp, Borde, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumeroPaso(indice + 1)
                Text(paso, style = CuerpoMedioStyle, color = Negro, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun NumeroPaso(numero: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(20.dp).clip(CircleShape).background(LocalTemaRol.current.degradado()),
        contentAlignment = Alignment.Center,
    ) {
        Text(numero.toString(), color = Blanco, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TarjetaDia(state: TomarAsistenciaUiState, vm: TomarAsistenciaViewModel) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.cambiarDia(-1) }, enabled = !state.editando) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Día anterior", tint = VerdeOscuro)
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (state.fecha == state.hoy) "HOY · ${state.fecha.diaSemana().uppercase()}" else state.fecha.diaSemana().uppercase(),
                    style = EtiquetaMayusculaStyle,
                    color = GrisTexto,
                )
                Text(state.fecha.largo(), style = TituloPantallaStyle, color = Negro, textAlign = TextAlign.Center)
            }
            IconButton(onClick = { vm.cambiarDia(1) }, enabled = !state.editando && state.fecha < state.hoy) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Día siguiente", tint = if (state.fecha < state.hoy) VerdeOscuro else Borde)
            }
        }

        if (state.fechaRegistrada || state.editando) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Contador("Presentes", state.presentes, EstadoAsistencia.PRESENTE)
                Contador("Tardanzas", state.tardanzas, EstadoAsistencia.TARDANZA)
                Contador("Faltas", state.faltas, EstadoAsistencia.FALTA)
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                state.editando -> EstadoChip("Editando", EstiloChip.INFO)
                state.fechaRegistrada -> EstadoChip("Asistencia registrada", EstiloChip.EXITO)
                else -> EstadoChip("Sin registrar", EstiloChip.ADVERTENCIA)
            }
            Spacer(Modifier.weight(1f))
            if (state.fecha != state.hoy && !state.editando) {
                Text(
                    "Ir a hoy",
                    style = CuerpoMedioStyle,
                    color = VerdePrimario,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = vm::irAHoy).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        if (!state.editando && !state.esFutura) {
            Spacer(Modifier.height(12.dp))
            if (state.fechaRegistrada) {
                SecondaryButton("Editar asistencia", onClick = vm::editar)
            } else {
                PrimaryButton("Tomar asistencia", onClick = vm::nuevo)
            }
        }
    }
}

@Composable
private fun RowScope.Contador(etiqueta: String, cantidad: Int, estado: EstadoAsistencia) {
    val color = estado.color()
    Column(
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.10f)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(cantidad.toString(), style = TituloPantallaStyle, color = if (estado == EstadoAsistencia.TARDANZA) AmbarTexto else color)
        Text(etiqueta, style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp)
    }
}

@Composable
private fun MarcarTodos(onMarcar: (EstadoAsistencia) -> Unit) {
    SectionCard {
        Text("Marcar a todos como", style = CuerpoMedioStyle, color = Negro)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EstadoAsistencia.entries.forEach { estado ->
                OpcionEstado(estado, activa = false, onClick = { onMarcar(estado) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TarjetaTrabajador(fila: FilaTrabajador, editando: Boolean, onCambiar: (EstadoAsistencia) -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(fila.usuario.iniciales, fila.estado)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(fila.usuario.nombreCompleto, style = CuerpoMedioStyle, color = Negro, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    fila.usuario.documento.ifBlank { "Sin documento" },
                    style = CuerpoStyle,
                    color = GrisTexto,
                    fontSize = 12.sp,
                )
            }
            if (!editando) {
                val estado = fila.estado
                if (estado != null) EstadoChip(estado.etiqueta, estado.estiloChip()) else EstadoChip("Sin registro", EstiloChip.INFO)
            }
        }
        if (editando) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EstadoAsistencia.entries.forEach { estado ->
                    OpcionEstado(estado, activa = fila.estado == estado, onClick = { onCambiar(estado) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Avatar(iniciales: String, estado: EstadoAsistencia?) {
    val color = estado?.color() ?: VerdePrimario
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(iniciales, color = if (estado == EstadoAsistencia.TARDANZA) AmbarTexto else color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** Botón de estado (Presente / Tardanza / Falta): relleno de su color cuando está elegido. */
@Composable
private fun OpcionEstado(estado: EstadoAsistencia, activa: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = estado.color()
    val forma = RoundedCornerShape(12.dp)
    val texto = when {
        activa && estado == EstadoAsistencia.TARDANZA -> Negro
        activa -> Blanco
        estado == EstadoAsistencia.TARDANZA -> AmbarTexto
        else -> color
    }
    Row(
        modifier = modifier
            .clip(forma)
            .background(if (activa) color else color.copy(alpha = 0.08f))
            .border(1.dp, if (activa) color else color.copy(alpha = 0.25f), forma)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(estado.icono(), contentDescription = null, tint = texto, modifier = Modifier.size(16.dp))
        Text(estado.etiqueta, style = CuerpoMedioStyle, color = texto, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp), maxLines = 1)
    }
}

@Composable
private fun BarraGuardar(onCancelar: () -> Unit, onGuardar: () -> Unit, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .shadow(12.dp, forma)
            .clip(forma)
            .background(Blanco)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SecondaryButton("Cancelar", onClick = onCancelar, modifier = Modifier.weight(1f))
        PrimaryButton("Guardar", onClick = onGuardar, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------- Pestaña "Justificaciones"

private fun LazyListScope.pestanaJustificaciones(state: TomarAsistenciaUiState, vm: TomarAsistenciaViewModel) {
    item(key = "filtros") {
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FiltroJustificacion.entries.forEach { filtro ->
                val activa = filtro == state.filtroJustificacion
                Text(
                    filtro.etiqueta,
                    style = CuerpoMedioStyle,
                    color = textoOpcion(activa),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(fondoOpcion(activa))
                        .clickable { vm.filtrarJustificaciones(filtro) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
    if (state.justificaciones.isEmpty()) {
        item(key = "sin-justificaciones") {
            EmptyState(
                "Sin justificaciones",
                if (state.filtroJustificacion == FiltroJustificacion.TODAS) "Todavía nadie envió justificaciones."
                else "No hay justificaciones ${state.filtroJustificacion.etiqueta.lowercase()}.",
                icono = Icons.Filled.Description,
            )
        }
        return
    }
    itemsIndexed(state.justificaciones, key = { _, f -> "j-${f.justificacion.id}" }) { index, fila ->
        val j = fila.justificacion
        val acciones: (@Composable RowScope.() -> Unit)? = if (j.estado == EstadoJustificacion.PENDIENTE) {
            {
                BotonAccion("Aceptar", Icons.Filled.Check, VerdeOscuro, onClick = { vm.aceptar(fila) })
                BotonAccion("Rechazar", Icons.Filled.Close, Rojo, onClick = { vm.rechazar(fila) })
            }
        } else {
            null
        }
        StaggeredListItem(index = index) {
            TarjetaLista(
                titulo = fila.trabajador,
                subtitulo = "${j.fecha.corto()} · ${fila.horario ?: "Asistencia general"}",
                icono = Icons.Filled.Description,
                derecha = { EstadoChip(j.estado.etiqueta, j.estado.estiloChip()) },
                acciones = acciones,
            ) {
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Justifica una ", style = CuerpoStyle, color = GrisTexto)
                    EstadoChip(j.estadoAsistencia.etiqueta, j.estadoAsistencia.estiloChip())
                }
                Text(
                    j.motivo,
                    style = CuerpoStyle,
                    color = Negro,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(VerdeClaro.copy(alpha = 0.6f)).padding(10.dp),
                )
                if (j.tieneArchivo) {
                    Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null, tint = CelesteTexto, modifier = Modifier.size(16.dp))
                        Text("Tiene un archivo adjunto (se ve en la web)", style = CuerpoStyle, color = CelesteTexto, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Pestaña "Acopiadores"

private fun LazyListScope.pestanaAcopiadores(state: TomarAsistenciaUiState, vm: TomarAsistenciaViewModel) {
    item(key = "dias-acopiador") {
        SectionCard {
            TituloPaso(1, "Días de asistencia", "Toca un día para activarlo o quitarlo. Solo los días en verde cuentan.") {
                EstadoChip(if (state.diasElegidos == 1) "1 día" else "${state.diasElegidos} días", EstiloChip.EXITO)
            }
            Spacer(Modifier.height(8.dp))
            Calendario(
                mes = state.mesAcopiadores,
                semanas = state.semanasAcopiadores,
                onMesAnterior = { vm.cambiarMesAcopiadores(-1) },
                onMesSiguiente = { vm.cambiarMesAcopiadores(1) },
            ) { dia, modifier -> CeldaDiaAcopiador(dia, viendo = dia.fecha == state.fecha, modifier = modifier, onClick = { vm.alternarDiaAcopiador(dia.fecha) }) }
            Leyenda(listOf(VerdePrimario to "Se toma", Ambar to "Hoy"))
            Text("Elegir rápido los días que quedan del mes:", style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val habilitado = !state.mesAcopiadoresTerminado
                BotonRapido("Lun – Sáb", habilitado, Modifier.weight(1f)) { vm.seleccionarDiasDelMes(ModoDias.LABORABLES) }
                BotonRapido("Todos", habilitado, Modifier.weight(1f)) { vm.seleccionarDiasDelMes(ModoDias.TODOS) }
                BotonRapido("Ninguno", habilitado, Modifier.weight(1f), onClick = vm::pedirQuitarDias)
            }
        }
    }

    item(key = "horarios") {
        SectionCard {
            TituloPaso(2, "Horarios", "Horas en que el acopiador puede marcar. Si no marca a tiempo, queda con falta.") {
                BotonChico("Nuevo", onClick = vm::nuevoHorario)
            }
            if (state.horarios.isEmpty()) {
                Text(
                    "Aún no hay horarios. Crea uno (ej. \"Mañana\") para que los acopiadores puedan marcar.",
                    style = CuerpoStyle,
                    color = GrisTexto,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            state.horarios.forEach { horario ->
                FilaHorario(horario, onEditar = { vm.editarHorario(horario) }, onEliminar = { vm.pedirEliminarHorario(horario) })
            }
        }
    }

    item(key = "asistencia-acopiadores") {
        SectionCard {
            TituloPaso(3, "Asistencia del día", "Revisa cómo marcó cada acopiador y corrige si hace falta.")
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.cambiarDia(-1) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Día anterior", tint = VerdeOscuro) }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.fecha.diaSemana().uppercase(), style = EtiquetaMayusculaStyle, color = GrisTexto)
                    Text(state.fecha.largo(), style = CuerpoMedioStyle, color = Negro, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = { vm.cambiarDia(1) }, enabled = state.fecha < state.hoy) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Día siguiente", tint = if (state.fecha < state.hoy) VerdeOscuro else Borde)
                }
            }
            if (!state.esDiaAcopiador) {
                Aviso("Este día no es día de asistencia de los acopiadores: no marcan ni quedan con falta.")
            }
        }
    }

    when {
        state.horariosActivos.isEmpty() -> item(key = "sin-horarios-activos") {
            EmptyState("Sin horarios activos", "Crea o activa un horario para ver la asistencia de los acopiadores.", icono = Icons.Filled.Schedule)
        }
        else -> {
            item(key = "busqueda-acopiadores") {
                AppTextField(state.busqueda, vm::onBusqueda, "Buscar acopiador", placeholder = "Nombre, apellido o documento")
            }
            if (state.acopiadores.isEmpty()) {
                item(key = "sin-acopiadores") {
                    EmptyState("Sin acopiadores", "No hay acopiadores activos que coincidan.", icono = Icons.Filled.LocalShipping)
                }
            }
            itemsIndexed(state.acopiadores, key = { _, f -> "a-${f.usuario.id}" }) { index, fila ->
                StaggeredListItem(index = index) {
                    TarjetaLista(
                        titulo = fila.usuario.nombreCompleto,
                        subtitulo = fila.usuario.documento.ifBlank { "Sin documento" },
                        icono = Icons.Filled.LocalShipping,
                        acciones = { BotonAccion("Editar", Icons.Filled.Edit, VerdeOscuro, onClick = { vm.editarAcopiador(fila) }) },
                    ) {
                        state.horariosActivos.forEach { horario ->
                            val marca = fila.marcas[horario.id]
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(horario.nombre, style = CuerpoMedioStyle, color = Negro, modifier = Modifier.weight(1f))
                                marca?.horaMarcada?.let {
                                    Text(it, style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                                }
                                if (marca != null) EstadoChip(marca.estado.etiqueta, marca.estado.estiloChip()) else EstadoChip("Sin registro", EstiloChip.INFO)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TituloPaso(numero: Int, titulo: String, ayuda: String, accion: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.Top) {
        NumeroPaso(numero, Modifier.padding(top = 2.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(titulo, style = TituloPantallaStyle, color = Negro)
            Text(ayuda, style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        accion?.let { Box(Modifier.padding(start = 8.dp)) { it() } }
    }
}

@Composable
private fun BotonRapido(texto: String, habilitado: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val forma = RoundedCornerShape(12.dp)
    Text(
        texto,
        style = CuerpoMedioStyle,
        color = VerdeOscuro,
        textAlign = TextAlign.Center,
        modifier = modifier
            .alpha(if (habilitado) 1f else 0.4f)
            .clip(forma)
            .background(VerdeClaro)
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun FilaHorario(horario: HorarioAsistencia, onEditar: () -> Unit, onEliminar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Borde, RoundedCornerShape(16.dp))
            .alpha(if (horario.activo) 1f else 0.6f)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = VerdePrimario, modifier = Modifier.size(18.dp))
            Text(
                horario.nombre,
                style = CuerpoMedioStyle,
                color = Negro,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            EstadoChip(if (horario.activo) "Activo" else "Inactivo", if (horario.activo) EstiloChip.EXITO else EstiloChip.ADVERTENCIA)
        }
        RangoHorario(VerdePrimario, "Presente", "${horario.horaInicio} – ${horario.limitePresente}")
        RangoHorario(Ambar, "Tardanza", "${horario.inicioTardanza} – ${horario.limiteTardanza}")
        RangoHorario(Rojo, "Falta", "después de las ${horario.limiteTardanza}")
        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonAccion("Editar", Icons.Filled.Edit, VerdeOscuro, onClick = onEditar)
            BotonAccion("Eliminar", Icons.Filled.Delete, Rojo, onClick = onEliminar)
        }
    }
}

@Composable
private fun RangoHorario(color: Color, etiqueta: String, valor: String) {
    Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(etiqueta, style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp, modifier = Modifier.width(78.dp).padding(start = 6.dp))
        Text(valor, style = CuerpoMedioStyle, color = Negro, fontSize = 12.sp)
    }
}

@Composable
private fun Aviso(texto: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(AmbarFondo).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = AmbarTexto, modifier = Modifier.size(18.dp))
        Text(texto, style = CuerpoStyle, color = AmbarTexto, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

// ---------------------------------------------------------------- Calendario

@Composable
private fun Calendario(
    mes: LocalDate,
    semanas: List<List<DiaCalendario>>,
    onMesAnterior: () -> Unit,
    onMesSiguiente: () -> Unit,
    celda: @Composable (DiaCalendario, Modifier) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMesAnterior) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Mes anterior", tint = VerdeOscuro) }
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = VerdePrimario, modifier = Modifier.size(18.dp))
            Text(mes.mesAnio(), style = TituloPantallaStyle, color = Negro, modifier = Modifier.padding(start = 6.dp))
        }
        IconButton(onClick = onMesSiguiente) { Icon(Icons.Filled.ChevronRight, contentDescription = "Mes siguiente", tint = VerdeOscuro) }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)) {
        DIAS_CORTOS.forEach {
            Text(it, style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        semanas.forEach { semana ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                semana.forEach { dia -> celda(dia, Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
}

@Composable
private fun CeldaAsistencia(dia: DiaCalendario, seleccionado: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val forma = RoundedCornerShape(10.dp)
    val tema = LocalTemaRol.current
    Box(
        modifier = modifier
            .alpha(if (dia.delMes) 1f else 0.4f)
            .clip(forma)
            .background(
                when {
                    seleccionado -> tema.degradado()
                    dia.registrada -> SolidColor(VerdeClaro)
                    else -> SolidColor(Color.Transparent)
                },
            )
            .then(if (dia.esHoy && !seleccionado) Modifier.border(2.dp, Ambar, forma) else Modifier)
            .clickable(enabled = !dia.esFuturo, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            dia.fecha.day.toString(),
            style = CuerpoMedioStyle,
            color = when {
                seleccionado -> Blanco
                dia.esFuturo -> GrisTexto.copy(alpha = 0.5f)
                else -> Negro
            },
            fontWeight = if (seleccionado || dia.esHoy) FontWeight.Bold else FontWeight.Medium,
        )
        if (dia.registrada) {
            val punto = when {
                seleccionado -> Blanco
                dia.faltas > 0 -> Rojo
                dia.tardanzas > 0 -> Ambar
                else -> VerdePrimario
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(5.dp).clip(CircleShape).background(punto))
        }
    }
}

@Composable
private fun CeldaDiaAcopiador(dia: DiaCalendario, viendo: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val forma = RoundedCornerShape(10.dp)
    val (fondo, texto) = when {
        dia.diaAcopiador && !dia.esPasado -> VerdePrimario to Blanco
        dia.diaAcopiador -> VerdeClaro to VerdeOscuro
        dia.esPasado -> Color.Transparent to GrisTexto.copy(alpha = 0.45f)
        else -> Color.Transparent to Negro
    }
    Box(
        modifier = modifier
            .alpha(if (dia.delMes) 1f else 0.4f)
            .clip(forma)
            .background(fondo)
            .then(if (dia.esHoy) Modifier.border(2.dp, Ambar, forma) else Modifier)
            .clickable(enabled = !dia.esPasado, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(dia.fecha.day.toString(), style = CuerpoMedioStyle, color = texto, fontWeight = if (dia.diaAcopiador) FontWeight.Bold else FontWeight.Medium)
        if (viendo) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(5.dp).clip(CircleShape)
                    .background(if (dia.diaAcopiador && !dia.esPasado) Blanco else VerdePrimario),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Leyenda(items: List<Pair<Color, String>>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (color, texto) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Text(texto, style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- Pestañas

@Composable
private fun Pestanas(actual: PestanaAsistencia, pendientes: Int, onSeleccionar: (PestanaAsistencia) -> Unit) {
    val tema = LocalTemaRol.current
    val forma = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier.fillMaxWidth().clip(forma).background(Blanco).border(1.dp, Borde, forma).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PestanaAsistencia.entries.forEach { pestana ->
            val activa = pestana == actual
            val icono = when (pestana) {
                PestanaAsistencia.ASISTENCIA -> Icons.Filled.EventAvailable
                PestanaAsistencia.JUSTIFICACIONES -> Icons.Filled.Description
                PestanaAsistencia.ACOPIADORES -> Icons.Filled.LocalShipping
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (activa) tema.degradado() else SolidColor(Color.Transparent))
                    .clickable { onSeleccionar(pestana) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icono, contentDescription = null, tint = if (activa) Blanco else GrisTexto, modifier = Modifier.size(16.dp))
                Text(
                    pestana.titulo,
                    style = CuerpoMedioStyle,
                    fontSize = 12.sp,
                    color = if (activa) Blanco else GrisTexto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp).weight(1f, fill = false),
                )
                if (pestana == PestanaAsistencia.JUSTIFICACIONES && pendientes > 0) {
                    Text(
                        pendientes.toString(),
                        color = Negro,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(999.dp)).background(Ambar).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Hojas inferiores

@Composable
private fun HorarioSheet(form: HorarioForm, vm: TomarAsistenciaViewModel) {
    AppBottomSheet(onDismiss = vm::cerrarHorario) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            EncabezadoFormulario(
                if (form.id == null) "Nuevo horario" else "Editar horario",
                "Ej. Mañana: presente de 05:00 a 05:10 y tardanza hasta las 05:20.",
            )
            AppTextField(form.nombre, { v -> vm.editarFormHorario { copy(nombre = v) } }, "Nombre", placeholder = "Mañana", modifier = Modifier.padding(top = 10.dp))
            AppTextField(
                form.horaInicio, { v -> vm.editarFormHorario { copy(horaInicio = formatearHora(v)) } }, "Hora de inicio",
                placeholder = "05:00", teclado = KeyboardType.Number, modifier = Modifier.padding(top = 10.dp),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(
                    form.presenteHasta, { v -> vm.editarFormHorario { copy(presenteHasta = formatearHora(v)) } }, "Presente hasta",
                    placeholder = "05:10", teclado = KeyboardType.Number, modifier = Modifier.weight(1f),
                )
                AppTextField(
                    form.tardanzaHasta, { v -> vm.editarFormHorario { copy(tardanzaHasta = formatearHora(v)) } }, "Tardanza hasta",
                    placeholder = "05:20", teclado = KeyboardType.Number, modifier = Modifier.weight(1f),
                )
            }
            Text(
                "Quien no marque hasta la hora de \"Tardanza hasta\" queda con falta.",
                style = CuerpoStyle,
                color = GrisTexto,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Horario activo", style = CuerpoMedioStyle, color = Negro)
                Switch(
                    checked = form.activo,
                    onCheckedChange = { v -> vm.editarFormHorario { copy(activo = v) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = Blanco, checkedTrackColor = VerdePrimario),
                )
            }
            if (form.error != null) {
                MensajeError(form.error, Modifier.padding(top = 10.dp))
            }
            BotonesFormulario(onCancelar = vm::cerrarHorario, onGuardar = vm::guardarHorario)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarcasAcopiadorSheet(form: MarcasAcopiadorForm, horarios: List<HorarioAsistencia>, fecha: LocalDate, vm: TomarAsistenciaViewModel) {
    AppBottomSheet(onDismiss = vm::cerrarAcopiador) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            EncabezadoFormulario("Asistencia del acopiador", "${form.usuario.nombreCompleto} · ${fecha.largo()}")
            horarios.forEach { horario ->
                val elegido = form.estados[horario.id]
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(CelesteClaro.copy(alpha = 0.5f)).padding(12.dp),
                ) {
                    Text(horario.nombre, style = CuerpoMedioStyle, color = Negro, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Presente ${horario.horaInicio}–${horario.limitePresente} · Tardanza hasta ${horario.limiteTardanza}",
                        style = CuerpoStyle,
                        color = GrisTexto,
                        fontSize = 12.sp,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SinRegistro(activa = elegido == null, onClick = { vm.cambiarMarcaAcopiador(horario.id, null) })
                        EstadoAsistencia.entries.forEach { estado ->
                            OpcionEstado(
                                estado,
                                activa = elegido == estado,
                                onClick = { vm.cambiarMarcaAcopiador(horario.id, estado) },
                                modifier = Modifier.width(96.dp),
                            )
                        }
                    }
                }
            }
            BotonesFormulario(onCancelar = vm::cerrarAcopiador, onGuardar = vm::guardarAcopiador)
        }
    }
}

@Composable
private fun SinRegistro(activa: Boolean, onClick: () -> Unit) {
    val forma = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .clip(forma)
            .background(if (activa) GrisTexto else GrisTexto.copy(alpha = 0.08f))
            .border(1.dp, GrisTexto.copy(alpha = if (activa) 1f else 0.25f), forma)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.RemoveCircleOutline, contentDescription = null, tint = if (activa) Blanco else GrisTexto, modifier = Modifier.size(16.dp))
        Text("Sin registro", style = CuerpoMedioStyle, color = if (activa) Blanco else GrisTexto, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
    }
}
