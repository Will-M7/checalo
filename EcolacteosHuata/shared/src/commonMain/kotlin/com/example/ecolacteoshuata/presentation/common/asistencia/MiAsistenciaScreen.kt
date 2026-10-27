package com.example.ecolacteoshuata.presentation.common.asistencia

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecolacteoshuata.core.theme.Ambar
import com.example.ecolacteoshuata.core.theme.AmbarFondo
import com.example.ecolacteoshuata.core.theme.AmbarTexto
import com.example.ecolacteoshuata.core.theme.Blanco
import com.example.ecolacteoshuata.core.theme.Borde
import com.example.ecolacteoshuata.core.theme.CelesteClaro
import com.example.ecolacteoshuata.core.theme.CelesteTexto
import com.example.ecolacteoshuata.core.theme.CifraGrandeStyle
import com.example.ecolacteoshuata.core.theme.CuerpoMedioStyle
import com.example.ecolacteoshuata.core.theme.CuerpoStyle
import com.example.ecolacteoshuata.core.theme.EtiquetaMayusculaStyle
import com.example.ecolacteoshuata.core.theme.GrisTexto
import com.example.ecolacteoshuata.core.theme.Negro
import com.example.ecolacteoshuata.core.theme.TituloPantallaStyle
import com.example.ecolacteoshuata.core.theme.VerdeClaro
import com.example.ecolacteoshuata.core.theme.VerdeOscuro
import com.example.ecolacteoshuata.core.theme.VerdePrimario
import com.example.ecolacteoshuata.core.ui.AppBottomSheet
import com.example.ecolacteoshuata.core.ui.AppTextField
import com.example.ecolacteoshuata.core.ui.BotonChico
import com.example.ecolacteoshuata.core.ui.BotonesFormulario
import com.example.ecolacteoshuata.core.ui.ConfirmToastHost
import com.example.ecolacteoshuata.core.ui.EmptyState
import com.example.ecolacteoshuata.core.ui.EncabezadoFormulario
import com.example.ecolacteoshuata.core.ui.EstadoChip
import com.example.ecolacteoshuata.core.ui.EstiloChip
import com.example.ecolacteoshuata.core.ui.ListaEspaciado
import com.example.ecolacteoshuata.core.ui.ListaPadding
import com.example.ecolacteoshuata.core.ui.LocalTemaRol
import com.example.ecolacteoshuata.core.ui.MensajeError
import com.example.ecolacteoshuata.core.ui.SectionCard
import com.example.ecolacteoshuata.core.ui.StaggeredListItem
import com.example.ecolacteoshuata.core.ui.TipoAviso
import com.example.ecolacteoshuata.core.ui.degradado
import com.example.ecolacteoshuata.domain.model.EstadoAsistencia
import com.example.ecolacteoshuata.domain.model.MiAsistencia
import com.example.ecolacteoshuata.domain.model.Usuario
import com.example.ecolacteoshuata.domain.model.estadoParaHora
import com.example.ecolacteoshuata.presentation.common.ModuloItem
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Módulo "Asistencia" (mis asistencias) del panel y del menú "Más". */
fun moduloMiAsistencia(descripcion: String = "Marca tu asistencia y justifica faltas", onClick: () -> Unit) =
    ModuloItem("Asistencia", Icons.Filled.Fingerprint, descripcion, onClick = onClick)

/** "Asistencia" de Laravel (mis asistencias), con el botón "Asistí" para los acopiadores. */
@Composable
fun MiAsistenciaHost(usuario: Usuario) {
    val vm = koinViewModel<MiAsistenciaViewModel>(key = "mi-asistencia-${usuario.id}") { parametersOf(usuario.id, usuario.esAcopiador) }
    val state by vm.state.collectAsState()
    MiAsistenciaScreen(state, vm)
}

@Composable
private fun MiAsistenciaScreen(state: MiAsistenciaUiState, vm: MiAsistenciaViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ListaPadding, verticalArrangement = ListaEspaciado) {
            item(key = "ayuda") {
                Text(
                    "Revisa tus asistencias y justifica tus faltas o tardanzas dentro de una semana.",
                    style = CuerpoStyle,
                    color = GrisTexto,
                )
            }
            if (state.esAcopiador) {
                item(key = "marcar") { TarjetaMarcar(state, vm::marcarAsistencia) }
            }
            if (state.porJustificar > 0) {
                item(key = "por-justificar") { AvisoPorJustificar(state.porJustificar) }
            }
            item(key = "mes") { SelectorMes(state, vm) }
            item(key = "contadores") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Contador("Presentes", state.presentes, EstadoAsistencia.PRESENTE)
                    Contador("Tardanzas", state.tardanzas, EstadoAsistencia.TARDANZA)
                    Contador("Faltas", state.faltas, EstadoAsistencia.FALTA)
                }
            }
            if (state.asistencias.isEmpty()) {
                item(key = "vacio") {
                    EmptyState("Sin asistencias", "No tienes asistencias registradas en este mes.", icono = Icons.Filled.EventAvailable)
                }
            } else {
                itemsIndexed(state.asistencias, key = { _, f -> f.item.asistencia.id }) { index, fila ->
                    StaggeredListItem(index = index) {
                        TarjetaMiAsistencia(fila, state, onJustificar = { vm.abrirJustificar(fila) })
                    }
                }
            }
        }
        ConfirmToastHost(
            mensaje = state.mensaje,
            onDismiss = vm::consumirMensaje,
            tipo = if (state.mensajeEsAdvertencia) TipoAviso.INFO else TipoAviso.EXITO,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    state.form?.let { JustificarSheet(it, vm) }
}

