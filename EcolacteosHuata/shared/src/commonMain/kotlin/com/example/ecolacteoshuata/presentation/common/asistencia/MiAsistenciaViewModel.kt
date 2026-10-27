package com.example.ecolacteoshuata.presentation.common.asistencia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.endOfMonth
import com.example.ecolacteoshuata.core.util.nowLocalDateTime
import com.example.ecolacteoshuata.core.util.plusDias
import com.example.ecolacteoshuata.core.util.startOfMonth
import com.example.ecolacteoshuata.domain.model.Asistencia
import com.example.ecolacteoshuata.domain.model.EstadoAsistencia
import com.example.ecolacteoshuata.domain.model.HorarioAsistencia
import com.example.ecolacteoshuata.domain.model.MiAsistencia
import com.example.ecolacteoshuata.domain.model.estadoParaHora
import com.example.ecolacteoshuata.domain.repository.AsistenciaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.number

/** Estado de un horario de hoy para el acopiador (las etiquetas de la tarjeta de Laravel). */
enum class EstadoHorarioHoy { MARCADO, NO_SE_MARCA, ABIERTO, FALTA, PENDIENTE }

data class HorarioHoy(val horario: HorarioAsistencia, val marca: Asistencia?, val estado: EstadoHorarioHoy)

data class FilaMiAsistencia(val item: MiAsistencia, val horario: String?)

/** Modal "Justificar falta / tardanza" de Laravel. */
data class JustificarForm(val item: MiAsistencia, val motivo: String = "", val error: String? = null, val enviando: Boolean = false)

data class MiAsistenciaUiState(
    val esAcopiador: Boolean,
    val ahora: LocalDateTime,
    val mes: LocalDate,
    val asistencias: List<FilaMiAsistencia> = emptyList(),
    val presentes: Int = 0,
    val tardanzas: Int = 0,
    val faltas: Int = 0,
    val porJustificar: Int = 0,
    val horariosHoy: List<HorarioHoy> = emptyList(),
    val horarioAbierto: HorarioAsistencia? = null,
    val hoyEsDiaDeAsistencia: Boolean = false,
    val form: JustificarForm? = null,
    val mensaje: String? = null,
    val mensajeEsAdvertencia: Boolean = false,
) {
    val hoy: LocalDate get() = ahora.date
    val horaActual: String get() = ahora.hhmm()
    val yaMarcoAbierto: Boolean get() = horarioAbierto != null && horariosHoy.any { it.horario.id == horarioAbierto.id && it.marca != null }
    val puedeMarcar: Boolean get() = horarioAbierto != null && !yaMarcoAbierto
}

private data class ControlMiAsistencia(
    val mes: LocalDate,
    val form: JustificarForm? = null,
    val mensaje: String? = null,
    val advertencia: Boolean = false,
)

