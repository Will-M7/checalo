package com.example.ecolacteoshuata.presentation.common.asistencia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecolacteoshuata.core.util.endOfMonth
import com.example.ecolacteoshuata.core.util.endOfWeek
import com.example.ecolacteoshuata.core.util.plusDias
import com.example.ecolacteoshuata.core.util.startOfMonth
import com.example.ecolacteoshuata.core.util.startOfWeek
import com.example.ecolacteoshuata.core.util.todayLocalDate
import com.example.ecolacteoshuata.domain.model.Asistencia
import com.example.ecolacteoshuata.domain.model.EstadoAsistencia
import com.example.ecolacteoshuata.domain.model.EstadoJustificacion
import com.example.ecolacteoshuata.domain.model.HorarioAsistencia
import com.example.ecolacteoshuata.domain.model.Justificacion
import com.example.ecolacteoshuata.domain.model.Usuario
import com.example.ecolacteoshuata.domain.repository.AsistenciaRepository
import com.example.ecolacteoshuata.domain.repository.UsuarioRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/** Las tres pestañas de "Tomar asistencia" de Laravel. */
enum class PestanaAsistencia(val titulo: String, val ayuda: String) {
    ASISTENCIA("Asistencia", "Registra quién vino, quién llegó tarde y quién faltó cada día."),
    JUSTIFICACIONES("Justificaciones", "Revisa los motivos que enviaron los trabajadores por sus faltas o tardanzas."),
    ACOPIADORES("Acopiadores", "Los acopiadores marcan su propia asistencia. Aquí eliges qué días y en qué horario."),
}

enum class FiltroJustificacion(val etiqueta: String, val estado: EstadoJustificacion?) {
    PENDIENTES("Pendientes", EstadoJustificacion.PENDIENTE),
    ACEPTADAS("Aceptadas", EstadoJustificacion.ACEPTADA),
    RECHAZADAS("Rechazadas", EstadoJustificacion.RECHAZADA),
    TODAS("Todas", null),
}

/** Un día del calendario, con el resumen de asistencia o si se toma a los acopiadores. */
data class DiaCalendario(
    val fecha: LocalDate,
    val delMes: Boolean,
    val esHoy: Boolean,
    val esFuturo: Boolean,
    val esPasado: Boolean,
    val registrada: Boolean = false,
    val faltas: Int = 0,
    val tardanzas: Int = 0,
    val diaAcopiador: Boolean = false,
)

data class FilaTrabajador(val usuario: Usuario, val estado: EstadoAsistencia?)

/** Acopiador con sus marcas del día, por id de horario. */
data class FilaAcopiador(val usuario: Usuario, val marcas: Map<String, Asistencia>)

data class FilaJustificacion(val justificacion: Justificacion, val trabajador: String, val documento: String, val horario: String?)

/** Formulario "Nuevo horario" / "Editar horario" (horas como "HH:MM"). */
data class HorarioForm(
    val id: String? = null,
    val nombre: String = "",
    val horaInicio: String = "",
    val presenteHasta: String = "",
    val tardanzaHasta: String = "",
    val activo: Boolean = true,
    val error: String? = null,
)

/** Editar la asistencia de un acopiador en el día: estado por horario (`null` = sin registro). */
data class MarcasAcopiadorForm(val usuario: Usuario, val estados: Map<String, EstadoAsistencia?>)

data class TomarAsistenciaUiState(
    val pestana: PestanaAsistencia = PestanaAsistencia.ASISTENCIA,
    val hoy: LocalDate,
    val fecha: LocalDate,
    val mes: LocalDate,
    val semanas: List<List<DiaCalendario>> = emptyList(),
    val fechaRegistrada: Boolean = false,
    val editando: Boolean = false,
    val busqueda: String = "",
    val trabajadores: List<FilaTrabajador> = emptyList(),
    val presentes: Int = 0,
    val tardanzas: Int = 0,
    val faltas: Int = 0,
    val filtroJustificacion: FiltroJustificacion = FiltroJustificacion.PENDIENTES,
    val justificaciones: List<FilaJustificacion> = emptyList(),
    val pendientes: Int = 0,
    val horarios: List<HorarioAsistencia> = emptyList(),
    val mesAcopiadores: LocalDate,
    val semanasAcopiadores: List<List<DiaCalendario>> = emptyList(),
    val diasElegidos: Int = 0,
    val mesAcopiadoresTerminado: Boolean = false,
    val esDiaAcopiador: Boolean = false,
    val acopiadores: List<FilaAcopiador> = emptyList(),
    val formHorario: HorarioForm? = null,
    val formAcopiador: MarcasAcopiadorForm? = null,
    val horarioAEliminar: HorarioAsistencia? = null,
    val confirmarQuitarDias: Boolean = false,
    val mensaje: String? = null,
    /** El mensaje es una advertencia (ej. día futuro), no una confirmación. */
    val mensajeEsAdvertencia: Boolean = false,
) {
    val esFutura: Boolean get() = fecha > hoy
    val horariosActivos: List<HorarioAsistencia> get() = horarios.filter { it.activo }
}