/** Hora actual, horarios de hoy y el botón grande "Asistí" (la tarjeta del acopiador en Laravel). */
@Composable
private fun TarjetaMarcar(state: MiAsistenciaUiState, onMarcar: () -> Unit) {
    val tema = LocalTemaRol.current
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("HORA ACTUAL", style = EtiquetaMayusculaStyle, color = GrisTexto)
                Text(state.horaActual, style = CifraGrandeStyle, color = VerdeOscuro)
                Text(state.hoy.diaSemana().replaceFirstChar { it.uppercase() } + ", " + state.hoy.largo(), style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp)
            }
            BotonAsisti(state, onMarcar)
        }

        Text(
            when {
                state.yaMarcoAbierto -> "Ya marcaste tu asistencia de ${state.horarioAbierto?.nombre}."
                state.horarioAbierto != null -> {
                    val h = state.horarioAbierto
                    if (h.estadoParaHora(state.horaActual) == EstadoAsistencia.PRESENTE) "Marca antes de las ${h.limitePresente} para quedar como presente."
                    else "Ya es tardanza. Tienes hasta las ${h.limiteTardanza}."
                }
                !state.hoyEsDiaDeAsistencia -> "Hoy no es día de asistencia."
                else -> "El botón se activa cuando empieza tu horario."
            },
            style = CuerpoMedioStyle,
            color = if (state.puedeMarcar) VerdeOscuro else GrisTexto,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (state.puedeMarcar) VerdeClaro else Borde.copy(alpha = 0.4f))
                .padding(10.dp),
        )

        if (state.horariosHoy.isEmpty()) {
            Text(
                "El administrador todavía no definió horarios de asistencia.",
                style = CuerpoStyle,
                color = GrisTexto,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        state.horariosHoy.forEach { h ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, if (h.estado == EstadoHorarioHoy.ABIERTO) tema.acento else Borde, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(h.horario.nombre, style = CuerpoMedioStyle, color = Negro, fontWeight = FontWeight.SemiBold)
                    Text("${h.horario.horaInicio} – ${h.horario.limiteTardanza}", style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp)
                }
                val marca = h.marca
                when (h.estado) {
                    EstadoHorarioHoy.MARCADO -> if (marca != null) {
                        EstadoChip(marca.estado.etiqueta + (marca.horaMarcada?.take(5)?.let { " · $it" } ?: ""), marca.estado.estiloChip())
                    }
                    EstadoHorarioHoy.NO_SE_MARCA -> EstadoChip("Hoy no se marca", EstiloChip.INFO)
                    EstadoHorarioHoy.ABIERTO -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = CelesteTexto, modifier = Modifier.size(16.dp).padding(end = 2.dp))
                        EstadoChip("Abierto ahora", EstiloChip.INFO)
                    }
                    EstadoHorarioHoy.FALTA -> EstadoChip("Falta", EstiloChip.ERROR)
                    EstadoHorarioHoy.PENDIENTE -> EstadoChip("Pendiente", EstiloChip.ADVERTENCIA)
                }
            }
        }
    }
}

@Composable
private fun BotonAsisti(state: MiAsistenciaUiState, onMarcar: () -> Unit) {
    val tema = LocalTemaRol.current
    val activo = state.puedeMarcar
    val pulso by rememberInfiniteTransition(label = "pulso").animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulso-asisti",
    )
    Column(
        modifier = Modifier
            .scale(if (activo) pulso else 1f)
            .size(116.dp)
            .shadow(if (activo) 12.dp else 0.dp, CircleShape, ambientColor = tema.acento, spotColor = tema.acento)
            .clip(CircleShape)
            .background(if (activo) tema.degradado() else SolidColor(Borde))
            .clickable(enabled = activo, onClick = onMarcar),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (state.yaMarcoAbierto) Icons.Filled.Verified else Icons.Filled.Fingerprint,
            contentDescription = null,
            tint = if (activo) Blanco else GrisTexto,
            modifier = Modifier.size(40.dp),
        )
        Text(
            if (state.yaMarcoAbierto) "Marcado" else "Asistí",
            style = TituloPantallaStyle,
            color = if (activo) Blanco else GrisTexto,
        )
    }
}