private fun LocalDateTime.hhmm(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

private fun LocalDateTime.hhmmss(): String = "${hhmm()}:${second.toString().padStart(2, '0')}"

/**
 * "Asistencia" de Laravel: el trabajador revisa sus asistencias del mes y justifica sus faltas o
 * tardanzas dentro de una semana. El acopiador además marca su asistencia por horario con el
 * botón "Asistí". Todo se guarda en el teléfono y se sube a Laravel al sincronizar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MiAsistenciaViewModel(
    private val usuarioId: String,
    private val esAcopiador: Boolean,
    private val repository: AsistenciaRepository,
) : ViewModel() {
    private val inicio = nowLocalDateTime()
    private val control = MutableStateFlow(ControlMiAsistencia(mes = inicio.date.startOfMonth()))

    /** La hora en pantalla y lo que depende de ella (horario abierto, faltas) se refrescan cada 30 s, como el `wire:poll` de Laravel. */
    private val reloj = flow {
        while (true) {
            emit(nowLocalDateTime())
            delay(30_000)
        }
    }

    private val delMes = control.map { it.mes }.distinctUntilChanged()
        .flatMapLatest { mes -> repository.observeMisAsistenciasEntre(usuarioId, mes, mes.endOfMonth()) }

    private val hoyFlow = reloj.map { it.date }.distinctUntilChanged()

    // Las que todavía se pueden justificar (de la última semana), sin importar el mes que se ve.
    private val ultimaSemana = hoyFlow.flatMapLatest { hoy ->
        repository.observeMisAsistenciasEntre(usuarioId, hoy.plusDias(-MiAsistencia.DIAS_PARA_JUSTIFICAR), hoy)
    }

    private val diaDeAsistencia = hoyFlow.flatMapLatest { hoy -> repository.observeDiasAcopiadorEntre(hoy, hoy).map { hoy in it } }

    private val datos = combine(delMes, ultimaSemana, repository.observeHorarios(), diaDeAsistencia) { mes, semana, horarios, esDia ->
        DatosMiAsistencia(mes, semana, horarios, esDia)
    }

    private data class DatosMiAsistencia(
        val delMes: List<MiAsistencia>,
        val ultimaSemana: List<MiAsistencia>,
        val horarios: List<HorarioAsistencia>,
        val hoyEsDia: Boolean,
    )

    val state: StateFlow<MiAsistenciaUiState> = combine(control, reloj, datos) { c, ahora, d -> armar(c, ahora, d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MiAsistenciaUiState(esAcopiador, inicio, inicio.date.startOfMonth()))

    private fun armar(c: ControlMiAsistencia, ahora: LocalDateTime, d: DatosMiAsistencia): MiAsistenciaUiState {
        val hoy = ahora.date
        val hora = ahora.hhmm()
        val nombres = d.horarios.associate { it.id to it.nombre }
        val activos = d.horarios.filter { it.activo }.sortedBy { it.horaInicio }
        // `HorarioAsistencia::abiertoA`: solo hay horarios abiertos en los días de asistencia.
        val abierto = if (esAcopiador && d.hoyEsDia) activos.firstOrNull { it.estadoParaHora(hora) != null } else null
        val marcasHoy = d.ultimaSemana.map { it.asistencia }
            .filter { it.fecha == hoy && it.horarioId != null }
            .associateBy { it.horarioId!! }

        val horariosHoy = if (!esAcopiador) emptyList() else activos.map { h ->
            val marca = marcasHoy[h.id]
            val estado = when {
                marca != null -> EstadoHorarioHoy.MARCADO
                !d.hoyEsDia -> EstadoHorarioHoy.NO_SE_MARCA
                abierto?.id == h.id -> EstadoHorarioHoy.ABIERTO
                hora > h.limiteTardanza -> EstadoHorarioHoy.FALTA
                else -> EstadoHorarioHoy.PENDIENTE
            }
            HorarioHoy(h, marca, estado)
        }

        return MiAsistenciaUiState(
            esAcopiador = esAcopiador,
            ahora = ahora,
            mes = c.mes,
            asistencias = d.delMes.map { FilaMiAsistencia(it, it.asistencia.horarioId?.let { id -> nombres[id] }) },
            presentes = d.delMes.count { it.asistencia.estado == EstadoAsistencia.PRESENTE },
            tardanzas = d.delMes.count { it.asistencia.estado == EstadoAsistencia.TARDANZA },
            faltas = d.delMes.count { it.asistencia.estado == EstadoAsistencia.FALTA },
            porJustificar = d.ultimaSemana.count { it.puedeJustificarse(hoy) },
            horariosHoy = horariosHoy,
            horarioAbierto = abierto,
            hoyEsDiaDeAsistencia = d.hoyEsDia,
            form = c.form,
            mensaje = c.mensaje,
            mensajeEsAdvertencia = c.advertencia,
        )
    }

    fun cambiarMes(desplazamiento: Int) = control.update { c ->
        val total = c.mes.year * 12 + (c.mes.month.number - 1) + desplazamiento
        c.copy(mes = LocalDate(total / 12, total % 12 + 1, 1))
    }

    /** "Asistí": presente o tardanza según la hora del horario que está abierto. */
    fun marcarAsistencia() {
        val s = state.value
        val ahora = nowLocalDateTime()
        val horario = s.horarioAbierto
        if (horario == null) {
            avisar("No hay un horario de asistencia abierto en este momento.", advertencia = true)
            return
        }
        if (s.yaMarcoAbierto) {
            avisar("Ya marcaste tu asistencia de ${horario.nombre}.", advertencia = true)
            return
        }
        val estado = horario.estadoParaHora(ahora.hhmm())
        if (estado == null) {
            avisar("El horario ${horario.nombre} ya cerró.", advertencia = true)
            return
        }
        viewModelScope.launch {
            repository.marcarAsistenciaPropia(usuarioId, horario.id, ahora.date, ahora.hhmmss(), estado)
            avisar(
                "¡Asistencia marcada! ${estado.etiqueta} a las ${ahora.hhmm()} (${horario.nombre}).",
                advertencia = estado != EstadoAsistencia.PRESENTE,
            )
        }
    }

    fun abrirJustificar(fila: FilaMiAsistencia) {
        if (!fila.item.puedeJustificarse(nowLocalDateTime().date)) {
            avisar("Esta asistencia ya no se puede justificar.", advertencia = true)
            return
        }
        control.update { it.copy(form = JustificarForm(fila.item)) }
    }

    fun onMotivo(texto: String) = control.update { c -> c.copy(form = c.form?.copy(motivo = texto, error = null)) }

    fun cerrarJustificar() = control.update { it.copy(form = null) }

    fun justificar() {
        val f = control.value.form ?: return
        if (f.enviando) return
        val motivo = f.motivo.trim()
        val hoy = nowLocalDateTime().date
        val error = when {
            motivo.length < 10 -> "Escribe el contexto con al menos 10 caracteres."
            motivo.length > 1000 -> "El contexto no puede tener más de 1000 caracteres."
            !f.item.puedeJustificarse(hoy) -> "El plazo de una semana para justificar ya venció."
            else -> null
        }
        if (error != null) {
            control.update { it.copy(form = f.copy(error = error)) }
            return
        }
        control.update { it.copy(form = f.copy(enviando = true)) }
        viewModelScope.launch {
            repository.justificar(f.item.asistencia.id, motivo, hoy)
            control.update {
                it.copy(form = null, mensaje = "Justificación enviada. Te avisaremos cuando sea revisada.", advertencia = false)
            }
        }
    }

    fun consumirMensaje() = control.update { it.copy(mensaje = null) }

    private fun avisar(mensaje: String, advertencia: Boolean = false) =
        control.update { it.copy(mensaje = mensaje, advertencia = advertencia) }
}