/** Lo que elige la persona en la pantalla; lo demás sale de la base. */
private data class Control(
    val pestana: PestanaAsistencia = PestanaAsistencia.ASISTENCIA,
    val fecha: LocalDate,
    val mes: LocalDate,
    val mesAcopiadores: LocalDate,
    val editando: Boolean = false,
    val estados: Map<String, EstadoAsistencia> = emptyMap(),
    val busqueda: String = "",
    val filtro: FiltroJustificacion = FiltroJustificacion.PENDIENTES,
    val formHorario: HorarioForm? = null,
    val formAcopiador: MarcasAcopiadorForm? = null,
    val horarioAEliminar: HorarioAsistencia? = null,
    val confirmarQuitarDias: Boolean = false,
    val mensaje: String? = null,
    val mensajeEsAdvertencia: Boolean = false,
)

private data class Datos(
    val usuarios: List<Usuario>,
    val horarios: List<HorarioAsistencia>,
    val asistencias: List<Asistencia>,
    val diasAcopiador: Set<LocalDate>,
    val justificaciones: List<Justificacion>,
)

private val HORA = Regex("^\\d{2}:\\d{2}$")

/**
 * "Tomar asistencia" como en Laravel: lo usa el administrador o quien él asigne. Todo se guarda
 * en SQLite y se sube a Laravel con la sincronización (funciona sin señal).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TomarAsistenciaViewModel(
    private val usuarioActualId: String,
    private val repository: AsistenciaRepository,
    usuarioRepository: UsuarioRepository,
) : ViewModel() {
    private val hoy = todayLocalDate()

    private val control = MutableStateFlow(Control(fecha = hoy, mes = hoy.startOfMonth(), mesAcopiadores = hoy.startOfMonth()))

    /** Trabajadores activos a los que se les toma asistencia desde la tabla general. */
    private var participantes: List<Usuario> = emptyList()

    /** Asistencia general del día seleccionado, por usuario. */
    private var registrosDelDia: Map<String, Asistencia> = emptyMap()

    private val asistencias = control
        .map { c -> rangoCalendario(c.mes).let { (desde, hasta) -> minOf(desde, c.fecha) to maxOf(hasta, c.fecha) } }
        .distinctUntilChanged()
        .flatMapLatest { (desde, hasta) -> repository.observeAsistenciasEntre(desde, hasta) }

    private val diasAcopiador = control
        .map { c -> rangoCalendario(c.mesAcopiadores).let { (desde, hasta) -> minOf(desde, c.fecha) to maxOf(hasta, c.fecha) } }
        .distinctUntilChanged()
        .flatMapLatest { (desde, hasta) -> repository.observeDiasAcopiadorEntre(desde, hasta) }

    private val datos = combine(
        usuarioRepository.observeAll(),
        repository.observeHorarios(),
        asistencias,
        diasAcopiador,
        repository.observeJustificaciones(),
    ) { usuarios, horarios, asistencias, dias, justificaciones -> Datos(usuarios, horarios, asistencias, dias, justificaciones) }

    val state: StateFlow<TomarAsistenciaUiState> = combine(control, datos) { c, d -> armar(c, d) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            TomarAsistenciaUiState(hoy = hoy, fecha = hoy, mes = hoy.startOfMonth(), mesAcopiadores = hoy.startOfMonth()),
        )

    private fun armar(c: Control, d: Datos): TomarAsistenciaUiState {
        val activos = d.usuarios.filter { it.activo }
        participantes = activos.filter { it.participaEnAsistenciaGeneral }
        val generales = d.asistencias.filter { it.horarioId == null }
        registrosDelDia = generales.filter { it.fecha == c.fecha }.associateBy { it.usuarioId }
        val porDia = generales.groupBy { it.fecha }

        val texto = c.busqueda.trim()
        val coincide: (Usuario) -> Boolean = { u ->
            texto.isEmpty() || u.nombres.contains(texto, true) || u.apellidos.contains(texto, true) || u.documento.contains(texto, true)
        }
        val trabajadores = participantes.filter(coincide).map { u ->
            FilaTrabajador(u, if (c.editando) c.estados[u.id] else registrosDelDia[u.id]?.estado)
        }
        val estadosDelDia = if (c.editando) participantes.mapNotNull { c.estados[it.id] } else registrosDelDia.values.map { it.estado }

        val semanas = dias(c.mes).map { dia ->
            val registros = porDia[dia.fecha].orEmpty()
            dia.copy(
                registrada = registros.isNotEmpty(),
                faltas = registros.count { it.estado == EstadoAsistencia.FALTA },
                tardanzas = registros.count { it.estado == EstadoAsistencia.TARDANZA },
            )
        }.chunked(7)
        val semanasAcopiadores = dias(c.mesAcopiadores).map { it.copy(diaAcopiador = it.fecha in d.diasAcopiador) }.chunked(7)

        val usuariosPorId = d.usuarios.associateBy { it.id }
        val horariosPorId = d.horarios.associateBy { it.id }
        val justificaciones = d.justificaciones
            .filter { c.filtro.estado == null || it.estado == c.filtro.estado }
            .map { j ->
                val trabajador = usuariosPorId[j.usuarioId]
                FilaJustificacion(j, trabajador?.nombreCompleto ?: "Trabajador", trabajador?.documento.orEmpty(), j.horarioId?.let { horariosPorId[it]?.nombre })
            }

        val marcasDelDia = d.asistencias.filter { it.horarioId != null && it.fecha == c.fecha }.groupBy { it.usuarioId }
        val acopiadores = activos.filter { it.esAcopiador && coincide(it) }.map { u ->
            FilaAcopiador(u, marcasDelDia[u.id].orEmpty().associateBy { it.horarioId!! })
        }

        return TomarAsistenciaUiState(
            pestana = c.pestana,
            hoy = hoy,
            fecha = c.fecha,
            mes = c.mes,
            semanas = semanas,
            fechaRegistrada = registrosDelDia.isNotEmpty(),
            editando = c.editando,
            busqueda = c.busqueda,
            trabajadores = trabajadores,
            presentes = estadosDelDia.count { it == EstadoAsistencia.PRESENTE },
            tardanzas = estadosDelDia.count { it == EstadoAsistencia.TARDANZA },
            faltas = estadosDelDia.count { it == EstadoAsistencia.FALTA },
            filtroJustificacion = c.filtro,
            justificaciones = justificaciones,
            pendientes = d.justificaciones.count { it.estado == EstadoJustificacion.PENDIENTE },
            horarios = d.horarios,
            mesAcopiadores = c.mesAcopiadores,
            semanasAcopiadores = semanasAcopiadores,
            diasElegidos = semanasAcopiadores.flatten().count { it.delMes && it.diaAcopiador },
            mesAcopiadoresTerminado = c.mesAcopiadores.endOfMonth() < hoy,
            esDiaAcopiador = c.fecha in d.diasAcopiador,
            acopiadores = acopiadores,
            formHorario = c.formHorario,
            formAcopiador = c.formAcopiador,
            horarioAEliminar = c.horarioAEliminar,
            confirmarQuitarDias = c.confirmarQuitarDias,
            mensaje = c.mensaje,
            mensajeEsAdvertencia = c.mensajeEsAdvertencia,
        )
    }

    /** Lunes a domingo del mes, en semanas completas. */
    private fun dias(mes: LocalDate): List<DiaCalendario> {
        val (desde, hasta) = rangoCalendario(mes)
        return generateSequence(desde) { it.plusDias(1) }.takeWhile { it <= hasta }.map { dia ->
            DiaCalendario(
                fecha = dia,
                delMes = dia.month == mes.month,
                esHoy = dia == hoy,
                esFuturo = dia > hoy,
                esPasado = dia < hoy,
            )
        }.toList()
    }

    private fun rangoCalendario(mes: LocalDate): Pair<LocalDate, LocalDate> = mes.startOfMonth().startOfWeek() to mes.endOfMonth().endOfWeek()

    fun seleccionarPestana(pestana: PestanaAsistencia) = control.update { it.copy(pestana = pestana) }

    fun onBusqueda(texto: String) = control.update { it.copy(busqueda = texto) }

    // ---------- Asistencia general ----------

    fun seleccionarFecha(fecha: LocalDate) {
        if (fecha > hoy) {
            avisar("No se puede tomar asistencia de un día futuro.", advertencia = true)
            return
        }
        control.update { it.copy(fecha = fecha, mes = fecha.startOfMonth(), editando = false, estados = emptyMap()) }
    }

    fun irAHoy() = seleccionarFecha(hoy)

    fun cambiarDia(desplazamiento: Int) = seleccionarFecha(control.value.fecha.plusDias(desplazamiento))

    fun cambiarMes(desplazamiento: Int) = control.update { it.copy(mes = it.mes.sumarMeses(desplazamiento)) }

    /** Empieza a tomar asistencia del día: todos los trabajadores como presentes. */
    fun nuevo() {
        val c = control.value
        if (c.fecha > hoy) return
        if (registrosDelDia.isNotEmpty()) {
            avisar("Este día ya tiene asistencia registrada. Usa \"Editar\" para modificarla.", advertencia = true)
            return
        }
        control.update { it.copy(editando = true, estados = participantes.associate { u -> u.id to EstadoAsistencia.PRESENTE }) }
    }

    fun editar() {
        if (registrosDelDia.isEmpty()) return
        // Quien se haya registrado después de tomar la asistencia aparece como presente.
        val estados = participantes.associate { u -> u.id to (registrosDelDia[u.id]?.estado ?: EstadoAsistencia.PRESENTE) }
        control.update { it.copy(editando = true, estados = estados) }
    }

    fun cancelar() = control.update { it.copy(editando = false, estados = emptyMap()) }

    fun marcarTodos(estado: EstadoAsistencia) = control.update { c ->
        c.copy(estados = c.estados + participantes.associate { it.id to estado })
    }

    fun cambiarEstado(usuarioId: String, estado: EstadoAsistencia) = control.update { c -> c.copy(estados = c.estados + (usuarioId to estado)) }

    fun guardar() {
        val c = control.value
        if (!c.editando || c.fecha > hoy) return
        val activos = participantes.map { it.id }.toSet()
        viewModelScope.launch {
            repository.guardarAsistenciaGeneral(c.fecha, c.estados.filterKeys { it in activos }, usuarioActualId)
            control.update { it.copy(editando = false, estados = emptyMap(), mensaje = "Asistencia guardada correctamente.", mensajeEsAdvertencia = false) }
        }
    }

    // ---------- Justificaciones ----------

    fun filtrarJustificaciones(filtro: FiltroJustificacion) = control.update { it.copy(filtro = filtro) }

    fun aceptar(fila: FilaJustificacion) = revisar(fila, EstadoJustificacion.ACEPTADA, "Justificación aceptada.")

    fun rechazar(fila: FilaJustificacion) = revisar(fila, EstadoJustificacion.RECHAZADA, "Justificación rechazada.")

    private fun revisar(fila: FilaJustificacion, estado: EstadoJustificacion, mensaje: String) {
        viewModelScope.launch {
            repository.revisarJustificacion(fila.justificacion.id, estado)
            avisar(mensaje)
        }
    }

    // ---------- Acopiadores: días ----------

    fun cambiarMesAcopiadores(desplazamiento: Int) = control.update { it.copy(mesAcopiadores = it.mesAcopiadores.sumarMeses(desplazamiento)) }

    /** Los días pasados no se cambian para no generar ni borrar faltas de forma retroactiva. */
    fun alternarDiaAcopiador(fecha: LocalDate) {
        if (fecha < hoy) {
            avisar("No se pueden cambiar días pasados.", advertencia = true)
            return
        }
        viewModelScope.launch { repository.alternarDiaAcopiador(fecha) }
    }

    fun pedirQuitarDias() = control.update { it.copy(confirmarQuitarDias = true) }

    fun cancelarQuitarDias() = control.update { it.copy(confirmarQuitarDias = false) }

    /** Elige de una vez los días que quedan del mes visible (desde hoy). */
    fun seleccionarDiasDelMes(modo: ModoDias) {
        control.update { it.copy(confirmarQuitarDias = false) }
        val mes = control.value.mesAcopiadores
        val desde = maxOf(mes.startOfMonth(), hoy)
        val hasta = mes.endOfMonth()
        if (desde > hasta) return
        val fechas = when (modo) {
            ModoDias.NINGUNO -> emptyList()
            else -> generateSequence(desde) { it.plusDias(1) }.takeWhile { it <= hasta }
                .filter { modo == ModoDias.TODOS || it.dayOfWeek != DayOfWeek.SUNDAY }
                .toList()
        }
        viewModelScope.launch {
            repository.reemplazarDiasAcopiador(desde, hasta, fechas)
            avisar("Días de asistencia actualizados.")
        }
    }

    // ---------- Acopiadores: horarios ----------

    fun nuevoHorario() = control.update { it.copy(formHorario = HorarioForm()) }

    fun editarHorario(h: HorarioAsistencia) = control.update {
        it.copy(formHorario = HorarioForm(h.id, h.nombre, h.horaInicio, h.limitePresente, h.limiteTardanza, h.activo))
    }

    fun cerrarHorario() = control.update { it.copy(formHorario = null) }

    fun editarFormHorario(cambio: HorarioForm.() -> HorarioForm) = control.update { c -> c.copy(formHorario = c.formHorario?.cambio()?.copy(error = null)) }

    fun guardarHorario() {
        val f = control.value.formHorario ?: return
        val error = when {
            f.nombre.isBlank() -> "Escribe el nombre del horario."
            f.nombre.trim().length > 60 -> "El nombre no puede tener más de 60 letras."
            listOf(f.horaInicio, f.presenteHasta, f.tardanzaHasta).any { !esHoraValida(it) } -> "Escribe las horas como HH:MM (ej. 05:00)."
            f.presenteHasta <= f.horaInicio -> "\"Presente hasta\" debe ser después de la hora de inicio."
            f.tardanzaHasta <= f.presenteHasta -> "\"Tardanza hasta\" debe ser después de \"Presente hasta\"."
            else -> null
        }
        if (error != null) {
            control.update { it.copy(formHorario = f.copy(error = error)) }
            return
        }
        viewModelScope.launch {
            repository.guardarHorario(
                HorarioAsistencia(f.id.orEmpty(), f.nombre.trim(), f.horaInicio, f.presenteHasta, f.tardanzaHasta, f.activo),
            )
            control.update { it.copy(formHorario = null, mensaje = "Horario guardado correctamente.", mensajeEsAdvertencia = false) }
        }
    }

    fun pedirEliminarHorario(h: HorarioAsistencia) = control.update { it.copy(horarioAEliminar = h) }

    fun cancelarEliminarHorario() = control.update { it.copy(horarioAEliminar = null) }

    fun eliminarHorario() {
        val h = control.value.horarioAEliminar ?: return
        viewModelScope.launch {
            repository.eliminarHorario(h.id)
            control.update { it.copy(horarioAEliminar = null, mensaje = "Horario eliminado.", mensajeEsAdvertencia = false) }
        }
    }

    // ---------- Acopiadores: asistencia del día ----------

    fun editarAcopiador(fila: FilaAcopiador) = control.update { c ->
        c.copy(formAcopiador = MarcasAcopiadorForm(fila.usuario, state.value.horariosActivos.associate { it.id to fila.marcas[it.id]?.estado }))
    }

    fun cambiarMarcaAcopiador(horarioId: String, estado: EstadoAsistencia?) = control.update { c ->
        c.copy(formAcopiador = c.formAcopiador?.let { it.copy(estados = it.estados + (horarioId to estado)) })
    }

    fun cerrarAcopiador() = control.update { it.copy(formAcopiador = null) }

    fun guardarAcopiador() {
        val c = control.value
        val f = c.formAcopiador ?: return
        viewModelScope.launch {
            repository.guardarMarcasAcopiador(f.usuario.id, c.fecha, f.estados, usuarioActualId)
            control.update { it.copy(formAcopiador = null, mensaje = "Asistencia del acopiador actualizada.", mensajeEsAdvertencia = false) }
        }
    }

    fun consumirMensaje() = control.update { it.copy(mensaje = null) }

    private fun avisar(mensaje: String, advertencia: Boolean = false) =
        control.update { it.copy(mensaje = mensaje, mensajeEsAdvertencia = advertencia) }
}

enum class ModoDias { LABORABLES, TODOS, NINGUNO }

private fun esHoraValida(hora: String): Boolean {
    if (!HORA.matches(hora)) return false
    val (h, m) = hora.split(":").map { it.toInt() }
    return h in 0..23 && m in 0..59
}

/** Primer día del mes que queda [n] meses después (o antes, si es negativo). */
private fun LocalDate.sumarMeses(n: Int): LocalDate {
    val total = year * 12 + (month.number - 1) + n
    return LocalDate(total / 12, total % 12 + 1, 1)
}

/** Lo que se escribe en un campo de hora queda como "HH:MM" (solo números, con los dos puntos). */
fun formatearHora(texto: String): String {
    val digitos = texto.filter { it.isDigit() }.take(4)
    return if (digitos.length > 2) "${digitos.take(2)}:${digitos.drop(2)}" else digitos
}