@Composable
private fun AvisoPorJustificar(cantidad: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AmbarFondo).border(1.dp, Ambar.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = AmbarTexto, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                "Tienes $cantidad ${if (cantidad == 1) "falta o tardanza" else "faltas o tardanzas"} por justificar",
                style = CuerpoMedioStyle,
                color = AmbarTexto,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Recuerda que solo tienes ${MiAsistencia.DIAS_PARA_JUSTIFICAR} días desde la fecha para enviar tu justificación.",
                style = CuerpoStyle,
                color = AmbarTexto,
            )
        }
    }
}

@Composable
private fun SelectorMes(state: MiAsistenciaUiState, vm: MiAsistenciaViewModel) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.cambiarMes(-1) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Mes anterior", tint = VerdeOscuro) }
            Text(state.mes.mesAnio(), style = TituloPantallaStyle, color = Negro, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.cambiarMes(1) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Mes siguiente", tint = VerdeOscuro) }
        }
    }
}

@Composable
private fun RowScope.Contador(etiqueta: String, cantidad: Int, estado: EstadoAsistencia) {
    val color = estado.color()
    Column(
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.10f)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(estado.icono(), contentDescription = null, tint = if (estado == EstadoAsistencia.TARDANZA) AmbarTexto else color, modifier = Modifier.size(18.dp))
        Text(cantidad.toString(), style = TituloPantallaStyle.copy(fontSize = 22.sp), color = if (estado == EstadoAsistencia.TARDANZA) AmbarTexto else color)
        Text(etiqueta, style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp)
    }
}

@Composable
private fun TarjetaMiAsistencia(fila: FilaMiAsistencia, state: MiAsistenciaUiState, onJustificar: () -> Unit) {
    val item = fila.item
    val a = item.asistencia
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(a.estado.color().copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(a.estado.icono(), contentDescription = null, tint = if (a.estado == EstadoAsistencia.TARDANZA) AmbarTexto else a.estado.color())
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    a.fecha.diaSemana().replaceFirstChar { it.uppercase() } + ", ${a.fecha.day} de ${a.fecha.largo().substringAfter(" de ").substringBefore(" de ")}",
                    style = CuerpoMedioStyle,
                    color = Negro,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.esAcopiador || fila.horario != null) {
                    Text(
                        listOfNotNull(fila.horario ?: "Asistencia general", a.horaMarcada?.take(5)).joinToString(" · "),
                        style = CuerpoStyle,
                        color = GrisTexto,
                        fontSize = 12.sp,
                    )
                }
            }
            EstadoChip(a.estado.etiqueta, a.estado.estiloChip())
        }

        if (a.estado != EstadoAsistencia.PRESENTE) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    item.puedeJustificarse(state.hoy) -> {
                        BotonChico(if (item.justificacionId != null) "Volver a justificar" else "Justificar", onClick = onJustificar, icono = Icons.Filled.NoteAdd)
                        if (item.justificacionId != null) {
                            EstadoChip("Rechazada", EstiloChip.ERROR, Modifier.padding(start = 8.dp))
                        }
                        val restantes = item.diasRestantes(state.hoy)
                        Text(
                            if (restantes == 0) "Último día para justificar" else "Quedan $restantes ${if (restantes == 1) "día" else "días"}",
                            style = CuerpoStyle,
                            color = GrisTexto,
                            fontSize = 12.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                    }
                    item.justificacionEstado != null -> EstadoChip(
                        "Justificación ${item.justificacionEstado.etiqueta.lowercase()}",
                        item.justificacionEstado.estiloChip(),
                    )
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = GrisTexto, modifier = Modifier.size(14.dp))
                        Text("Plazo vencido", style = CuerpoStyle, color = GrisTexto, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun JustificarSheet(form: JustificarForm, vm: MiAsistenciaViewModel) {
    val a = form.item.asistencia
    AppBottomSheet(onDismiss = vm::cerrarJustificar) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            EncabezadoFormulario(
                "Justificar ${a.estado.etiqueta.lowercase()}",
                a.fecha.diaSemana().replaceFirstChar { it.uppercase() } + ", " + a.fecha.largo(),
            )
            AppTextField(
                form.motivo,
                vm::onMotivo,
                "Contexto",
                placeholder = "Explica el motivo de tu falta o tardanza...",
                lineasMax = 5,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                "${form.motivo.trim().length}/1000 · mínimo 10 caracteres",
                style = CuerpoStyle,
                color = GrisTexto,
                fontSize = 12.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(CelesteClaro).padding(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = CelesteTexto, modifier = Modifier.size(18.dp))
                Text(
                    "Si tienes una constancia (PDF o foto), puedes adjuntarla desde la web en \"Asistencia\".",
                    style = CuerpoStyle,
                    color = CelesteTexto,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (form.error != null) {
                MensajeError(form.error, Modifier.padding(top = 10.dp))
            }
            BotonesFormulario(
                onCancelar = vm::cerrarJustificar,
                onGuardar = vm::justificar,
                textoGuardar = "Enviar justificación",
                cargando = form.enviando,
            )
        }
    }
}
